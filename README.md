# Silent Signal

**PSWB03: Non-Verbal Distress Detection via Voice Pattern Analysis**

Silent Signal listens to **your side** of a phone call. If you say a secret code word, or your voice sounds stressed for a while, it quietly sends a push notification to people you trust. The person you're talking to sees nothing.

All the audio processing happens on the laptop. Audio is never saved and never sent to the internet.

---

## How it works

```
  Browser (call page)                Analysis service (Python)            Core API (Java)
  ───────────────────                ─────────────────────────            ───────────────
  records your mic                   turns sound into:                    saves contacts,
  every 4 seconds    ── clip ──►       - text (Whisper)                    settings, alerts
                                       - pitch, loudness, silence
                                     decides: alert or not?  ── alert ──►  sends push
                                                                           notification (ntfy)
                                                                                 │
                                                                                 ▼
                                                                          contact's phone
```

There are 3 parts, and each one runs in its own terminal:

| Part | Folder | Language | Port | Job |
|---|---|---|---|---|
| Frontend | `frontend/` | HTML + JavaScript | 5500 | The pages you click on |
| Analysis service | `analysis-service/` | Python (FastAPI) | 8000 | Listens to the audio and gives it a score |
| Core API | `core-api/` | Java (Spring Boot) | 8080 | Stores data and sends notifications |

---

## How it decides to send an alert

There are two ways an alert can be triggered:

**1. Code word (instant).** You pick a secret phrase in Settings, like "red umbrella". If you say it during a call, the alert goes out right away. Small mistakes like "red umbrela" still count.

**2. Voice change (after about 12 seconds).** First you record 10 seconds of your normal voice (calibration). Then every 4-second clip gets a score from 0 to 100:

| Signal | Points | Why |
|---|---|---|
| Pitch goes up | up to 40 | stressed voices get higher |
| Loudness goes up | up to 40 | shouting or panicking |
| Long pauses while talking | up to 20 | freezing up |

If a clip is mostly silent, you're just listening, so it scores 0.

We **average the last 3 clips** (about 12 seconds). An alert is sent when the average goes above your sensitivity setting: **Low = 80, Medium = 65, High = 50.** Averaging means one cough or loud noise can't set it off on its own.

After any alert there is a **60-second break**, so your contacts don't get spammed.

**If nobody responds:** the first contact gets the alert. If they don't tap "acknowledge" within 2 minutes, the next contact gets it.

**If it was a mistake:** press "False alarm" on the dashboard, or say your cancel phrase (for example "false alarm") during the call. Everyone who got the alert is told it was a false alarm.

---

## Setup

You need:
- **Java 17 or newer** and **Maven**
- **Python 3.10 or newer**
- **Google Chrome**

On a Mac with Homebrew: `brew install openjdk maven python`

### 1. Core API (terminal 1)

```bash
cd core-api
mvn spring-boot:run
```
Wait until it says `Started DistressApplication`.

### 2. Analysis service (terminal 2)

Mac:
```bash
cd analysis-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python3 -m uvicorn main:app --port 8000
```

Windows (PowerShell):
```
cd analysis-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
python -m uvicorn main:app --port 8000
```

The first start downloads the Whisper speech model (about 150MB), so it needs internet once.

### 3. Frontend (terminal 3)

```bash
cd frontend
python3 -m http.server 5500
```

Then open **http://localhost:5500** in Chrome.

> Start the core API **before** the analysis service. The analysis service loads your settings from it when it starts.

---

## Try it (demo steps)

1. **Settings:** add a contact. Click "Generate" to make a topic, then on your phone install the **ntfy** app and subscribe to that topic.
2. **Settings:** add a code word (e.g. `red umbrella`), then click **Record 10 seconds** and talk normally. Save.
3. **Dashboard:** click **Send test alert**. Your phone should get a notification.
4. Open **Call** in two tabs (or on two laptops). Copy the ID from one into the other and press Call.
5. Keep the **Dashboard** open in a third tab so you can watch the scores update every 4 seconds.
6. Say "I left my red umbrella at home." An alert appears on the dashboard and your phone.
7. Or talk loudly in a high voice for about 15 seconds, which triggers a voice-change alert.
8. Say "false alarm" to cancel it.

**Disguise mode:** in Settings, turn on disguise mode and set a duress PIN. The home page now shows a calculator. Type the PIN and press `=` to see a fake "no alerts" screen. Tap its title 3 times to go back. You reach the real app by going straight to `call.html`, `dashboard.html` or `settings.html`.

---

## Test checklist

- [ ] http://localhost:8080/api/health shows `"status":"ok"`
- [ ] http://localhost:8000/health shows `"status":"ok"`
- [ ] Two call tabs can hear each other
- [ ] Dashboard scores move every ~4 seconds during a call
- [ ] Calibration shows your normal pitch in Hz
- [ ] Saying the code word sends an alert right away
- [ ] ~15 seconds of loud, high-pitched talking sends a voice-change alert
- [ ] A single loud moment does **not** send an alert
- [ ] Your phone gets the ntfy notification
- [ ] With 2 contacts, the 2nd gets the alert if the 1st doesn't acknowledge within 2 minutes
- [ ] "False alarm" button and the spoken cancel phrase both cancel the alert
- [ ] Unticking "Allow voice analysis" in Settings stops analysis (call page shows an error)
- [ ] Calculator works normally, and the duress PIN shows the fake dashboard

---

## Files

```
frontend/
  index.html, call.html, dashboard.html, settings.html, disguise.html
  css/styles.css        one stylesheet for all pages
  js/api.js             every request to the two backends
  js/recorder.js        records the mic in 4-second clips
  js/call.js            the voice call (PeerJS)
  js/dashboard.js       live scores + alert history
  js/settings.js        contacts, code words, calibration
  js/disguise.js        fake calculator / notes
  manifest.json, service-worker.js   lets Chrome install it as an app

analysis-service/
  main.py        the API endpoints
  audio.py       decode audio, speech-to-text, pitch/loudness/silence
  scoring.py     the scoring rules explained above
  config.py      settings loaded from the core API

core-api/src/main/java/com/hackathon/distress/
  controller/    the API endpoints (alerts, contacts, config, health)
  service/       AlertService (alert logic + escalation), NtfyService (sends notifications)
  entity/        the 3 database tables: Contact, AppConfig, Alert
  repository/    database access (Spring writes the SQL)
```

---

## Problems?

| Problem | Fix |
|---|---|
| Dashboard says "Could not load alerts" | The core API isn't running (terminal 1) |
| Call page shows "Analysis problem: Failed to fetch" | The analysis service isn't running (terminal 2) |
| "403: Analysis is turned off" | Tick "Allow voice analysis" in Settings and save |
| Microphone doesn't work | Use Chrome and open the page on `localhost`, not your IP address |
| Want to test without the heavy Python libraries | Put `STUB_MODE=true` in `analysis-service/.env` (returns fake scores) |
| Port 5500 is taken | Use another port, and add it to `app.cors.allowed-origins` in `core-api/src/main/resources/application.properties` and `allow_origins` in `analysis-service/main.py` |
| Want to see the database | Go to http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/distressdb`, user `sa`, no password) |

---

## Not included (out of scope)

Login/accounts, training our own AI model, reading real phone calls (iPhone and Android don't allow apps to record calls), a native mobile app, and cloud hosting.
