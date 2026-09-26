# Running Silent Signal with Docker

Docker is the easiest way to run the app. You don't install Java, Maven, Python or a database. Docker downloads and runs everything for you.

## What is Docker? (30-second version)

- An **image** is a packaged app with everything it needs, like a zip file that includes its own Java or Python.
- A **container** is a running copy of an image, like a small separate computer inside your laptop.
- **Docker Compose** starts several containers together from one file, `docker-compose.yml`.

Our app runs as 4 containers (5 with the tunnel):

```
                        your computer (only 127.0.0.1 can connect)
   ┌────────────────────────────────────────────────────────────────────┐
   │  Chrome ──► frontend :5500     core-api :8080 ◄── analysis :8000   │
   │                                     │                              │
   │                                     ▼   private network,           │
   │                                  db (PostgreSQL)  no internet      │
   │                                                                    │
   │  tunnel (optional) ──► core-api  (only the "Acknowledge" link)     │
   └────────────────────────────────────────────────────────────────────┘
```

| Container | What it is | Address |
|---|---|---|
| `frontend` | The web pages (nginx web server) | http://localhost:5500 ← **open this** |
| `core-api` | Java API: contacts, settings, alerts | http://localhost:8080 |
| `analysis-service` | Python: speech-to-text + stress score | http://localhost:8000 |
| `db` | PostgreSQL database | not reachable from outside (on purpose) |
| `tunnel` | Optional public link for the ack button | see [TUNNEL.md](TUNNEL.md) |

## 1. Install Docker (once)

- **Mac / Windows:** install **Docker Desktop** from https://www.docker.com/products/docker-desktop and open it. Wait for the whale icon to say "running".
- **Linux:** install Docker Engine and the Compose plugin (https://docs.docker.com/engine/install/).

Check it works:
```bash
docker --version
docker compose version
```

## 2. Create your secrets file (once)

In the `silent-signal` folder:

```bash
cp .env.example .env
```

Open `.env` and replace the two `REPLACE_ME` values. You can generate them with:

```bash
openssl rand -hex 24
```
Use that for `POSTGRES_PASSWORD`.

```bash
openssl rand -base64 32
```
Use that for `DATA_ENCRYPTION_KEY`.

> Keep a copy of `.env` somewhere safe (not GitHub). If you lose `DATA_ENCRYPTION_KEY`, saved data can't be read any more. `.env` is in `.gitignore`, so it's never uploaded.

## 3. Start the app

```bash
docker compose up --build
```

- The first time takes a few minutes (it downloads Java, Python, PostgreSQL and the ~150MB speech model).
- Wait until you see `Application startup complete`.
- Open **http://localhost:5500** in Chrome.

To run it in the background instead (you get your terminal back), add `-d`:
```bash
docker compose up --build -d
```

To stop it, press **Ctrl + C**, or if you used `-d`:
```bash
docker compose down
```
Your data is kept for next time.

## Everyday commands

| I want to... | Command |
|---|---|
| Start everything | `docker compose up --build -d` |
| Start with the public ack link | `docker compose --profile tunnel up --build -d` |
| See what's running (and if it's healthy) | `docker compose ps` |
| See the logs of everything | `docker compose logs -f` |
| See logs of one part | `docker compose logs -f core-api` |
| Restart one part | `docker compose restart analysis-service` |
| Stop everything (keep data) | `docker compose down` |
| Stop and **delete all data** | `docker compose down -v` |
| Rebuild after changing code | `docker compose up --build -d` |
| Open the database (read-only look) | `docker compose exec db psql -U silentsignal -d silentsignal` |
| Quick test without the speech model | `STUB_MODE=true docker compose up --build -d` |
| Run the automatic checks | `MODE=docker bash scripts/smoke_test.sh` (on a fresh stub-mode stack) |

Inside `psql`: `\dt` lists tables, `select * from contacts;` shows rows (names are encrypted, which is expected), and `\q` quits.

## Why it's safe

- Ports are published on **127.0.0.1 only**, so other devices on the Wi-Fi can't connect.
- The database has **no published port** and sits on an **internal network with no internet**. Only `core-api` can talk to it.
- Every container except the database runs as a **normal user, not root**, with extra permissions removed (`cap_drop: ALL`, `no-new-privileges`).
- Personal fields are **encrypted by the app before they reach PostgreSQL** (AES-256-GCM). Anyone who opens the database sees scrambled text.
- Secrets come from `.env` and are **not baked into the images** (`.dockerignore`).
- Image versions are pinned. GitHub scans the images with Trivy, and Dependabot suggests updates.

## Problems?

| Problem | Fix |
|---|---|
| `Set POSTGRES_PASSWORD in .env` | You skipped step 2 |
| `DATA_ENCRYPTION_KEY must be 32 random bytes` | Generate the key with `openssl rand -base64 32` and paste the whole line |
| `port is already allocated` | Something else uses 8080/8000/5500. Stop it (maybe the non-Docker version is still running) |
| `analysis-service` stays "starting" a long time | The first start downloads the speech model. Watch `docker compose logs -f analysis-service` |
| `Could not decrypt a database value` | `.env` has a different `DATA_ENCRYPTION_KEY` than when the data was saved. Put the old key back, or start fresh with `docker compose down -v` |
| Changed PostgreSQL password and now it can't log in | The password is set when the database is first created. Start fresh with `docker compose down -v` |
| Docker Desktop says "not running" | Open Docker Desktop and wait for it to start |

## Using the published images (after a release)

When the team pushes a version tag (for example `v1.0.0`), GitHub Actions publishes the images to
`ghcr.io/aayushy015c-code/internal-hackathon/<core-api|analysis-service|frontend>`.
You can see them on the repo page under **Packages**.
