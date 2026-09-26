"""
scoring.py
-----------
The decision engine: turns one chunk's raw signals (transcript, pitch,
energy, silence) into a stress score and, sometimes, an alert.

Two independent trigger paths, exactly as specced:

  Path A - code word: a confident fuzzy match on a phrase the user
  configured on purpose. This fires IMMEDIATELY, with no averaging - the
  bug the spec calls out ("a code word + pitch spike failed to trigger
  because of smoothing") happened because an earlier draft ran code words
  through the same rolling average as the non-verbal signals. Here, Path A
  is checked first and completely bypasses the rolling average.

  Path B - non-verbal: pitch/energy/silence deviations combine into a
  0-100 "chunk score", and we alert when the AVERAGE of the last 3 chunks
  (~12 seconds) crosses the sensitivity threshold. Averaging is what keeps
  one noisy chunk (a cough, a truck driving past) from firing an alert by
  itself - only sustained stress triggers Path B.

A 60-second cooldown applies after ANY alert (either path), so a single
real event doesn't spam contacts with a new alert every few seconds while
the person is still talking.

Note: `rapidfuzz` is imported inside match_codeword(), not at the top of
this file, keeping this module importable before rapidfuzz is installed
(same Step 1 reasoning as audio_utils.py/features.py/transcription.py).
"""
from __future__ import annotations

from dataclasses import dataclass

import config
from state import get_session

CODEWORD_MATCH_THRESHOLD = 85
CANCEL_MATCH_THRESHOLD = 85
COOLDOWN_SECONDS = 60.0


def _pct_deviation(value: float, baseline: float) -> float:
    if not baseline:
        return 0.0
    return abs(value - baseline) / baseline * 100.0


def score_pitch(pitch_hz: float, baseline_pitch_hz: float | None) -> tuple[float, float]:
    """Returns (component score out of 35, delta_pct). No baseline yet -> always 0."""
    if not baseline_pitch_hz or pitch_hz <= 0:
        return 0.0, 0.0
    delta_pct = _pct_deviation(pitch_hz, baseline_pitch_hz)
    # A 50%+ swing from the user's normal pitch maxes out this component.
    component = min(35.0, (delta_pct / 50.0) * 35.0)
    return component, delta_pct


def score_energy(rms: float, baseline_rms: float | None) -> tuple[float, float]:
    """Returns (component score out of 35, delta_pct)."""
    if not baseline_rms:
        return 0.0, 0.0
    delta_pct = _pct_deviation(rms, baseline_rms)
    # Loudness naturally swings more than pitch, so it needs a bigger
    # deviation (80%) to fully saturate this component.
    component = min(35.0, (delta_pct / 80.0) * 35.0)
    return component, delta_pct


def score_silence(silence_ratio: float) -> float:
    """Returns component score out of 30. Ordinary speech has pauses, so we only
    start scoring once a chunk is mostly silent."""
    if silence_ratio <= 0.3:
        return 0.0
    return min(30.0, (silence_ratio - 0.3) / 0.7 * 30.0)


def match_codeword(combined_transcript: str, code_words: list[str]) -> tuple[bool, str | None, int]:
    """Fuzzy-matches every configured phrase against the transcript, keeps the best hit."""
    if not code_words or not combined_transcript.strip():
        return False, None, 0

    from rapidfuzz import fuzz

    best_phrase, best_score = None, 0
    for phrase in code_words:
        phrase = phrase.strip()
        if not phrase:
            continue
        score = fuzz.partial_ratio(phrase.lower(), combined_transcript.lower())
        if score > best_score:
            best_phrase, best_score = phrase, score

    matched = best_score >= CODEWORD_MATCH_THRESHOLD
    return matched, best_phrase, round(best_score)


