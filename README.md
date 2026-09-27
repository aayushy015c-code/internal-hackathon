# Silent Signal

[![CI](https://github.com/aayushy015c-code/internal-hackathon/actions/workflows/ci.yml/badge.svg)](https://github.com/aayushy015c-code/internal-hackathon/actions/workflows/ci.yml) [![CodeQL](https://github.com/aayushy015c-code/internal-hackathon/actions/workflows/codeql.yml/badge.svg)](https://github.com/aayushy015c-code/internal-hackathon/actions/workflows/codeql.yml)

**PSWB03: Non-Verbal Distress Detection via Voice Pattern Analysis**

Silent Signal listens to **your side** of a phone call. If you say a secret code word, or your voice sounds stressed for a while, it quietly sends a push notification to people you trust. The person you're talking to sees nothing.

All the audio processing happens on the laptop. Audio is never saved and never sent to the internet.

Security and privacy: see [SECURITY.md](SECURITY.md) and [PRIVACY.md](PRIVACY.md).

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
| Database | (Docker: `db`) | PostgreSQL (Docker) or H2 file (without Docker) | - | Contacts, settings, alerts, with personal fields encrypted |

---

## How it looks

The interface uses six colours only (a CI check fails if any other colour appears in the frontend):

| Colour | Hex | Used for (60 / 30 / 10 rule) |
|---|---|---|
| Bone | `#E6E0D6` | 60%: page background (light mode) |
| Navy | `#1C2430` | 30%: header, headings, main buttons, main panels |
| Oxblood | `#4B1E23` | 10%: only danger and alerts (pending alert, high stress, hang up, delete) |
| Olive | `#6B6F4E` | safe / on / acknowledged, dividers |
| Bronze | `#8A5A2B` | medium / warning, helper text, focus |
| Ink | `#121212` | text, and the background in dark mode |

It follows your system's light or dark mode, respects "reduce motion", and every text colour passes WCAG AA contrast. Details are at the top of `frontend/css/styles.css`.

## Your identity (call ID)

The first time you open the app, it gives you a **permanent call ID** like `SS-K7P3-9QDM-X2WA`. It stays the same after refreshing, restarting the browser or restarting Docker, because it's stored in the database.

- **Calling:** give your call ID to the people who might call you. They type it on the Call page.
- **Your data is yours:** contacts, code words, calibration, disguise PIN and alerts all belong to your call ID. Another user never sees them.
- **Another browser:** Settings > Your identity shows your secret **access key**. Paste it into "Use an existing identity" in the other browser to get the same call ID and data there. Keep the key private: it works like a password.
- **Data from before this feature** automatically belongs to the first user who opens the app.

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

### Easiest: Docker (one command)

Install Docker Desktop, then in this folder:

```bash
cp .env.example .env
```

Put a password and a key in `.env`: generate them with `openssl rand -hex 24` and `openssl rand -base64 32`. Then:

```bash
docker compose up --build
```

Open **http://localhost:5500**. That's it. Full beginner guide: **[DOCKER.md](DOCKER.md)**.

To make the "Acknowledge" button work on contacts' phones, start with the tunnel. See **[TUNNEL.md](TUNNEL.md)**.

```bash
docker compose --profile tunnel up --build
```

### Without Docker (run each part yourself)

You need:
- **Java 17 or newer** and **Maven**
- **Python 3.12 or newer**
- **Google Chrome**

On a Mac with Homebrew: `brew install openjdk maven python`

### 1. Core API (terminal 1)

First time only: create `core-api/.env` with the database passwords and the encryption key.

```bash
cd core-api
cp .env.example .env
```

Open `core-api/.env`:
- Replace both `change-me` passwords with long random text (no spaces).
- Set `DATA_ENCRYPTION_KEY` to the output of `openssl rand -base64 32`.

Keep a copy somewhere safe: if you lose them, the saved data can't be opened. `.env` is never uploaded to GitHub.

Then start it:
```bash
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
python3 -m http.server 5500 --bind 127.0.0.1
```
(`--bind 127.0.0.1` means only this computer can open it, not others on your Wi-Fi.)

Then open **http://localhost:5500** in Chrome.

> Start the core API **before** the analysis service. The analysis service loads your settings from it when it starts.

---

## Try it (demo steps)

0. The first time, you'll see the **privacy notice**. Tick "I agree" to continue. Nothing is analyzed until you do.
1. **Settings:** add a contact. Click "Generate" to make a topic, then on your phone install the **ntfy** app and subscribe to that topic.
2. **Settings:** add a code word (e.g. `red umbrella`), then click **Record 10 seconds** and talk normally. Save.
3. **Dashboard:** click **Send test alert**. Your phone should get a notification.
4. Open **Call** in two tabs (or on two laptops). Copy the ID from one into the other and press Call.
5. Keep the **Dashboard** open in a third tab so you can watch the scores update every 4 seconds.
6. Say "I left my red umbrella at home." An alert appears on the dashboard and your phone.
7. Or talk loudly in a high voice for about 15 seconds, which triggers a voice-change alert.
8. Say "false alarm" to cancel it.

**Disguise mode:** in Settings, turn on disguise mode and set a duress PIN (4 to 8 digits). The home page now shows a calculator. Type the PIN and press `=` to see a fake "no alerts" screen. Tap its title 3 times to go back. You reach the real app by going straight to `call.html`, `dashboard.html` or `settings.html`.

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
- [ ] Settings → Download my data gives a JSON file; Delete everything clears it all

### Automatic tests

```bash
cd core-api && mvn test                                   # Java: 18 tests
cd analysis-service && pip install -r requirements-dev.txt && python -m pytest tests   # Python: 26 tests
bash scripts/smoke_test.sh                                 # whole app + security checks (needs the jar built)
MODE=docker bash scripts/smoke_test.sh                     # same checks against a fresh Docker stack (STUB_MODE=true)
```

These also run on GitHub for every push (see `.github/workflows/`):

| Workflow | What it does |
|---|---|
| **CI** | Java + Python tests, frontend checks, end-to-end smoke test, Docker + PostgreSQL stack test, image vulnerability scan (Trivy), dependency scan, secret scan |
| **CodeQL** | GitHub's security scanner, on every push and weekly |
| **Release** | Push a tag like `v1.0.0`: publishes Docker images to ghcr.io and a GitHub Release with the files |
| **Dependabot** | Opens a pull request when a library needs an update |

---

## Files

```
frontend/
  index.html, call.html, dashboard.html, settings.html, disguise.html
  consent.html          privacy notice, must be accepted first
  css/styles.css        one stylesheet for all pages
  js/home.js, js/consent.js, js/consent-check.js
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
  tests/         pytest tests

core-api/src/main/java/com/hackathon/distress/
  controller/    the API endpoints (alerts, contacts, config, disguise, your data, health)
  service/       AlertService (alert logic, escalation, auto-delete), NtfyService, PinService (hashes the PIN)
  config/        CORS, LocalOnlyFilter (local-only access, security headers), FieldEncryptor (encrypts personal columns)
  entity/        the 3 database tables: Contact, AppConfig, Alert
  repository/    database access (Spring writes the SQL)
core-api/src/test/   Java tests

docker-compose.yml      runs everything in Docker (+ PostgreSQL, + optional tunnel)
*/Dockerfile            how each part's Docker image is built
frontend/nginx.conf     web server + security headers (Docker)
scripts/smoke_test.sh   starts everything and tests it end to end
.github/                CI/CD workflows and Dependabot
DOCKER.md, TUNNEL.md, SECURITY.md, PRIVACY.md, LICENSE
```

---

## Problems?

| Problem | Fix |
|---|---|
| Dashboard says "Could not load alerts" | The core API isn't running (terminal 1) |
| Call page shows "Analysis problem: Failed to fetch" | The analysis service isn't running (terminal 2) |
| "403: Analysis is turned off" | Tick "Allow voice analysis" in Settings and save |
| "403: Please accept the privacy notice" | Open `consent.html` and agree |
| Core API won't start: "Wrong user name or password" or "Could not resolve placeholder DB_FILE_PASSWORD" | `core-api/.env` is missing or has different passwords from when the database was created. For a fresh start, delete `core-api/data/` |
| Contacts can't open the "Acknowledge" link | It needs a tunnel, see [TUNNEL.md](TUNNEL.md) |
| Anything Docker | See the table at the end of [DOCKER.md](DOCKER.md) |
| Microphone doesn't work | Use Chrome and open the page on `localhost`, not your IP address |
| Want to test without the heavy Python libraries | Put `STUB_MODE=true` in `analysis-service/.env` (returns fake scores) |
| Port 5500 is taken | Use another port, and add it to `app.cors.allowed-origins` in `core-api/src/main/resources/application.properties` and `allow_origins` in `analysis-service/main.py` |
| Want to see the database | Set `spring.h2.console.enabled=true`, restart, open http://localhost:8080/h2-console. JDBC URL `jdbc:h2:file:./data/distressdb;CIPHER=AES`, user `sa`, password `<DB_FILE_PASSWORD> <DB_PASSWORD>` (with a space). Turn it off again after |

---

## Not included (out of scope)

Login/accounts, training our own AI model, reading real phone calls (iPhone and Android don't allow apps to record calls), a native mobile app, and cloud hosting.
