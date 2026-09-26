"""
main.py
-------
The FastAPI app itself - this is the file you run (`uvicorn main:app`).
It owns exactly the HTTP layer: reading the request, calling out to the
other modules to do the real work, and shaping the response. All the
actual audio/ML logic lives in the other files in this folder:

    audio_utils.py    decode WebM bytes -> numpy samples
    transcription.py  faster-whisper speech-to-text
    features.py       pitch / energy / silence via librosa
    scoring.py         combine everything into a score + trigger decision
    state.py          per-call rolling buffers and cooldown
    config.py         cached copy of the user's settings from the core API
    models.py         response shapes (pydantic)

Endpoints:
    GET  /health          - is this service up?
    POST /analyze         - one 4-second audio chunk in, a score out
    POST /calibrate       - ~10s of normal speech in, a baseline out
    POST /calibrate/demo  - skip recording, use a reasonable demo baseline
    POST /reload-config   - re-fetch code words/sensitivity/etc from the core API
"""
import time
from typing import Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

import audio_utils
import config
import features
import scoring
import state
import transcription
from models import (
    AnalyzeResponse,
    CalibrateResponse,
    CodewordSignal,
    EnergySignal,
    PitchSignal,
    Signals,
    SilenceSignal,
)

# Security hygiene: hard cap on how large one audio chunk is allowed to be.
# A normal 4-second Opus/WebM chunk is well under 100KB; 2MB is generous
# headroom while still blocking anything absurd.
MAX_UPLOAD_BYTES = 2 * 1024 * 1024

app = FastAPI(title="Distress Analysis Service", version="0.1.0")

# CORS: only our own localhost frontend is allowed to call this API.
# Keep this list in sync with core-api's application.properties.
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5500", "http://127.0.0.1:5500",
        "http://localhost:3000", "http://127.0.0.1:3000",
        "http://localhost:8081", "http://127.0.0.1:8081",
    ],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


@app.on_event("startup")
def on_startup():
    config.refresh_from_core_api()
    if not config.STUB_MODE:
        # Load the whisper model now, so the first real /analyze call isn't
        # slow. In STUB_MODE we skip this entirely - that's the whole point
        # of stub mode: run the pipeline before the ML deps are even set up.
        transcription.load_model()


@app.get("/health")
def health():
    return {"status": "ok", "service": "analysis-service", "stub_mode": config.STUB_MODE}


@app.post("/reload-config")
def reload_config():
    """Call this after saving changes on the /settings page so this service
    picks up new code words / sensitivity / etc without a restart."""
    ok = config.refresh_from_core_api()
    return {"reloaded": ok}


def _validate_upload(audio: UploadFile, raw_bytes: bytes) -> None:
    if len(raw_bytes) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Audio chunk too large (max 2MB per 4s chunk).")
    if audio.content_type and not audio.content_type.startswith("audio/"):
        raise HTTPException(status_code=415, detail=f"Unsupported content type: {audio.content_type}")


def _last_n_words(text: str, n: int = 10) -> str:
    words = text.split()
    return " ".join(words[-n:])


def _send_alert_to_core_api(session_id: str, result: scoring.ScoreResult,
                             latitude: Optional[float], longitude: Optional[float]) -> None:
    """Fire-and-forget POST to the core API. Never raises - a notification
    problem must not crash or stall the analysis loop for the current call."""
    session = state.get_session(session_id)
    combined_transcript = " ".join(session.recent_transcripts)

    payload = {
        "sessionId": session_id,
        "triggerPath": result.trigger_path,
        "stressScore": result.stress_score,
        "rollingScore": result.rolling_score,
        "reasons": scoring.build_reason_text(result),
        "transcriptSnippet": _last_n_words(combined_transcript, 10),
        "latitude": latitude,
        "longitude": longitude,
    }
    try:
        import httpx
        response = httpx.post(f"{config.CORE_API_URL}/api/alerts", json=payload, timeout=5.0)
        response.raise_for_status()
        session.last_alert_id = response.json().get("id")
    except Exception as e:  # noqa: BLE001
        print(f"[alerts] failed to notify core API: {e}")


