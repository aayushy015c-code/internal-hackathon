"""
transcription.py
-----------------
Wraps faster-whisper (a fast CPU-friendly re-implementation of OpenAI's
Whisper speech-to-text model).

Key choices, straight from the spec:
  - model size "base", not "tiny.en" - "base" is multilingual, and the
    team wants to support Hindi/Marathi code words later. "tiny.en" is
    English-only and would silently fail on those.
  - compute_type="int8" on CPU - int8 quantization roughly halves memory
    and speeds up inference, at a small accuracy cost that doesn't matter
    for short phrase-spotting.
  - The model loads ONCE at startup (see main.py's startup event), not on
    every request - loading it per-request would make each 4-second chunk
    take many seconds to process.
  - `initial_prompt` is passed the user's configured code words, which
    biases Whisper's decoding toward recognizing them correctly even when
    said quickly or quietly.

Note: `faster_whisper` is imported inside load_model(), not at the top of
this file, so this module (and the FastAPI app) can be imported - and
STUB_MODE can run - before faster-whisper is even installed. See
audio_utils.py's docstring and BUILD_ORDER.md for the full reasoning.
"""
from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from faster_whisper import WhisperModel

_model: "WhisperModel | None" = None


def load_model() -> "WhisperModel":
    """Loads the model on first call and reuses it after that. Safe to call more than once."""
    global _model
    if _model is None:
        from faster_whisper import WhisperModel

        print("[transcription] loading faster-whisper 'base' model (first run downloads ~150MB)...")
        _model = WhisperModel("base", device="cpu", compute_type="int8")
        print("[transcription] model loaded.")
    return _model


def transcribe(samples, sample_rate: int = 16000, initial_prompt: str = "") -> str:
    """
    Transcribes one chunk of audio (a numpy float32 array) to text.
    Returns "" for silence/empty audio rather than raising.
    """
    if samples is None or samples.size == 0:
        return ""

    model = load_model()
    segments, _info = model.transcribe(
        samples,
        language=None,  # let Whisper auto-detect, since code words may not be English
        initial_prompt=initial_prompt or None,
        beam_size=1,  # greedy-ish decoding: fast, good enough for short chunks
        vad_filter=False,  # we do our own silence handling in features.py
    )
    return " ".join(segment.text.strip() for segment in segments).strip()
