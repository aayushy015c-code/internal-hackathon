"""
config.py
---------
Two jobs:

1. Read this service's own settings (CORE_API_URL, STUB_MODE) from
   environment variables / a local .env file.
2. Hold a live, in-memory copy of the *user's* configuration (code words,
   sensitivity, baseline, etc.) that actually lives in the Spring Boot core
   API's database. Spring Boot is always the source of truth; this module
   is just a fast local cache so /analyze doesn't make an HTTP call to
   fetch config on every single 4-second chunk.

The cache is refreshed:
  - once when this service starts up
  - whenever the frontend calls POST /reload-config (e.g. right after the
    user saves changes on the /settings page)
  - whenever /calibrate successfully computes a new baseline
"""
import os

import httpx
from dotenv import load_dotenv

load_dotenv()  # reads a local .env file if one exists; harmless if it doesn't

CORE_API_URL = os.environ.get("CORE_API_URL", "http://localhost:8080")
STUB_MODE = os.environ.get("STUB_MODE", "false").lower() == "true"

# Stress-score thresholds per sensitivity level (see scoring.py). Lower
# threshold = more sensitive = easier to trigger a non-verbal alert.
SENSITIVITY_THRESHOLDS = {"LOW": 80, "MEDIUM": 65, "HIGH": 50}


class LiveConfig:
    """A plain mutable holder for the current user configuration."""

    def __init__(self):
        self.code_words: list[str] = []
        self.cancel_code_word: str = "false alarm"
        self.sensitivity: str = "MEDIUM"
        self.baseline_pitch_hz: float | None = None
        self.baseline_rms: float | None = None
        self.analysis_active: bool = True

    def threshold(self) -> int:
        return SENSITIVITY_THRESHOLDS.get(self.sensitivity, 65)


# One shared instance, imported by scoring.py and main.py.
live_config = LiveConfig()


def refresh_from_core_api() -> bool:
    """
    Pulls the latest config from the core API into `live_config`.

    Returns True on success, False on failure. Failures are logged, never
    raised - if the core API happens to be down or still starting up, the
    analysis service keeps running with whatever config it already has
    (or safe defaults), rather than crashing.
    """
    try:
        resp = httpx.get(f"{CORE_API_URL}/api/config", timeout=3.0)
        resp.raise_for_status()
        data = resp.json()
        live_config.code_words = [w.strip() for w in (data.get("codeWords") or "").split(",") if w.strip()]
        live_config.cancel_code_word = data.get("cancelCodeWord") or "false alarm"
        live_config.sensitivity = data.get("sensitivity") or "MEDIUM"
        live_config.baseline_pitch_hz = data.get("baselinePitchHz")
        live_config.baseline_rms = data.get("baselineRms")
        live_config.analysis_active = data.get("analysisActive", True)
        return True
    except Exception as e:  # noqa: BLE001 - deliberately broad, this must never crash the caller
        print(f"[config] could not reach core API to refresh config: {e}")
        return False


def push_baseline_to_core_api(pitch_hz: float, rms: float) -> None:
    """Persists a freshly-calibrated baseline back to the core API's database."""
    try:
        httpx.put(
            f"{CORE_API_URL}/api/config/baseline",
            json={"baselinePitchHz": pitch_hz, "baselineRms": rms},
            timeout=3.0,
        )
    except Exception as e:  # noqa: BLE001
        print(f"[config] could not push baseline to core API: {e}")
