# Privacy notice

Written with India's **Digital Personal Data Protection Act 2023** and **DPDP Rules 2025**
in mind. This is a student prototype, not legal advice. If you run it for other people,
get it reviewed.

The same notice is shown in the app (`consent.html`) and must be accepted before any
audio is analyzed.

## Who is responsible
The person running Silent Signal on their own computer. If a team runs it for other
users, that team is the "Data Fiduciary" and must add a contact person below.

**Grievance / contact:** _add your name and email here before sharing the app with anyone_

## What we process and why

| Data | Why | Where it goes | How long |
|---|---|---|---|
| Microphone audio (4s at a time) | Detect code words and stress | This computer only, in memory | Seconds. Never saved |
| Words you say (text) | Code word matching | In memory; last ~10 words saved with an alert | Memory: until the call ends (max 10 min idle). Alert: 30 days |
| Voice pitch / loudness / pauses | Compare with your normal voice | In memory; your normal pitch/loudness saved in settings | Until you delete it |
| Location (optional) | So contacts can find you | Saved with the alert; sent to contacts via ntfy.sh | 30 days (ntfy.sh keeps ~12 hours) |
| Contacts' names and ntfy topics | Send alerts | Encrypted database on this computer | Until you delete them |
| Settings, duress PIN (hashed) | App settings | Encrypted database on this computer | Until you delete them |

**Legal basis:** your consent (Section 6). Sending an alert in an emergency can also fall under
"legitimate uses" (Section 7) for threats to life or safety.

## Other people
- **Your contacts** are data principals too. Tell them you added them and what they'll receive.
- **The other person on the call:** only your microphone is analyzed, but a speakerphone can
  pick up their voice. Consider telling them.
- **Children:** users under 18 need verifiable consent from a parent or guardian (Section 9).

## Your rights (and where to use them)
| Right | In the app |
|---|---|
| Know what's stored / get a copy | Settings → **Download my data** |
| Correct it | Settings (edit and save) |
| Erase it | Settings → **Delete everything** |
| Withdraw consent | Settings → **Withdraw consent** (analysis stops immediately) |
| Complain | Grievance contact above, then the Data Protection Board of India |

## Security safeguards (Rule 6)
Encryption at rest, local-only access, hashed PIN, audit log of important actions
(consent, settings changes, alerts, deletions; no personal content in the log), automatic
deletion. Details in [SECURITY.md](SECURITY.md).

## If there's a data breach (Rule 7)
1. Stop the services and disconnect the laptop from the network.
2. Tell affected people **without delay**: what happened, what data, what they should do.
3. If you run this for others: report to the Data Protection Board without delay, and a full
   report within 72 hours. If you're a service provider under CERT-In's 2022 directions,
   report to CERT-In within 6 hours.
4. Change the database passwords in `core-api/.env` and the ntfy topics.

## Third parties
- **ntfy.sh** (push notifications): receives alert text and location over HTTPS.
- **PeerJS cloud server** (connects the call): sees peer IDs and IP addresses. Call audio itself is encrypted end-to-end by WebRTC.
- **jsDelivr CDN**: serves the PeerJS library (checked with an integrity hash).
- **Hugging Face**: the speech model is downloaded once. No user data is sent.
