# Making the "Acknowledge" link work on contacts' phones

## The problem

When an alert goes out, your contact gets a notification with an **Acknowledge** button. Tapping it tells the app "I saw it, don't escalate to the next person."

The app runs on **your laptop**, but the contact taps the button on **their phone**, somewhere else on the internet. A link like `http://localhost:8080/...` means "this device" to their phone, so it can't reach your laptop.

## The fix: a tunnel

A **tunnel** gives your laptop a temporary public `https://` address. We use **Cloudflare's free quick tunnel**: no account, no sign-up.

```
contact's phone ──https──► something.trycloudflare.com ──► tunnel on your laptop ──► core-api
```

**Is that safe?** Through the tunnel, **only** the link `/api/alerts/ack/<secret token>` works. Everything else (settings, contacts, alerts, delete) returns **403 Forbidden**; we tested this over the real internet. The token is 32 random hex characters, so it can't be guessed.

---

## Option A: with Docker (easiest, automatic)

```bash
docker compose --profile tunnel up --build -d
```

That's it. The `tunnel` container starts, and the core API **asks it for its address automatically** before each alert. You don't copy or paste anything.

Check that it works:

```bash
docker compose logs tunnel | grep trycloudflare
```
This shows your public address.

1. In the app, go to **Dashboard** and click **Send test alert**.
2. On your phone, open the ntfy app. The notification's **Acknowledge** button should open a page saying "Thanks, the alert is acknowledged."
3. On the Dashboard, the alert changes to **ACKNOWLEDGED**.

Turn it off when you don't need it:

```bash
docker compose --profile tunnel stop tunnel
```

## Option B: without Docker

1. Install cloudflared once:
   - Mac: `brew install cloudflared`
   - Windows: `winget install --id Cloudflare.cloudflared`
   - Linux: see https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/
2. Start the core API as usual, then in a **new terminal**:
   ```bash
   cloudflared tunnel --url http://localhost:8080 --metrics 127.0.0.1:2000
   ```
3. Tell the core API where to ask for the address. Add this line to `core-api/.env` and restart the core API:
   ```
   TUNNEL_METRICS_URL=http://127.0.0.1:2000/quicktunnel
   ```
   (Or copy the `https://....trycloudflare.com` address it prints into `core-api/.env` as `APP_BASE_URL=https://....trycloudflare.com`. You'd have to update that every time, because the address changes each time the tunnel restarts.)

## Good to know

- **The address changes** every time the tunnel restarts. That's fine: new alerts use the new address automatically (Option A, or Option B with `TUNNEL_METRICS_URL`). Old notifications' links stop working.
- **Quick tunnels are for testing and demos.** Cloudflare doesn't guarantee uptime. For real use:
  - a **named Cloudflare Tunnel** with your own domain (free Cloudflare account), or
  - an **ngrok** free static domain.
  
  Put the fixed URL in `APP_BASE_URL`.
- **If the tunnel isn't running**, everything still works. Notifications are still sent, the link just won't open on the phone, and the alert escalates to the next contact after 2 minutes, which is the safe default.
- The tunnel only forwards to the **core API**. The frontend and analysis service are never exposed.
