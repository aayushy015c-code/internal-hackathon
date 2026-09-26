// All calls to our two backends are in this file.
// If you change a port, change it here only.
const CORE_API = "http://localhost:8080";     // Spring Boot
const ANALYSIS_API = "http://localhost:8000"; // FastAPI

// fetch() + turn errors into a readable message
async function request(url, options = {}) {
  const res = await fetch(url, options);
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
  Contacts: {
    list: () => request(CORE_API + "/api/contacts"),
    create: (contact) => sendJson("POST", CORE_API + "/api/contacts", contact),
    update: (id, contact) => sendJson("PUT", CORE_API + "/api/contacts/" + id, contact),
    remove: (id) => request(CORE_API + "/api/contacts/" + id, { method: "DELETE" }),
  },

  Config: {
    get: () => request(CORE_API + "/api/config"),
    update: (config) => sendJson("PUT", CORE_API + "/api/config", config),
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
    calibrate: (blob) => {
      const form = new FormData();
      form.append("audio", blob, "calibration.webm");
      return request(ANALYSIS_API + "/calibrate", { method: "POST", body: form });
    },
    calibrateDemo: () => request(ANALYSIS_API + "/calibrate/demo", { method: "POST" }),
    reloadConfig: () => request(ANALYSIS_API + "/reload-config", { method: "POST" }),
  },
};
