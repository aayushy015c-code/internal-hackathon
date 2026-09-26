# The analysis service (FastAPI). Run with:  uvicorn main:app --port 8000
#
#   GET  /health          is it running?
#   POST /analyze         one 4 second audio clip in -> stress score out
#   POST /end-session     call ended, forget everything about it
#   POST /calibrate       ~10 seconds of normal talking -> saves your "normal voice"
#   POST /calibrate/demo  use a made-up normal voice (for quick demos)
#   POST /reload-config   re-read settings from the core API
#
# Try it in the browser: http://localhost:8000/docs
import time
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import JSONResponse

import audio
import config
import scoring

MAX_UPLOAD = 2 * 1024 * 1024  # 2MB, a 4s clip is usually under 100KB
ALLOWED_ORIGINS = ["http://localhost:5500", "http://127.0.0.1:5500"]

@asynccontextmanager
async def lifespan(app):
    # runs once when the server starts
    config.load_from_core_api()
    if not config.STUB_MODE:
        audio.load_whisper()  # load now so the first clip isn't slow
    yield


app = FastAPI(title="Silent Signal analysis service", lifespan=lifespan)

# only our frontend is allowed to call this
app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)
# only answer when called as "localhost" (stops DNS rebinding attacks)
app.add_middleware(TrustedHostMiddleware, allowed_hosts=["localhost", "127.0.0.1"])


# CORS only stops other websites from READING our answers. A plain form
# POST from another website would still run. So we block those here.
@app.middleware("http")
async def block_other_websites(request: Request, call_next):
    origin = request.headers.get("origin")
    if origin and origin not in ALLOWED_ORIGINS:
        return JSONResponse({"detail": "Requests from other websites are not allowed."}, status_code=403)
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Cache-Control"] = "no-store"
    return response


@app.get("/health")
def health():
    return {"status": "ok", "service": "analysis-service", "stub_mode": config.STUB_MODE}


@app.post("/reload-config")
def reload_config():
    return {"reloaded": config.load_from_core_api()}


def read_upload(file: UploadFile):
    data = file.file.read(MAX_UPLOAD + 1)  # never read more than the limit into memory
    if len(data) > MAX_UPLOAD:
        raise HTTPException(413, "Audio clip too big")
    return data


# Note: these are normal "def" (not "async def") on purpose. Whisper is slow
# and would freeze the whole server if it ran inside an async function.
@app.post("/analyze")
def analyze(
    session_id: str = Form(..., max_length=64),
    audio_file: UploadFile = File(..., alias="audio"),
    latitude: float | None = Form(None, ge=-90, le=90),
    longitude: float | None = Form(None, ge=-180, le=180),
    force_alert: bool = Form(False),
):
    start = time.time()
    if not config.settings["consent_given"]:
        raise HTTPException(403, "Please accept the privacy notice first (consent page).")
    if not config.settings["analysis_active"]:
        raise HTTPException(403, "Analysis is turned off in Settings.")

    data = read_upload(audio_file)

    if config.STUB_MODE:
        result = scoring.fake_result(session_id, force_alert)
    else:
        samples = audio.decode(data)
        pitch, loudness, silence = audio.measure(samples, config.settings["baseline_rms"])
        # skip whisper on silent clips: saves time, and whisper tends to
        # "hear" words like "Thank you." in silence
        text = audio.transcribe(samples, config.settings["code_words"]) if silence < 0.95 else ""
        result = scoring.evaluate(session_id, text, pitch, loudness, silence)

        # hands-free "false alarm": cancel the last alert from this call
        session = scoring.get_session(session_id)
        if session["last_alert_id"] and scoring.said_cancel_phrase(session_id):
            cancel_alert(session["last_alert_id"])
            session["last_alert_id"] = None

    if result["alert_triggered"]:
        send_alert(session_id, result, latitude, longitude)

    result["processing_ms"] = int((time.time() - start) * 1000)
    return result


@app.post("/end-session")
def end_session(session_id: str = Form(..., max_length=64)):
    scoring.end_session(session_id)
    return {"ended": True}


def send_alert(session_id, result, latitude, longitude):
    session = scoring.get_session(session_id)
    last_words = " ".join(" ".join(session["texts"]).split()[-10:])
    try:
        response = httpx.post(f"{config.CORE_API_URL}/api/alerts", timeout=5, json={
            "sessionId": session_id,
            "triggerPath": result["trigger_path"],
            "stressScore": result["stress_score"],
            "rollingScore": result["rolling_score"],
            "reasons": scoring.explain(result),
            "transcriptSnippet": last_words,
            "latitude": latitude,
            "longitude": longitude,
        })
        session["last_alert_id"] = response.json().get("id")
    except Exception as e:
        # never crash the call just because the alert couldn't be sent
        print("Could not send alert to core API:", e)


def cancel_alert(alert_id):
    try:
        httpx.post(f"{config.CORE_API_URL}/api/alerts/{alert_id}/cancel", timeout=5)
    except Exception as e:
        print("Could not cancel alert:", e)


@app.post("/calibrate")
def calibrate(audio_file: UploadFile = File(..., alias="audio")):
    if not config.settings["consent_given"]:
        raise HTTPException(403, "Please accept the privacy notice first (consent page).")
    data = read_upload(audio_file)
    if config.STUB_MODE:
        return use_baseline(150.0, 0.05, "Stub mode: saved a fake baseline.")

    samples = audio.decode(data)
    if samples.size < audio.SAMPLE_RATE * 3:
        raise HTTPException(400, "Recording too short, please talk for the full 10 seconds.")
    pitch, loudness, _ = audio.measure(samples)
    if pitch <= 0:
        raise HTTPException(400, "Couldn't hear a voice clearly, try again somewhere quieter.")
    return use_baseline(pitch, loudness, "Done! This is now your normal voice.")


@app.post("/calibrate/demo")
def calibrate_demo():
    return use_baseline(150.0, 0.05, "Using a demo baseline (not your real voice).")


def use_baseline(pitch, rms, message):
    config.save_baseline(pitch, rms)
    return {"baseline_pitch_hz": round(pitch, 1), "baseline_rms": round(rms, 4), "message": message}
