"""
models.py
---------
Pydantic models describing the JSON shapes this service returns. FastAPI
uses these to validate the response and to auto-generate the interactive
docs at http://localhost:8000/docs - open that page in a browser any time
you want to see or try the API without writing frontend code.
"""
from typing import Optional

from pydantic import BaseModel


class CodewordSignal(BaseModel):
    matched: bool
    phrase: Optional[str] = None
    confidence: int = 0


class PitchSignal(BaseModel):
    value_hz: float
    delta_pct: float


class EnergySignal(BaseModel):
    delta_pct: float


class SilenceSignal(BaseModel):
    ratio: float


class Signals(BaseModel):
    codeword: CodewordSignal
    pitch: PitchSignal
    energy: EnergySignal
    silence: SilenceSignal


class AnalyzeResponse(BaseModel):
    session_id: str
    stress_score: int
    rolling_score: int
    alert_triggered: bool
    trigger_path: Optional[str] = None
    signals: Signals
    processing_ms: int


class CalibrateResponse(BaseModel):
    baseline_pitch_hz: float
    baseline_rms: float
    message: str
