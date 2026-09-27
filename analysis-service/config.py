# Settings for the analysis service.
#
# Each user's settings (code words, sensitivity, baseline voice, consent) are
# saved in the core API's database. The browser sends the user's access key
# with every request (X-User-Key header), and we use it to ask the core API
# for THAT user's settings. We keep a copy per user so we don't have to ask
# the core API every 4 seconds. A user's copy is refreshed after calibration
# and when their Settings page calls /reload-config.
import os

import httpx
from dotenv import load_dotenv

load_dotenv()

CORE_API_URL = os.environ.get("CORE_API_URL", "http://localhost:8080")
STUB_MODE = os.environ.get("STUB_MODE", "false").lower() == "true"
USER_HEADER = "X-User-Key"

# rolling score needed to send an alert (lower = easier to trigger)
THRESHOLDS = {"LOW": 80, "MEDIUM": 65, "HIGH": 50}

# access key -> that user's settings
_cache = {}


def defaults():
    return {
        "code_words": [],
        "cancel_word": "false alarm",
        "sensitivity": "MEDIUM",
        "baseline_pitch": None,
        "baseline_rms": None,
        "analysis_active": True,
        "consent_given": False,  # nothing is analyzed until we know the user agreed
    }


def threshold(settings):
    return THRESHOLDS.get(settings["sensitivity"], 65)


def settings_for(user_key):
    if user_key not in _cache:
        load_from_core_api(user_key)
    return _cache.get(user_key) or defaults()


def load_from_core_api(user_key):
    try:
        response = httpx.get(f"{CORE_API_URL}/api/config", headers={USER_HEADER: user_key}, timeout=3)
        response.raise_for_status()
        data = response.json()
    except Exception as e:
        print("Could not load settings from core API:", e)
        return False

    words = data.get("codeWords") or ""
    s = defaults()
    s["code_words"] = [w.strip() for w in words.split(",") if w.strip()]
    s["cancel_word"] = data.get("cancelCodeWord") or ""
    s["sensitivity"] = data.get("sensitivity") or "MEDIUM"
    s["baseline_pitch"] = data.get("baselinePitchHz")
    s["baseline_rms"] = data.get("baselineRms")
    s["analysis_active"] = data.get("analysisActive", True)
    s["consent_given"] = data.get("consentGiven", False)
    _cache[user_key] = s
    return True


def save_baseline(user_key, pitch, rms):
    s = settings_for(user_key)
    s["baseline_pitch"] = pitch
    s["baseline_rms"] = rms
    try:
        httpx.put(f"{CORE_API_URL}/api/config/baseline", headers={USER_HEADER: user_key},
                  json={"baselinePitchHz": pitch, "baselineRms": rms}, timeout=3)
    except Exception as e:
        print("Could not save baseline to core API:", e)
