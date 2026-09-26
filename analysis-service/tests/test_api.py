import pytest
from fastapi.testclient import TestClient

import config
import main

FRONTEND = "http://localhost:5500"


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(main, "send_alert", lambda *a: None)
    monkeypatch.setattr(config, "save_baseline", lambda *a: None)
    config.settings.update(consent_given=True, analysis_active=True)
    with TestClient(main.app, base_url="http://localhost") as c:
        yield c


def clip(size=1000):
    return {"audio": ("chunk.webm", b"x" * size, "audio/webm")}


def test_health(client):
    assert client.get("/health").json()["status"] == "ok"


def test_analyze_from_our_frontend(client):
    r = client.post("/analyze", data={"session_id": "s1"}, files=clip(), headers={"Origin": FRONTEND})
    assert r.status_code == 200
    assert r.json()["rolling_score"] == 35
    assert r.headers["access-control-allow-origin"] == FRONTEND


def test_other_websites_are_blocked(client):
    # a plain form POST from another site, which CORS alone would NOT stop
    r = client.post("/calibrate/demo", headers={"Origin": "https://evil.example"})
    assert r.status_code == 403


def test_other_host_names_are_blocked():
    with TestClient(main.app, base_url="http://attacker.example") as c:
        assert c.get("/health").status_code == 400


def test_needs_consent(client):
    config.settings["consent_given"] = False
    r = client.post("/analyze", data={"session_id": "s1"}, files=clip())
    assert r.status_code == 403
    assert "privacy notice" in r.json()["detail"]


def test_analysis_switch_off(client):
    config.settings["analysis_active"] = False
    assert client.post("/analyze", data={"session_id": "s1"}, files=clip()).status_code == 403


def test_upload_too_big(client):
    r = client.post("/analyze", data={"session_id": "s1"}, files=clip(main.MAX_UPLOAD + 1))
    assert r.status_code == 413


def test_bad_location_rejected(client):
    r = client.post("/analyze", data={"session_id": "s1", "latitude": "500"}, files=clip())
    assert r.status_code == 422


def test_end_session(client):
    assert client.post("/end-session", data={"session_id": "s1"}).json() == {"ended": True}
