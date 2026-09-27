import pytest
from fastapi.testclient import TestClient

import config
import main

FRONTEND = "http://localhost:5500"
USER_A = {"X-User-Key": "key-of-user-a"}
USER_B = {"X-User-Key": "key-of-user-b"}


@pytest.fixture
def client(monkeypatch):
    sent = []
    monkeypatch.setattr(main, "send_alert", lambda key, *a: sent.append(key))
    monkeypatch.setattr(config, "save_baseline", lambda *a: None)
    # pretend the core API returned these settings for each user
    config._cache.clear()
    for headers in (USER_A, USER_B):
        s = config.defaults()
        s.update(consent_given=True, analysis_active=True)
        config._cache[headers["X-User-Key"]] = s
    with TestClient(main.app, base_url="http://localhost") as c:
        c.sent_alerts = sent
        yield c


def clip(size=1000):
    return {"audio": ("chunk.webm", b"x" * size, "audio/webm")}


def test_health(client):
    assert client.get("/health").json()["status"] == "ok"


def test_analyze_from_our_frontend(client):
    r = client.post("/analyze", data={"session_id": "s1"}, files=clip(), headers={"Origin": FRONTEND, **USER_A})
    assert r.status_code == 200
    assert r.json()["rolling_score"] == 35
    assert r.json()["session_id"] == "s1"
    assert r.headers["access-control-allow-origin"] == FRONTEND


def test_access_key_is_required(client):
    assert client.post("/analyze", data={"session_id": "s1"}, files=clip()).status_code == 401
    assert client.post("/calibrate/demo").status_code == 401
    assert client.post("/reload-config").status_code == 401


def test_each_user_uses_their_own_settings(client):
    config._cache[USER_B["X-User-Key"]]["consent_given"] = False
    assert client.post("/analyze", data={"session_id": "s1"}, files=clip(), headers=USER_A).status_code == 200
    assert client.post("/analyze", data={"session_id": "s1"}, files=clip(), headers=USER_B).status_code == 403


def test_alert_is_sent_for_the_right_user(client):
    client.post("/analyze", data={"session_id": "s1", "force_alert": "true"}, files=clip(), headers=USER_B)
    assert client.sent_alerts == [USER_B["X-User-Key"]]


def test_unknown_user_gets_no_analysis(client, monkeypatch):
    monkeypatch.setattr(config, "load_from_core_api", lambda key: False)  # core API says: unknown key
    r = client.post("/analyze", data={"session_id": "s1"}, files=clip(), headers={"X-User-Key": "who-is-this"})
    assert r.status_code == 403


def test_other_websites_are_blocked(client):
    # a plain form POST from another site, which CORS alone would NOT stop
    r = client.post("/calibrate/demo", headers={"Origin": "https://evil.example", **USER_A})
    assert r.status_code == 403


def test_other_host_names_are_blocked():
    with TestClient(main.app, base_url="http://attacker.example") as c:
        assert c.get("/health").status_code == 400


def test_needs_consent(client):
    config._cache[USER_A["X-User-Key"]]["consent_given"] = False
    r = client.post("/analyze", data={"session_id": "s1"}, files=clip(), headers=USER_A)
    assert r.status_code == 403
    assert "privacy notice" in r.json()["detail"]


def test_analysis_switch_off(client):
    config._cache[USER_A["X-User-Key"]]["analysis_active"] = False
    assert client.post("/analyze", data={"session_id": "s1"}, files=clip(), headers=USER_A).status_code == 403


def test_upload_too_big(client):
    r = client.post("/analyze", data={"session_id": "s1"}, files=clip(main.MAX_UPLOAD + 1), headers=USER_A)
    assert r.status_code == 413


def test_bad_location_rejected(client):
    r = client.post("/analyze", data={"session_id": "s1", "latitude": "500"}, files=clip(), headers=USER_A)
    assert r.status_code == 422


def test_end_session(client):
    assert client.post("/end-session", data={"session_id": "s1"}, headers=USER_A).json() == {"ended": True}


def test_sessions_are_separate_per_user():
    assert main.session_key("key-a", "s1") != main.session_key("key-b", "s1")
