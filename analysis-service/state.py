"""
state.py
--------
Per-call in-memory state. Each browser tab that opens /call generates its
own `session_id` (a random string), so if there were ever two calls running
at once, they would not interfere with each other's rolling averages or
cooldowns.

This is intentionally just a Python dict in memory - no database, no
Redis. If the analysis service restarts, all sessions reset. That is fine
for a hackathon demo and also arguably correct for privacy: nothing about
an in-progress call's voice patterns should outlive the process.
"""
import time
from collections import deque


class SessionState:
    def __init__(self):
        # Last 2 transcript chunks, so a code word split across a 4s chunk
        # boundary ("...red um" | "brella...") is still caught when we
        # search the *combined* text.
        self.recent_transcripts: deque[str] = deque(maxlen=2)

        # Last 3 per-chunk stress scores (~12 seconds), averaged for the
        # Path B "rolling_score" per the spec.
        self.recent_chunk_scores: deque[int] = deque(maxlen=3)

        self.last_alert_at: float | None = None

        # The core API's id for the most recent alert WE created for this
        # session, so a later spoken cancel word knows what to cancel.
        # Cleared once cancelled, so repeating the phrase doesn't re-send
        # a cancellation notice over and over.
        self.last_alert_id: int | None = None

    def in_cooldown(self, cooldown_seconds: float) -> bool:
        return self.last_alert_at is not None and (time.time() - self.last_alert_at) < cooldown_seconds

    def mark_alert(self) -> None:
        self.last_alert_at = time.time()


_sessions: dict[str, SessionState] = {}


def get_session(session_id: str) -> SessionState:
    if session_id not in _sessions:
        _sessions[session_id] = SessionState()
    return _sessions[session_id]
