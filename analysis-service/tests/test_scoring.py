import pytest

import config
import scoring


S = None  # this test user's settings


@pytest.fixture(autouse=True)
def settings():
    global S
    scoring.sessions.clear()
    S = config.defaults()
    S.update(code_words=["red umbrella"], cancel_word="false alarm", sensitivity="MEDIUM",
             baseline_pitch=150.0, baseline_rms=0.05, consent_given=True)


@pytest.mark.parametrize("text, expected_match", [
    ("I left my red umbrella at home", True),
    ("I left my red umbrela at home", True),   # small spelling mistake still counts
    ("umbrella", False),                       # only half the phrase
    ("red", False),
    (" ", False),
    ("", False),
    ("thank you", False),
])
def test_code_word_matching(text, expected_match):
    _, score = scoring.find_phrase(text, ["red umbrella"])
    assert (score >= scoring.CODE_WORD_MATCH) == expected_match


def test_listening_quietly_scores_zero():
    # quieter than normal and mostly silent = just listening
    assert scoring.clip_score(pitch_change=-50, energy_change=-90, silence_ratio=0.9) == 0


def test_only_higher_or_louder_counts():
    assert scoring.clip_score(-40, -40, 0.1) == 0
    assert scoring.clip_score(50, 100, 0.1) == 80


def test_code_word_alerts_immediately():
    r = scoring.evaluate("s", "please bring my red umbrella", 150, 0.05, 0.1, S)
    assert r["alert_triggered"] and r["trigger_path"] == "codeword"


def test_voice_change_needs_three_clips():
    loud = dict(text="", pitch=225, loudness=0.10, silence_ratio=0.1)  # +50% pitch, 2x louder
    results = [scoring.evaluate("s", **loud, s=S) for _ in range(3)]
    assert [r["alert_triggered"] for r in results] == [False, False, True]
    assert results[2]["trigger_path"] == "nonverbal"


def test_one_loud_clip_does_not_alert():
    normal = dict(text="", pitch=150, loudness=0.05, silence_ratio=0.1)
    loud = dict(text="", pitch=225, loudness=0.10, silence_ratio=0.1)
    results = [scoring.evaluate("s", **c, s=S) for c in (normal, loud, normal, normal)]
    assert not any(r["alert_triggered"] for r in results)


def test_cooldown_after_alert():
    first = scoring.evaluate("s", "red umbrella", 150, 0.05, 0.1, S)
    second = scoring.evaluate("s", "red umbrella", 150, 0.05, 0.1, S)
    assert first["alert_triggered"] and not second["alert_triggered"]


def test_cancel_phrase():
    scoring.evaluate("s", "oh sorry false alarm", 150, 0.05, 0.1, S)
    assert scoring.said_cancel_phrase("s", S)


def test_ending_a_call_forgets_everything():
    scoring.evaluate("s", "some private words", 150, 0.05, 0.1, S)
    scoring.end_session("s")
    assert "s" not in scoring.sessions


def test_idle_calls_are_forgotten(monkeypatch):
    scoring.evaluate("old", "private words", 150, 0.05, 0.1, S)
    scoring.sessions["old"]["last_seen"] -= scoring.FORGET_AFTER_SECONDS + 1
    scoring.get_session("new")
    assert "old" not in scoring.sessions


def test_alert_reason_is_plain_english():
    r = scoring.evaluate("s", "red umbrella", 150, 0.05, 0.1, S)
    assert scoring.explain(r) == "Code word 'red umbrella' heard (100% match)."
