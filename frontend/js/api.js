// All calls to our two backends are in this file.
// If you change a port, change it here only.
const CORE_API = "http://localhost:8080";     // Spring Boot
const ANALYSIS_API = "http://localhost:8000"; // FastAPI

// ---- Who am I? ----
// The first time this browser opens the app, it registers a user and gets a
// permanent call ID plus a secret access key. The key is saved in this browser
// (localStorage) and sent with every request, so the backend knows whose data
// to use. Settings > "Your identity" shows the key, so you can use the same
// identity in another browser.
const KEY_STORAGE = "silent-signal-access-key";
let userReady = null; // one registration per page, even if many requests start at once

function savedKey() {
  try {
    return localStorage.getItem(KEY_STORAGE);
  } catch (e) {
    return null; // private window / storage blocked: works, but only for this visit
  }
}

function saveKey(key) {
  try {
    if (key) localStorage.setItem(KEY_STORAGE, key);
    else localStorage.removeItem(KEY_STORAGE);
  } catch (e) {}
}

function ensureUser() {
  if (!userReady) {
    userReady = (async () => {
      const existing = savedKey();
      if (existing) return existing;
      const res = await fetch(CORE_API + "/api/users", { method: "POST" });
      if (!res.ok) throw new Error(res.status + ": could not register");
      const user = await res.json();
      saveKey(user.accessKey);
      return user.accessKey;
    })();
    userReady.catch(() => (userReady = null)); // try again next time if the server was down
  }
  return userReady;
}

// fetch() + our access key + turn errors into a readable message
async function request(url, options = {}, retried = false) {
  const key = await ensureUser();
  const res = await fetch(url, { ...options, headers: { ...(options.headers || {}), "X-User-Key": key } });
  if (res.status === 401 && !retried && url.startsWith(CORE_API)) {
    // the server doesn't know this key (e.g. its database was reset): register again.
    // Only the first request to notice resets it; the others wait for that same new user.
    if (savedKey() === key) {
      saveKey(null);
      userReady = null;
    }
    return request(url, options, true);
  }
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      message = body.detail || body.error || message;
    } catch (e) {}
    throw new Error(res.status + ": " + message);
  }
  const type = res.headers.get("content-type") || "";
  return type.includes("json") ? res.json() : res.text();
}

function sendJson(method, url, data) {
  return request(url, {
    method: method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

// Use this before putting any text from the server into innerHTML,
// otherwise a contact name like "<script>" would run as code.
function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text == null ? "" : String(text);
  return div.innerHTML;
}

const Api = {
  Me: {
    get: () => request(CORE_API + "/api/users/me"),
    accessKey: () => savedKey(),
    // use an existing identity on this browser (checks the key first)
    signIn: async (key) => {
      const res = await fetch(CORE_API + "/api/users/me", { headers: { "X-User-Key": key } });
      if (!res.ok) throw new Error("That access key isn't known on this computer.");
      saveKey(key);
      userReady = Promise.resolve(key);
      return res.json();
    },
    // forget the identity on this browser (the user and their data stay in the database)
    signOut: () => {
      saveKey(null);
      userReady = null;
    },
  },

  Contacts: {
    list: () => request(CORE_API + "/api/contacts"),
    create: (contact) => sendJson("POST", CORE_API + "/api/contacts", contact),
    update: (id, contact) => sendJson("PUT", CORE_API + "/api/contacts/" + id, contact),
    remove: (id) => request(CORE_API + "/api/contacts/" + id, { method: "DELETE" }),
  },

  Config: {
    get: () => request(CORE_API + "/api/config"),
    update: (config) => sendJson("PUT", CORE_API + "/api/config", config),
    consent: (given) => sendJson("PUT", CORE_API + "/api/config/consent", { given: given }),
  },

  Disguise: {
    info: () => request(CORE_API + "/api/disguise"),
    checkPin: (pin) => sendJson("POST", CORE_API + "/api/disguise/check-pin", { pin: pin }),
  },

  MyData: {
    exportUrl: CORE_API + "/api/data/export",
    deleteAll: () => request(CORE_API + "/api/data", { method: "DELETE" }),
  },

  Alerts: {
    list: () => request(CORE_API + "/api/alerts"),
    sendTestAlert: () => request(CORE_API + "/api/alerts/test-alert", { method: "POST" }),
    cancel: (id) => request(CORE_API + "/api/alerts/" + id + "/cancel", { method: "POST" }),
  },

  Analysis: {
    // send one 4 second audio clip
    analyzeChunk: (sessionId, blob, location) => {
      const form = new FormData();
      form.append("session_id", sessionId);
      form.append("audio", blob, "chunk.webm");
      if (location.latitude != null) {
        form.append("latitude", location.latitude);
        form.append("longitude", location.longitude);
      }
      return request(ANALYSIS_API + "/analyze", { method: "POST", body: form });
    },
    endSession: (sessionId) => {
      const form = new FormData();
      form.append("session_id", sessionId);
      return request(ANALYSIS_API + "/end-session", { method: "POST", body: form });
    },
    calibrate: (blob) => {
      const form = new FormData();
      form.append("audio", blob, "calibration.webm");
      return request(ANALYSIS_API + "/calibrate", { method: "POST", body: form });
    },
    calibrateDemo: () => request(ANALYSIS_API + "/calibrate/demo", { method: "POST" }),
    reloadConfig: () => request(ANALYSIS_API + "/reload-config", { method: "POST" }),
  },
};