def _cancel_alert_on_core_api(alert_id: int) -> None:
    """The spoken cancel-word counterpart to the dashboard's 'Mark false alarm' button."""
    try:
        import httpx
        httpx.post(f"{config.CORE_API_URL}/api/alerts/{alert_id}/cancel", timeout=5.0)
    except Exception as e:  # noqa: BLE001
        print(f"[alerts] failed to cancel alert {alert_id}: {e}")


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(
    session_id: str = Form(...),
    audio: UploadFile = File(...),
    latitude: Optional[float] = Form(None),
    longitude: Optional[float] = Form(None),
    force_alert: bool = Form(False),  # only meaningful in STUB_MODE - lets Step 1 testing trigger an alert on demand
):
    start = time.time()

    if not config.live_config.analysis_active:
        # The one-tap "stop" consent switch is on - refuse to analyze anything.
        raise HTTPException(status_code=403, detail="Analysis is currently stopped by the user.")

    raw = await audio.read()
    _validate_upload(audio, raw)

    if config.STUB_MODE:
        result = scoring.stub_result(force_alert)
    else:
        samples = audio_utils.decode_webm_to_float32_mono_16k(raw)
        transcript_chunk = transcription.transcribe(
            samples, sample_rate=16000, initial_prompt=", ".join(config.live_config.code_words)
        )
        pitch_hz = features.compute_pitch_hz(samples)
        rms = features.compute_rms(samples)
        silence_ratio = features.compute_silence_ratio(samples, baseline_rms=config.live_config.baseline_rms)
        result = scoring.evaluate_chunk(session_id, transcript_chunk, pitch_hz, rms, silence_ratio)

        # Spoken "false alarm" path: if the user's cancel phrase shows up in
        # the recent transcript and we have a pending alert for this call,
        # cancel it - the same effect as the dashboard's "Mark false alarm"
        # button, just hands-free.
        session = state.get_session(session_id)
        combined_transcript = " ".join(session.recent_transcripts)
        if session.last_alert_id and scoring.match_cancel_word(combined_transcript, config.live_config.cancel_code_word):
            _cancel_alert_on_core_api(session.last_alert_id)
            session.last_alert_id = None

    if result.alert_triggered:
        _send_alert_to_core_api(session_id, result, latitude, longitude)

    processing_ms = int((time.time() - start) * 1000)

    return AnalyzeResponse(
        session_id=session_id,
        stress_score=result.stress_score,
        rolling_score=result.rolling_score,
        alert_triggered=result.alert_triggered,
        trigger_path=result.trigger_path,
        signals=Signals(
            codeword=CodewordSignal(
                matched=result.codeword_matched,
                phrase=result.codeword_phrase,
                confidence=result.codeword_confidence,
            ),
            pitch=PitchSignal(value_hz=round(result.pitch_hz, 1), delta_pct=round(result.pitch_delta_pct, 1)),
            energy=EnergySignal(delta_pct=round(result.energy_delta_pct, 1)),
            silence=SilenceSignal(ratio=round(result.silence_ratio, 2)),
        ),
        processing_ms=processing_ms,
    )


@app.post("/calibrate", response_model=CalibrateResponse)
async def calibrate(audio: UploadFile = File(...)):
    raw = await audio.read()
    _validate_upload(audio, raw)

    if config.STUB_MODE:
        pitch_hz, rms = 150.0, 0.05
    else:
        samples = audio_utils.decode_webm_to_float32_mono_16k(raw)
        if samples.size < 16000:  # less than ~1 second of decodable audio
            raise HTTPException(
                status_code=400,
                detail="Calibration clip too short - please record about 10 seconds of normal speech.",
            )
        pitch_hz = features.compute_pitch_hz(samples)
        rms = features.compute_rms(samples)
        if pitch_hz <= 0:
            raise HTTPException(
                status_code=400,
                detail="Could not detect a clear pitch - please try again somewhere quieter.",
            )

    config.live_config.baseline_pitch_hz = pitch_hz
    config.live_config.baseline_rms = rms
    config.push_baseline_to_core_api(pitch_hz, rms)

    return CalibrateResponse(
        baseline_pitch_hz=round(pitch_hz, 1),
        baseline_rms=round(rms, 4),
        message="Calibration complete - this is now your voice baseline.",
    )


@app.post("/calibrate/demo", response_model=CalibrateResponse)
def calibrate_demo():
    """The "use demo baseline" option from the spec - skips recording entirely."""
    pitch_hz, rms = 150.0, 0.05
    config.live_config.baseline_pitch_hz = pitch_hz
    config.live_config.baseline_rms = rms
    config.push_baseline_to_core_api(pitch_hz, rms)
    return CalibrateResponse(
        baseline_pitch_hz=pitch_hz,
        baseline_rms=rms,
        message="Using a demo baseline (not your real voice) - fine for a quick demo, not for real use.",
    )
