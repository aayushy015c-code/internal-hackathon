// api.js
// ------
// One shared place for every fetch() call this frontend makes, to the two
// backends: the FastAPI "analysis service" (audio in, scores out) and the
// Spring Boot "core API" (contacts, config, alerts). Every other JS file
// (call.js, dashboard.js, settings.js) imports this module instead of
// calling fetch() directly, so if a URL or error-handling pattern ever
// needs to change, it changes in exactly one place.
//
// This is loaded as a plain <script>, not an ES module (the team isn't
// using a build step), so it just attaches everything to `window.Api`.

const Api = (() => {
  // Change these two lines if you run the backends on different ports.
  const CORE_API_BASE = "http://localhost:8080";
  const ANALYSIS_API_BASE = "http://localhost:8000";

  /** Wraps fetch + JSON parsing + error handling so callers don't repeat try/catch everywhere. */
  async function request(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
      let detail = response.statusText;
      try {
        const body = await response.json();
        detail = body.detail || JSON.stringify(body);
      } catch (_) {
        /* response wasn't JSON - fall back to statusText */
      }
      throw new Error(`${response.status}: ${detail}`);
    }
    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
      return response.json();
    }
    return response.text();
  }

  // ---- Core API (Spring Boot, :8080) ----

  const Contacts = {
    list: () => request(`${CORE_API_BASE}/api/contacts`),
    create: (contact) =>
      request(`${CORE_API_BASE}/api/contacts`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(contact),
      }),
    update: (id, contact) =>
      request(`${CORE_API_BASE}/api/contacts/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(contact),
      }),
    remove: (id) => request(`${CORE_API_BASE}/api/contacts/${id}`, { method: "DELETE" }),
  };

  const Config = {
    get: () => request(`${CORE_API_BASE}/api/config`),
    update: (config) =>
      request(`${CORE_API_BASE}/api/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
      }),
  };

  const Alerts = {
    list: () => request(`${CORE_API_BASE}/api/alerts`),
    sendTestAlert: () => request(`${CORE_API_BASE}/api/alerts/test-alert`, { method: "POST" }),
    cancel: (id) => request(`${CORE_API_BASE}/api/alerts/${id}/cancel`, { method: "POST" }),
  };

  const Health = {
    coreApi: () => request(`${CORE_API_BASE}/api/health`),
    analysisService: () => request(`${ANALYSIS_API_BASE}/health`),
  };

  // ---- Analysis service (FastAPI, :8000) ----

  const Analysis = {
    /**
     * Sends one 4-second audio chunk for analysis.
     * `extra` can include { latitude, longitude, force_alert } - all optional.
     */
    analyzeChunk: (sessionId, blob, extra = {}) => {
      const form = new FormData();
      form.append("session_id", sessionId);
      form.append("audio", blob, "chunk.webm");
      if (extra.latitude != null) form.append("latitude", extra.latitude);
      if (extra.longitude != null) form.append("longitude", extra.longitude);
      if (extra.force_alert) form.append("force_alert", "true");
      return request(`${ANALYSIS_API_BASE}/analyze`, { method: "POST", body: form });
    },

    calibrate: (blob) => {
      const form = new FormData();
      form.append("audio", blob, "calibration.webm");
      return request(`${ANALYSIS_API_BASE}/calibrate`, { method: "POST", body: form });
    },

    calibrateDemo: () => request(`${ANALYSIS_API_BASE}/calibrate/demo`, { method: "POST" }),

    reloadConfig: () => request(`${ANALYSIS_API_BASE}/reload-config`, { method: "POST" }),
  };

  return { Contacts, Config, Alerts, Health, Analysis };
})();
