# Security

## Reporting a problem

If you find a security problem, please **don't open a public issue**. Use GitHub's
"Report a vulnerability" button (Security tab → Advisories), or email the team.
We'll reply within 7 days.

## How the app is protected

| Risk | What we did | Where |
|---|---|---|
| Someone on the same Wi-Fi reads or changes your settings | Every server only listens on `127.0.0.1` (this computer) | `application.properties`, README run commands |
| A website you visit sends requests to the app | Core API: CORS allow-list. Analysis service: blocks any request whose `Origin` isn't our frontend | `WebConfig.java`, `main.py` |
| DNS rebinding (a website pretends to be `localhost`) | Both servers reject unknown `Host` names | `LocalOnlyFilter.java`, `TrustedHostMiddleware` |
| Someone guesses the "Acknowledge" link | Link uses a random 128-bit token, not the alert number. Only its SHA-256 hash is used for lookups | `AlertService.java` |
| Stolen laptop / copied database / DB admin reads it | Personal columns (names, topics, code words, voice baseline, reasons, transcript, location) are encrypted by the app with AES-256-GCM before saving. Without Docker, the whole H2 file is also AES-encrypted | `FieldEncryptor.java`, `EncryptedDouble.java` |
| Database reachable by attackers (Docker) | PostgreSQL has no published port and sits on an internal network with no internet access; only core-api can reach it | `docker-compose.yml` |
| Container escape / privilege abuse | Containers run as non-root users with `cap_drop: ALL` and `no-new-privileges` | Dockerfiles, `docker-compose.yml` |
| Duress PIN found by an attacker | Stored only as a salted PBKDF2 hash; checked on the server, never sent to the browser | `PinService.java`, `DisguiseController.java` |
| Injected script (XSS) | Server text is escaped; Content-Security-Policy on every page blocks inline/unknown scripts | `api.js` `escapeHtml`, `<meta>` CSP |
| CDN serves a modified PeerJS | Subresource Integrity hash on the script tag | `call.html` |
| Bad or huge input | Validation on every field; uploads capped at 2MB (read with a limit) | `@Valid`, `ConfigUpdate.java`, `main.py` |
| Old personal data piling up | Alerts auto-deleted after 30 days; call text forgotten when the call ends or after 10 minutes idle | `AlertService.deleteOldAlerts`, `scoring.py` |
| Leaked secret in the code | `.env` files are git-ignored; CI runs gitleaks on every push | `.gitignore`, `ci.yml` |
| Vulnerable library / base image | Exact versions pinned; `pip-audit` and Trivy image scans in CI; Dependabot PRs (Maven, pip, Docker, Actions); CodeQL weekly | `requirements.txt`, `.github/` |
| Compromised GitHub Action | Third-party actions pinned to an exact commit SHA, not a movable tag | `.github/workflows/` |
| Clickjacking / sniffing | `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `no-store` | `LocalOnlyFilter.java`, `main.py` |

## Encryption

| Data | Protected? |
|---|---|
| Call audio between the two browsers | Yes, WebRTC always encrypts (DTLS-SRTP) |
| Browser ↔ our servers | Plain HTTP, but only over `127.0.0.1`, it never touches the network |
| Alert to contacts (via ntfy.sh) | HTTPS in transit. ntfy.sh can read the message (not end-to-end) |
| Personal columns in the database | AES-256-GCM, random IV per value (key in `.env`, never in the database) |
| Whole database file (without Docker) | Also AES (H2 `CIPHER=AES`) |
| PostgreSQL (Docker) | Personal columns encrypted as above. The Docker volume itself relies on your disk encryption (FileVault / BitLocker) |
| Ack link through the tunnel | HTTPS (Cloudflare) |
| Duress PIN | PBKDF2-SHA256, 100,000 rounds, random salt |
| Audio | Never stored |

## Known limitations (risk register)

| # | Risk | Likelihood | Impact | Why it's accepted / what to do |
|---|---|---|---|---|
| 1 | No login. Anyone using this computer's browser can open Settings | Medium | High | One-user, one-laptop prototype. Use an OS account password + FileVault/BitLocker |
| 2 | ntfy.sh can read alert text and location | Low | Medium | Self-host ntfy or use ntfy access tokens (`NTFY_TOKEN`); turn off location in Settings |
| 3 | PeerJS's public server sees your IP | Medium | Low | Run your own PeerJS server for real use |
| 4 | A 4-8 digit PIN can be brute-forced by someone on the laptop | Low | Medium | Only works locally; use 8 digits |
| 5 | The ack link only works on contacts' phones if you run a tunnel | High | Medium | See [TUNNEL.md](TUNNEL.md). Only the ack link is reachable through a tunnel; without it, escalation still happens |
| 6 | If you lose `.env` (encryption key), saved data can't be read | Low | Medium | Keep a copy somewhere safe (not on GitHub) |
| 8 | Quick tunnels have no uptime guarantee; Cloudflare sees ack requests | Medium | Low | Use a named tunnel or your own domain for real use |
| 7 | Speech-to-text or stress detection can be wrong | Medium | High | Code word path, sensitivity setting, false-alarm cancel, escalation |

## Acknowledge link on phones

See **[TUNNEL.md](TUNNEL.md)**. In short: `docker compose --profile tunnel up`. Through the tunnel
**only** `/api/alerts/ack/<token>` works; every other path returns 403 (`LocalOnlyFilter.java`).
Tested over the real internet.

## Checks that run automatically (GitHub Actions)

- Java tests (JDK 17 and 21), Python tests (3.12 and 3.13)
- Full Docker stack with PostgreSQL + smoke test, and Trivy scans of the images
- End-to-end smoke test, including the attacks above (`scripts/smoke_test.sh`)
- `pip-audit` (known vulnerable Python packages)
- gitleaks (secrets in the code or history)
- CodeQL (security bugs in Java, Python, JavaScript), also weekly
- Dependabot (library updates)