def match_cancel_word(combined_transcript: str, cancel_word: str) -> bool:
    """The spoken "false alarm" path: fuzzy-matches the configured cancel
    phrase the same way match_codeword() does. Checked separately in
    main.py, since a cancellation isn't a stress signal - it undoes one."""
    if not cancel_word or not combined_transcript.strip():
        return False

    from rapidfuzz import fuzz

    return fuzz.partial_ratio(cancel_word.strip().lower(), combined_transcript.lower()) >= CANCEL_MATCH_THRESHOLD


@dataclass
class ScoreResult:
    stress_score: int
    rolling_score: int
    alert_triggered: bool
    trigger_path: str | None
    codeword_matched: bool
    codeword_phrase: str | None
    codeword_confidence: int
    pitch_hz: float
    pitch_delta_pct: float
    energy_delta_pct: float
    silence_ratio: float


def evaluate_chunk(session_id: str, transcript_chunk: str, pitch_hz: float,
                    rms: float, silence_ratio: float) -> ScoreResult:
    session = get_session(session_id)
    session.recent_transcripts.append(transcript_chunk)
    combined_transcript = " ".join(session.recent_transcripts)

    codeword_matched, codeword_phrase, codeword_confidence = match_codeword(
        combined_transcript, config.live_config.code_words
    )

    pitch_component, pitch_delta_pct = score_pitch(pitch_hz, config.live_config.baseline_pitch_hz)
    energy_component, energy_delta_pct = score_energy(rms, config.live_config.baseline_rms)
    silence_component = score_silence(silence_ratio)

    chunk_score = round(pitch_component + energy_component + silence_component)
    session.recent_chunk_scores.append(chunk_score)
    rolling_score = round(sum(session.recent_chunk_scores) / len(session.recent_chunk_scores))

    in_cooldown = session.in_cooldown(COOLDOWN_SECONDS)
    alert_triggered = False
    trigger_path = None

    if codeword_matched and not in_cooldown:
        # Path A wins outright - deliberate signal, no smoothing.
        alert_triggered = True
        trigger_path = "codeword"
    elif rolling_score >= config.live_config.threshold() and not in_cooldown:
        # Path B - sustained non-verbal stress.
        alert_triggered = True
        trigger_path = "nonverbal"

    if alert_triggered:
        session.mark_alert()

    return ScoreResult(
        stress_score=chunk_score,
        rolling_score=rolling_score,
        alert_triggered=alert_triggered,
        trigger_path=trigger_path,
        codeword_matched=codeword_matched,
        codeword_phrase=codeword_phrase,
        codeword_confidence=codeword_confidence,
        pitch_hz=pitch_hz,
        pitch_delta_pct=pitch_delta_pct,
        energy_delta_pct=energy_delta_pct,
        silence_ratio=silence_ratio,
    )


def stub_result(force_alert: bool = False) -> ScoreResult:
    """Used only when STUB_MODE=true (Step 1 skeleton) - a fixed, believable-looking
    result so the rest of the pipeline (dashboard, core API, ntfy) can be wired up
    and tested before faster-whisper/librosa are even installed."""
    return ScoreResult(
        stress_score=35,
        rolling_score=35,
        alert_triggered=force_alert,
        trigger_path="nonverbal" if force_alert else None,
        codeword_matched=False,
        codeword_phrase=None,
        codeword_confidence=0,
        pitch_hz=180.0,
        pitch_delta_pct=0.0,
        energy_delta_pct=0.0,
        silence_ratio=0.1,
    )


def build_reason_text(result: ScoreResult) -> str:
    """The human-readable explanation stored on the Alert and shown on the dashboard."""
    if result.trigger_path == "codeword":
        return f"Code word '{result.codeword_phrase}' matched at {result.codeword_confidence}% confidence."
    if result.trigger_path == "nonverbal":
        return (
            f"Sustained stress signal: rolling score {result.rolling_score} "
            f"(pitch +{result.pitch_delta_pct:.0f}% vs baseline, "
            f"energy +{result.energy_delta_pct:.0f}% vs baseline, "
            f"silence ratio {result.silence_ratio:.2f}) averaged over the last 3 chunks."
        )
    return "No alert triggered."
