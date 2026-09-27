# Decides if an audio clip looks like distress.
#
# Path A - code word: if the user says one of their secret phrases,
#          send an alert right away.
# Path B - voice change: give each 4s clip a score from 0 to 100 based on
#          how different the voice is from normal. Average the last 3 clips
#          (about 12 seconds). If the average is above the sensitivity
#          threshold, send an alert. Averaging means one cough or loud
#          noise won't set it off.
#
# After any alert we wait 60 seconds before sending another one.
import time
from collections import deque

import config

CODE_WORD_MATCH = 85  # how close the words must be (0-100), allows small mistakes
COOLDOWN_SECONDS = 60
FORGET_AFTER_SECONDS = 10 * 60  # forget a call's text/scores after 10 minutes without audio

# one entry per call, keyed by session id. Only in memory, never saved.
sessions = {}


def forget_old_sessions():
    now = time.time()
    for sid in list(sessions):
        if now - sessions[sid]["last_seen"] > FORGET_AFTER_SECONDS:
            del sessions[sid]


def end_session(session_id):
    """Call ended: throw away everything we kept about it."""
    sessions.pop(session_id, None)


def get_session(session_id):
    forget_old_sessions()
    if session_id not in sessions:
        sessions[session_id] = {
            "texts": deque(maxlen=2),   # last 2 clips of text, so a phrase cut in half is still found
            "scores": deque(maxlen=3),  # last 3 clip scores, for the average
            "last_alert_time": 0,
            "last_alert_id": None,      # so the cancel phrase knows what to cancel
        }
    sessions[session_id]["last_seen"] = time.time()
    return sessions[session_id]


def percent_change(value, baseline):
    if not baseline or not value:
        return 0.0
    return (value - baseline) / baseline * 100


def find_phrase(text, phrases):
    """Fuzzy search, so "red umbrela" still matches "red umbrella".
    Returns (best phrase, match % 0-100)."""
    from rapidfuzz import fuzz

    text = text.lower().strip()
    best, best_score = None, 0
    for phrase in phrases:
        phrase_l = phrase.lower()
        if len(text) < len(phrase_l):
            # text is shorter than the phrase, so compare the whole thing
            # (otherwise just "umbrella" would count as a 100% match for "red umbrella")
            score = fuzz.ratio(phrase_l, text)
        else:
            score = fuzz.partial_ratio(phrase_l, text)
        if score > best_score:
            best, best_score = phrase, score
    return best, round(best_score)


def clip_score(pitch_change, energy_change, silence_ratio):
    # Mostly silent = the user is just listening. That's normal, not stress.
    if silence_ratio >= 0.8:
        return 0

    # Only count the voice going UP (higher / louder), that's what stress does.
    pitch_points = min(40, max(0, pitch_change) / 50 * 40)     # +50% pitch = full 40 points
    energy_points = min(40, max(0, energy_change) / 100 * 40)  # twice as loud = full 40 points
    # long pauses in the middle of talking
    silence_points = min(20, max(0, silence_ratio - 0.3) / 0.5 * 20)

    return round(pitch_points + energy_points + silence_points)


def evaluate(session_id, text, pitch, loudness, silence_ratio, s):
    # s = this user's settings (config.settings_for)
    session = get_session(session_id)
    session["texts"].append(text)
    recent_text = " ".join(session["texts"]).strip()

    phrase, match = None, 0
    if s["code_words"] and recent_text:
        phrase, match = find_phrase(recent_text, s["code_words"])

    pitch_change = percent_change(pitch, s["baseline_pitch"])
    energy_change = percent_change(loudness, s["baseline_rms"])
    score = clip_score(pitch_change, energy_change, silence_ratio)

    session["scores"].append(score)
    rolling = round(sum(session["scores"]) / len(session["scores"]))

    trigger = None
    cooling_down = time.time() - session["last_alert_time"] < COOLDOWN_SECONDS
    if not cooling_down:
        if match >= CODE_WORD_MATCH:
            trigger = "codeword"
        elif len(session["scores"]) == 3 and rolling >= config.threshold(s):  # need a full 12 seconds first
            trigger = "nonverbal"
    if trigger:
        session["last_alert_time"] = time.time()

    return {
        "session_id": session_id,
        "stress_score": score,
        "rolling_score": rolling,
        "alert_triggered": trigger is not None,
        "trigger_path": trigger,
        "signals": {
            "codeword": {"matched": match >= CODE_WORD_MATCH, "phrase": phrase, "confidence": match},
            "pitch": {"value_hz": round(pitch, 1), "delta_pct": round(pitch_change, 1)},
            "energy": {"delta_pct": round(energy_change, 1)},
            "silence": {"ratio": round(silence_ratio, 2)},
        },
    }


def said_cancel_phrase(session_id, s):
    cancel = s["cancel_word"]
    text = " ".join(get_session(session_id)["texts"]).strip()
    if not cancel or not text:
        return False
    return find_phrase(text, [cancel])[1] >= CODE_WORD_MATCH


def explain(result):
    """Plain-English reason saved with the alert."""
    sig = result["signals"]
    if result["trigger_path"] == "codeword":
        return f"Code word '{sig['codeword']['phrase']}' heard ({sig['codeword']['confidence']}% match)."
    return (
        f"Voice changed for about 12 seconds: average score {result['rolling_score']}. "
        f"Last clip: pitch {sig['pitch']['delta_pct']:+.0f}%, loudness {sig['energy']['delta_pct']:+.0f}%, "
        f"silence {sig['silence']['ratio'] * 100:.0f}%."
    )


def fake_result(session_id, force_alert=False):
    """STUB_MODE: a made-up result so the website can be tested without the audio libraries."""
    return {
        "session_id": session_id,
        "stress_score": 35,
        "rolling_score": 35,
        "alert_triggered": force_alert,
        "trigger_path": "nonverbal" if force_alert else None,
        "signals": {
            "codeword": {"matched": False, "phrase": None, "confidence": 0},
            "pitch": {"value_hz": 180.0, "delta_pct": 0.0},
            "energy": {"delta_pct": 0.0},
            "silence": {"ratio": 0.1},
        },
    }
