// call.js
// -------
// Wires up the /call page: a WebRTC voice call via PeerJS, plus starting
// and stopping the background voice analysis (recorder.js) alongside it.
//
// PeerJS handles the messy parts of WebRTC (signaling, ICE negotiation)
// for us via its own free public broker - `new Peer()` with no arguments
// gets you a random ID and a working connection with zero server setup on
// our side, which is exactly what a 44-hour hackathon team wants.
//
// One microphone stream is shared between the call and the analysis
// recorder - `ensureLocalStream()` is the single place that stream is
// created, and `_maybeReleaseMic()` is the single place it's torn down,
// so the browser's "microphone in use" indicator is only ever on while
// something here actually needs it.

let peer = null;
let localStream = null;
let currentCall = null;
let timerInterval = null;
let callStartTime = null;

// BroadcastChannel is a same-origin, tab-to-tab messaging API built into
// the browser - NOT a network connection and NOT a WebSocket (which the
// spec rules out). We use it so the /dashboard page, open in another tab,
// can show a live risk meter for what's happening on THIS /call tab
// without either page needing its own server round trip for every chunk.
const liveChannel = new BroadcastChannel("silent-signal-live");

const myIdInput = document.getElementById("my-peer-id");
const remoteIdInput = document.getElementById("remote-peer-id");
const callBtn = document.getElementById("call-btn");
const hangupBtn = document.getElementById("hangup-btn");
const muteBtn = document.getElementById("mute-btn");
const copyBtn = document.getElementById("copy-id-btn");
const setupPanel = document.getElementById("setup-panel");
const inCallPanel = document.getElementById("in-call-panel");
const callStatus = document.getElementById("call-status");
const callTimer = document.getElementById("call-timer");
const remoteAudio = document.getElementById("remote-audio");
const analysisDot = document.getElementById("analysis-dot");
const analysisLabel = document.getElementById("analysis-label");
const analysisToggleBtn = document.getElementById("analysis-toggle-btn");

async function ensureLocalStream() {
  if (!localStream) {
    localStream = await navigator.mediaDevices.getUserMedia({ audio: true });
  }
  return localStream;
}

function _maybeReleaseMic() {
  if (!currentCall && !Recorder.isRunning() && localStream) {
    localStream.getTracks().forEach((track) => track.stop());
    localStream = null;
  }
}

function initPeer() {
  peer = new Peer();

  peer.on("open", (id) => {
    myIdInput.value = id;
  });

  peer.on("call", async (call) => {
    const stream = await ensureLocalStream();
    call.answer(stream);
    _wireCall(call);
  });

  peer.on("error", (err) => {
    console.error("[call] PeerJS error:", err);
    callStatus.textContent = `Connection error: ${err.type || err.message}`;
  });
}

function _wireCall(call) {
  currentCall = call;
  call.on("stream", (remoteStream) => {
    remoteAudio.srcObject = remoteStream;
    _enterInCallUi();
    startAnalysis(); // auto-start monitoring the moment the call actually connects
  });
  call.on("close", _handleHangup);
  call.on("error", (err) => {
    console.error("[call] call error:", err);
    _handleHangup();
  });
}

callBtn.addEventListener("click", async () => {
  const remoteId = remoteIdInput.value.trim();
  if (!remoteId) return;
  const stream = await ensureLocalStream();
  callStatus.textContent = "Calling...";
  _wireCall(peer.call(remoteId, stream));
});

hangupBtn.addEventListener("click", () => {
  if (currentCall) currentCall.close();
  _handleHangup();
});

function _handleHangup() {
  if (timerInterval) clearInterval(timerInterval);
  callTimer.classList.add("hidden");
  callTimer.textContent = "00:00";
  callStatus.textContent = "Not connected";
  setupPanel.classList.remove("hidden");
  inCallPanel.classList.add("hidden");
  inCallPanel.classList.remove("flex");
  currentCall = null;
  stopAnalysis();
  _maybeReleaseMic();
}

function _enterInCallUi() {
  setupPanel.classList.add("hidden");
  inCallPanel.classList.remove("hidden");
  inCallPanel.classList.add("flex");
  callStatus.textContent = "Connected";
  callTimer.classList.remove("hidden");
  callStartTime = Date.now();
  timerInterval = setInterval(_updateTimer, 1000);
}

function _updateTimer() {
  const elapsedSeconds = Math.floor((Date.now() - callStartTime) / 1000);
  const mm = String(Math.floor(elapsedSeconds / 60)).padStart(2, "0");
  const ss = String(elapsedSeconds % 60).padStart(2, "0");
  callTimer.textContent = `${mm}:${ss}`;
}

muteBtn.addEventListener("click", () => {
  const track = localStream && localStream.getAudioTracks()[0];
  if (!track) return;
  track.enabled = !track.enabled;
  muteBtn.textContent = track.enabled ? "🎤" : "🔇";
});

copyBtn.addEventListener("click", () => {
  myIdInput.select();
  document.execCommand("copy");
});

// ---- Voice analysis (records locally, sends chunks to the analysis service) ----

async function startAnalysis() {
  if (Recorder.isRunning()) return;
  const stream = await ensureLocalStream();
  await Recorder.start({
    stream,
    onAnalyzed: (result) => {
      liveChannel.postMessage(result);
      if (result.alert_triggered) {
        // Deliberately silent here - a covert alert must not surface anything
        // visible on the call screen itself. The dashboard (usually open in
        // another tab, or checked afterwards) is where an alert shows up.
        console.log("[call] alert triggered:", result);
      }
    },
    onError: (err) => console.error("[call] analysis error:", err),
  });
  analysisDot.classList.replace("bg-slate-500", "bg-emerald-400");
  analysisLabel.textContent = "Voice analysis: on";
  analysisToggleBtn.textContent = "Stop";
}

function stopAnalysis() {
  Recorder.stop(false); // never let the recorder decide to release the mic; call.js owns that
  analysisDot.classList.replace("bg-emerald-400", "bg-slate-500");
  analysisLabel.textContent = "Voice analysis: off";
  analysisToggleBtn.textContent = "Start";
}

analysisToggleBtn.addEventListener("click", async () => {
  if (Recorder.isRunning()) {
    stopAnalysis();
    _maybeReleaseMic();
  } else {
    await startAnalysis();
  }
});

initPeer();
