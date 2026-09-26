"""
audio_utils.py
--------------
Turns the raw bytes of a WebM audio blob (what the browser sends us every
4 seconds) into a plain numpy array of float32 samples at 16kHz mono - the
format both faster-whisper and librosa want.

We use PyAV (the `av` package), which bundles its own copy of the ffmpeg
libraries, so there is nothing extra to install on the machine. Everything
happens in memory (io.BytesIO) - the audio is never written to disk, per
the privacy requirement that raw audio never touches storage.

Note: `av` is imported INSIDE the function below, not at the top of this
file. That is deliberate - it means this module can be imported (and the
whole FastAPI app can start, in STUB_MODE) before `av` is even installed,
which is what makes the Step 1 skeleton usable with a tiny dependency list.
See BUILD_ORDER.md.
"""
from __future__ import annotations  # so the `av.AudioFrame` type hint below doesn't need `av` imported yet

import io
from typing import TYPE_CHECKING

import numpy as np

if TYPE_CHECKING:
    import av

TARGET_SAMPLE_RATE = 16000


def decode_webm_to_float32_mono_16k(webm_bytes: bytes) -> np.ndarray:
    """
    Decodes a standalone WebM/Opus blob into a 1-D float32 numpy array,
    resampled to 16kHz mono. Returns an empty array if the blob has no
    decodable audio (e.g. a corrupt or empty chunk) rather than raising -
    callers treat an empty array as "silence" and move on, so one bad
    chunk never crashes the analysis loop.
    """
    import av  # deferred import - see module docstring

    try:
        container = av.open(io.BytesIO(webm_bytes))
    except Exception:
        return np.zeros(0, dtype=np.float32)

    resampler = av.AudioResampler(format="fltp", layout="mono", rate=TARGET_SAMPLE_RATE)
    chunks: list[np.ndarray] = []

    try:
        for frame in container.decode(audio=0):
            # Each MediaRecorder-restarted blob is its own independent
            # WebM file with its own timestamps starting near zero, so we
            # drop the original pts and let the resampler re-timestamp
            # frames itself - otherwise PyAV can raise on "non-monotonic"
            # timestamps across chunks.
            frame.pts = None
            for resampled in resampler.resample(frame):
                chunks.append(_frame_to_mono_array(resampled))

        # Flush any audio buffered inside the resampler.
        for resampled in resampler.resample(None):
            chunks.append(_frame_to_mono_array(resampled))
    finally:
        container.close()

    if not chunks:
        return np.zeros(0, dtype=np.float32)

    return np.concatenate(chunks).astype(np.float32)


def _frame_to_mono_array(frame: av.AudioFrame) -> np.ndarray:
    arr = frame.to_ndarray()  # shape (1, n_samples) for planar float, mono
    return arr.reshape(-1)
