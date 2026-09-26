// recorder.js
// -----------
// Captures the user's own microphone in 4-second chunks and sends each one
// to the analysis service. This is the trickiest piece of the whole
// frontend, because of two easy-to-miss pitfalls (both called out in the
// spec, both cost the earlier draft real debugging time):
//
// 1. DO NOT use a single MediaRecorder with `start(4000)` (a "timeslice").
//    Only the very first blob produced that way contains the WebM header;
//    every later blob is a headerless fragment the server can't decode on
//    its own. Instead, we create a BRAND NEW MediaRecorder every 4 seconds
//    and call plain `start()`/`stop()` - each resulting blob is then a
//    complete, independent WebM file.
//
// 2. Recording must not wait for the network. We keep recording a new 4s
//    chunk every 4 seconds no matter what, and if the *previous* chunk's
//    analysis hasn't come back yet from the server, we simply DROP the
//    new chunk instead of sending it (or queueing it). This is what keeps
//    a slow response from building up an ever-growing backlog of audio to
//    process, which would make the "distress detected" alert arrive later
//    and later the longer a call runs.
//
// Recording is always taken from the LOCAL microphone track, never from
// the remote caller's audio - see call.js, which passes in the same local
// stream it uses for the PeerJS call.
const Recorder = (() => {
  const CHUNK_MS = 4000;

  let localStream = null;
  let mediaRecorder = null;
  let sessionId = null;
  let running = false;
  let sendInFlight = false; // the "one in-flight request at a time" guard
  let onChunkAnalyzed = () => {};
  let onChunkError = () => {};
  const geolocation = { latitude: null, longitude: null };

  function _generateSessionId() {
    return typeof crypto !== "undefined" && crypto.randomUUID
      ? crypto.randomUUID()
      : `sess-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function _watchLocation() {
    if (!("geolocation" in navigator)) return;
    navigator.geolocation.watchPosition(
      (pos) => {
        geolocation.latitude = pos.coords.latitude;
        geolocation.longitude = pos.coords.longitude;
      },
      () => {
        // User denied location, or it's unavailable. Alerts still work,
        // they just won't include a location link. Nothing to do here.
      },
      { enableHighAccuracy: false, maximumAge: 30000, timeout: 5000 }
    );
  }

  function _recordOneChunk() {
    if (!running) return;

    const chunks = [];
    mediaRecorder = new MediaRecorder(localStream, { mimeType: "audio/webm;codecs=opus" });

    mediaRecorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) chunks.push(event.data);
    };

    mediaRecorder.onstop = () => {
      const blob = new Blob(chunks, { type: "audio/webm" });
      _sendChunk(blob); // fire-and-forget on purpose - see module docstring
      if (running) _recordOneChunk(); // keep the 4s cadence going regardless of network speed
    };

    mediaRecorder.start();
    setTimeout(() => {
      if (mediaRecorder && mediaRecorder.state !== "inactive") mediaRecorder.stop();
    }, CHUNK_MS);
  }

  async function _sendChunk(blob) {
    if (sendInFlight) {
      return; // drop this chunk - the previous one is still being analyzed
    }
    sendInFlight = true;
    try {
      const result = await Api.Analysis.analyzeChunk(sessionId, blob, {
        latitude: geolocation.latitude,
        longitude: geolocation.longitude,
      });
      onChunkAnalyzed(result);
    } catch (err) {
      onChunkError(err);
    } finally {
      sendInFlight = false;
    }
  }

  /**
   * Starts recording. `stream` should be the LOCAL microphone MediaStream
   * (call.js already has one for PeerJS and passes it straight through).
   * If omitted, this requests its own mic access.
   */
  async function start({ stream, onAnalyzed, onError } = {}) {
    if (running) return sessionId;
    localStream = stream || (await navigator.mediaDevices.getUserMedia({ audio: true }));
    sessionId = _generateSessionId();
    onChunkAnalyzed = onAnalyzed || (() => {});
    onChunkError = onError || ((e) => console.error("[recorder] chunk error:", e));
    running = true;
    _watchLocation();
    _recordOneChunk();
    return sessionId;
  }

  /** Stops recording. Does NOT stop the tracks of a stream you passed in yourself
   * (e.g. one still being used for the live call) - only stops tracks it opened itself. */
  function stop(stopOwnTracksOnly = true) {
    const wasSelfOpened = stopOwnTracksOnly;
    running = false;
    if (mediaRecorder && mediaRecorder.state !== "inactive") {
      mediaRecorder.stop();
    }
    if (wasSelfOpened && localStream) {
      localStream.getTracks().forEach((track) => track.stop());
    }
  }

  function isRunning() {
    return running;
  }

  function getSessionId() {
    return sessionId;
  }

  return { start, stop, isRunning, getSessionId };
})();
