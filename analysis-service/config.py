# Settings for the analysis service.
#
# The user's settings (code words, sensitivity, baseline voice) are saved in
# the core API's database. We keep a copy here so we don't have to ask the
# core API every 4 seconds. The copy is refreshed on startup, after
# calibration, and when the Settings page calls /reload-config.
import os

import httpx
from dotenv import load_dotenv

load_dotenv()

CORE_API_URL = os.environ.get("CORE_API_URL", "http://localhost:8080")
STUB_MODE = os.environ.get("STUB_MODE", "false").lower() == "true"

# rolling score needed to send an alert (lower = easier to trigger)
THRESHOLDS = {"LOW": 80, "MEDIUM": 65, "HIGH": 50}

# the user's settings, with defaults until we load the real ones
settings = {
    "code_words": [],
    "cancel_word": "false alarm",
    "sensitivity": "MEDIUM",
    "baseline_pitch": None,
    "baseline_rms": None,
    "analysis_active": True,
    "consent_given": False,
}


def threshold():
    return THRESHOLDS.get(settings["sensitivity"], 65)


def load_from_core_api():
    try:
        data = httpx.get(f"{CORE_API_URL}/api/config", timeout=3).json()
    except Exception as e:
        print("Could not load settings from core API:", e)
        return False

    words = data.get("codeWords") or ""
    settings["code_words"] = [w.strip() for w in words.split(",") if w.strip()]
    settings["cancel_word"] = data.get("cancelCodeWord") or ""
    settings["sensitivity"] = data.get("sensitivity") or "MEDIUM"
    settings["baseline_pitch"] = data.get("baselinePitchHz")
    settings["baseline_rms"] = data.get("baselineRms")
    settings["analysis_active"] = data.get("analysisActive", True)
    settings["consent_given"] = data.get("consentGiven", False)
    return True


def save_baseline(pitch, rms):
    settings["baseline_pitch"] = pitch
    settings["baseline_rms"] = rms
    try:
        httpx.put(f"{CORE_API_URL}/api/config/baseline",
                  json={"baselinePitchHz": pitch, "baselineRms": rms}, timeout=3)
    except Exception as e:
        print("Could not save baseline to core API:", e)
