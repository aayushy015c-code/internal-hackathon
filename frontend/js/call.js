// The call page: a voice call using PeerJS (WebRTC), and voice analysis at the same time.
// PeerJS gives us a free server to connect the two browsers, so we don't need our own.

let peer = null;
let localStream = null; // our microphone, shared by the call and the recorder
let currentCall = null;
let timer = null;

// Sends each result to the dashboard tab (browser tab-to-tab messaging, no server needed)
const liveChannel = new BroadcastChannel("silent-signal-live");

const el = (id) => document.getElementById(id);

async function getMic() {
  if (!localStream) {
    localStream = await navigator.mediaDevices.getUserMedia({ audio: true });
  }
  return localStream;
}

// turn the microphone off when nothing needs it
function releaseMicIfUnused() {
  if (!currentCall && !Recorder.running && localStream) {
    localStream.getTracks().forEach((t) => t.stop());
    localStream = null;
  }
}

// ---- Call ----

// ---- Call ID ----
// Your call ID is saved in this browser (localStorage), so it's the same every
// time you open this page and people who saved it can still reach you.
// No accounts or server involved: a different browser or cleared site data
// gets a new ID. If storage is blocked (e.g. private window) we fall back to a
// new random ID on each visit, like before.

const CALL_ID_KEY = "silent-signal-call-id";
const ID_LETTERS = "abcdefghjkmnpqrstuvwxyz23456789"; // no 0/o, 1/i/l, easy to read out loud

// Other open call pages in this browser, so a second tab doesn't steal the saved ID
const idChannel = new BroadcastChannel("silent-signal-call-id");
idChannel.onmessage = (e) => {
  if (e.data && e.data.whoHas && peer && peer.open && peer.id === e.data.whoHas) {
    idChannel.postMessage({ iHave: e.data.whoHas });
  }
};

function newCallId() {
  const bytes = crypto.getRandomValues(new Uint8Array(10));
  const s = Array.from(bytes, (b) => ID_LETTERS[b % ID_LETTERS.length]).join("");
  return "ss-" + s.slice(0, 5) + "-" + s.slice(5); // e.g. ss-k7p3m-9qdwx
}

// null = storage not available
function readSavedId() {
  try {
    localStorage.setItem(CALL_ID_KEY + "-test", "1");
    localStorage.removeItem(CALL_ID_KEY + "-test");
    return localStorage.getItem(CALL_ID_KEY) || "";
  } catch (e) {
    return null;
  }
}

function saveId(id) {
  try {
    localStorage.setItem(CALL_ID_KEY, id);
  } catch (e) {}
}

// Is this ID open in another call tab of this same browser?
function usedByAnotherTab(id) {
  return new Promise((resolve) => {
    const onReply = (e) => {
      if (e.data && e.data.iHave === id) {
        idChannel.removeEventListener("message", onReply);
        resolve(true);
      }
    };
    idChannel.addEventListener("message", onReply);
    idChannel.postMessage({ whoHas: id });
    setTimeout(() => {
      idChannel.removeEventListener("message", onReply);
      resolve(false);
    }, 500);
  });
}

// step: 0 = first try, 1 = retried the same ID, 2 = trying a brand new ID
function connectPeer(id, step) {
  peer = id ? new Peer(id) : new Peer(); // no id = PeerJS picks a random one
  peer.on("open", (openId) => (el("my-peer-id").value = openId));
  peer.on("error", (err) => {
    if (err.type === "unavailable-id" && id) {
      handleIdTaken(id, step);
      return;
    }
    el("call-status").textContent = "Error: " + err.type;
  });
  peer.on("call", onIncomingCall);
}

