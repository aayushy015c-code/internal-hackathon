# How to explain Silent Signal

Notes for presenting to judges or a professor.

## The 30-second version

> Sometimes a person in danger can't say "help" out loud because someone is listening. Silent Signal runs during a phone call and listens to **your** voice. If you say a secret code word, like "red umbrella", or your voice stays stressed for about 12 seconds, it quietly sends a push notification to your trusted contacts, with your location. Nothing appears on your screen, so the person next to you doesn't know. All the audio processing happens on your own laptop, and audio is never saved.

## The 2-minute version (follow the diagram)

1. **The call.** Two browsers call each other using WebRTC (we used the PeerJS library).
2. **Recording.** Every 4 seconds the browser cuts a clip of *your* microphone, never the other person's, and sends it to our Python service.
3. **Understanding the clip.** The Python service does two things:
   - turns speech into text with **Whisper** (an open-source speech-to-text model that runs on the laptop)
   - measures **pitch**, **loudness** and **silence** with **librosa** (a sound analysis library)
4. **Deciding.** There are two ways to trigger an alert:
   - **Code word:** instant.
   - **Voice change:** we compare against your normal voice (recorded during calibration), give each clip a 0-100 score, and alert if the average of the last 3 clips is too high.
5. **Alerting.** The Java service saves the alert and sends a notification through **ntfy**. If the first contact doesn't respond in 2 minutes, it goes to the next contact.

## Why we built it this way

| Choice | Reason |
|---|---|
| Two backends (Python + Java) | Python has the best audio/AI libraries. Java/Spring Boot is what our team knows for APIs and databases. |
| 4-second clips | Short enough to react fast, long enough for Whisper to understand a phrase |
| Average of 3 clips | One cough or a door slam shouldn't cause an alert, only stress that lasts |
| Compare to *your* normal voice | Everyone's voice is different. A high voice isn't stress if it's your normal voice. |
| Only count voice going **up** | Being quiet while you listen is normal, not stress |
| Code word skips the average | If you say the code word, you meant it. Waiting 12 seconds would be dangerous. |
| 60-second break after an alert | Stops contacts getting 15 notifications about the same thing |
| ntfy for notifications | Free, no sign-up, works on any phone |
| Plain HTML/JS, no React | Simple, no build step, and the whole team can read it |

## Questions you might get

**Does it record my calls?**
No. Each 4-second clip is processed in memory and thrown away. We only save the last ~10 words of text around an alert, so your contact knows what happened.

**Does it send my voice to the cloud?**
No. Whisper and librosa both run on the laptop. The only thing that goes out to the internet is the notification text.

**Can it work on a real phone call?**
Not on normal cellular calls, because iPhone and Android block apps from accessing call audio. That's why we built our own browser call. It can be installed as an app from Chrome.

**What about false alarms?**
Three protections: the 12-second average, sensitivity settings (Low/Medium/High), and a "false alarm" button or spoken cancel phrase that tells contacts to ignore it.

**What if someone grabs the phone?**
Disguise mode makes the app open as a working calculator. If someone forces you to "unlock" it, you type the duress PIN, and they see a fake empty dashboard.

**Is this AI?**
Partly. Whisper, the speech-to-text part, is an AI model. The stress detection is simple rules on pitch, loudness and silence, not a trained model. That makes it easy to explain why an alert fired: every alert saves a plain-English reason, like "pitch +40%, loudness +90%".

**Is it secure?**
- Everything only listens on the laptop itself (127.0.0.1), so nobody on the Wi-Fi can reach it, and other websites are blocked.
- The database is encrypted (AES). The duress PIN is stored as a hash and checked on the server.
- The acknowledge link uses a random secret token.
- Every page has a Content-Security-Policy.
- GitHub checks every push: tests, CodeQL, secret scanning and vulnerable-library scanning.

Details and known limitations are in SECURITY.md.

**Does it follow the DPDP Act?**
It's built with it in mind: a consent screen before any analysis, very little data kept, local processing, data deleted after 30 days, and "download my data", "delete everything" and "withdraw consent" buttons. See PRIVACY.md. Since it's a prototype, it would still need a legal review before real users.

**What would you improve next?**
Better stress detection with more signals (like breathing), and a one-tap silent alert from an iPhone shortcut or smartwatch.

## Live demo order (about 3 minutes)

1. Show **Settings**: contact, code word, calibration (record 10 seconds live).
2. **Send test alert** and show the notification on your phone.
3. Start a call between two tabs, with the **Dashboard** open next to it.
4. Talk normally and show the score staying low.
5. Say the code word. The alert shows up and your phone buzzes.
6. Say "false alarm". The alert is cancelled.
7. Show the **calculator** disguise and the duress PIN.