async function handleIdTaken(id, step) {
  peer.destroy();
  if (step === 0) {
    // right after a reload PeerJS can still hold our own ID for a moment: wait and retry it
    setTimeout(() => connectPeer(id, 1), 3000);
    return;
  }
  if (step === 1 && (await usedByAnotherTab(id))) {
    // our own ID is open in another tab: use a temporary one here, keep the saved one
    el("call-status").textContent = "Your call ID is open in another tab. This tab uses a temporary ID.";
    connectPeer(null, 2);
    return;
  }
  if (step === 1) {
    // someone else on the public PeerJS server has this ID: make a new one and keep it
    const fresh = newCallId();
    saveId(fresh);
    connectPeer(fresh, 2);
    return;
  }
  // the new ID was taken too (very unlikely): don't get stuck, use a random one for now
  connectPeer(null, 2);
}

// someone is calling us
async function onIncomingCall(call) {
  call.answer(await getMic());
  setupCall(call);
}

function startPeer() {
  let id = readSavedId();
  if (id === "") {
    id = newCallId();
    saveId(id);
  }
  connectPeer(id, 0); // id is null when storage is blocked: random ID, like before
}
startPeer();

el("call-btn").onclick = async () => {
  const id = el("remote-peer-id").value.trim();
  if (!id) return;
  el("call-status").textContent = "Calling...";
  setupCall(peer.call(id, await getMic()));
};

function setupCall(call) {
  currentCall = call;
  call.on("stream", (remoteStream) => {
    if (timer) return; // "stream" can fire twice, only set up once
    el("remote-audio").srcObject = remoteStream;
    showInCall();
    startAnalysis(); // start listening as soon as the call connects
  });
  call.on("close", endCall);
  call.on("error", endCall);
}

el("hangup-btn").onclick = () => {
  if (currentCall) currentCall.close();
  endCall();
};

function showInCall() {
  el("setup-panel").classList.add("hidden");
  el("in-call-panel").classList.remove("hidden");
  el("call-timer").classList.remove("hidden");
  el("call-status").textContent = "Connected";
  const start = Date.now();
  timer = setInterval(() => {
    const s = Math.floor((Date.now() - start) / 1000);
    el("call-timer").textContent = String(Math.floor(s / 60)).padStart(2, "0") + ":" + String(s % 60).padStart(2, "0");
  }, 1000);
}

function endCall() {
  clearInterval(timer);
  timer = null;
  currentCall = null;
  el("setup-panel").classList.remove("hidden");
  el("in-call-panel").classList.add("hidden");
  el("call-timer").classList.add("hidden");
  el("call-timer").textContent = "00:00";
  el("call-status").textContent = "Not connected";
  stopAnalysis();
  releaseMicIfUnused();
}

el("mute-btn").onclick = () => {
  const track = localStream && localStream.getAudioTracks()[0];
  if (!track) return;
  track.enabled = !track.enabled;
  el("mute-btn").textContent = track.enabled ? "Mute" : "Unmute";
};

el("copy-id-btn").onclick = () => navigator.clipboard.writeText(el("my-peer-id").value);

// ---- Voice analysis ----

async function startAnalysis() {
  let shareLocation = false;
  try {
    shareLocation = (await Api.Config.get()).shareLocation;
  } catch (err) {}
  Recorder.start(
    await getMic(),
    (result) => {
      el("analysis-error").textContent = "";
      // Nothing is shown here on purpose, even for alerts. Someone looking at
      // the phone should only see a normal call. Results go to the dashboard.
      liveChannel.postMessage(result);
    },
    (err) => (el("analysis-error").textContent = "Analysis problem: " + err.message),
    shareLocation
  );
  el("analysis-dot").classList.add("on");
  el("analysis-label").textContent = "Voice analysis: on";
  el("analysis-toggle-btn").textContent = "Stop";
}

function stopAnalysis() {
  Recorder.stop();
  el("analysis-dot").classList.remove("on");
  el("analysis-label").textContent = "Voice analysis: off";
  el("analysis-toggle-btn").textContent = "Start";
}

el("analysis-toggle-btn").onclick = () => {
  if (Recorder.running) {
    stopAnalysis();
    releaseMicIfUnused();
  } else {
    startAnalysis();
  }
};
