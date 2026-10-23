# Tierlock

Multi-level permissions for shared phones and tablets. Parents set the rules, kids and teens get their own home screen, and access is earned or requested.

## Prototype

`prototype/index.html` is a self-contained clickable prototype. Open it in a browser. No build step.

Live version: https://yrw2go-ui.github.io/tierlock/ (redeploys automatically when `prototype/` changes on `main`).

It can be installed as an app on phones and tablets: in Safari tap Share, then Add to Home Screen; in Chrome tap the menu, then Install app (or Add to Home screen). `prototype/manifest.webmanifest`, the icons in `prototype/icons/` and `prototype/sw.js` make that work, and the installed app still opens without a connection.

It covers:

- Four access levels (Owner, Manager, Standard, Restricted). Each level manages the levels below it.
- Sign-in with a PIN or face/fingerprint, with a separate home screen per person, including a kids space that opens on its own.
- Phone-style home screens: apps in A to Z order, swipeable pages, and custom wallpaper or photos.
- Chores: a parent sends a task, the device stays locked until it is done (with optional photo proof), and approval unlocks it until a set time or for a number of hours.
- Calls and texts limited to approved contacts, with parent monitoring of calls, texts and pictures kept for 14 days.
- Blocked words for web search, and YouTube/video content levels.
- App requests that go up the chain for approval.
- Velo, an assistant that sends reminders and flags what needs a parent.

## Building the real app

- **Android:** a native app set up as the device's managing app. It acts as the home screen, default phone app and default texting app, so every feature above is possible.
- **iPhone and iPad:** Apple's Screen Time tools handle app blocking, time limits and web filtering. Apple doesn't let any app read texts or call logs or replace the Phone and Messages apps, so call and text monitoring is Android-only.
- **Parent dashboard:** can be a PWA for sending tasks, approving them and reading reports from any device.

## Neon

On the Vercel site, a parent can sign in from the Family account panel. Their family's people, rules, tasks, requests and chore proof photos then save to Neon automatically and load on any device. The `api/` functions handle sign-in (a same-site proxy to Neon Auth) and saving (`api/family.js`); the tables are in `db/schema.sql`. They need `NEON_AUTH_BASE_URL` and `DATABASE_URL` set on Vercel. The GitHub Pages copy has no server, so it stays a demo without the panel. Proof photos go to the private `subphotos` bucket through `api/photos.js`, which needs `NEON_STORAGE_ENDPOINT`, `NEON_STORAGE_ACCESS_KEY_ID` and `NEON_STORAGE_SECRET_ACCESS_KEY` (a Neon credential with storage read and write); photos removed from a chore are deleted from storage on the next save.

`neon.ts` declares the Neon services for the Tierlock project: Postgres, sign-in (Neon Auth), the AI gateway and a private `subphotos` bucket for chore photo proof. Run `npm install`, then `npx neon link` once and `npx neon deploy` to apply it. Pull requests get their own Neon branch automatically (`.github/workflows/neon-pr-branches.yml`).

### Family devices

A signed-in parent taps **Link a family device** to get a one-time 8-character code (valid 10 minutes). The device app sends it to `POST /api/pair` with a device name and gets back its own token; only the token's SHA-256 is stored. With `Authorization: Bearer <token>` the device can:

- `GET /api/device/state?day=YYYY-MM-DD`: people (with PINs and rules), tasks, requests and today's screen time
- `POST /api/device/usage`: `{day, entries: [{member, app, minutes}]}`, cumulative minutes per person and app for that day

Parents see linked devices in the Family account panel and can remove one, which stops its token at once. Everyone starts with the prototype's public demo PINs (1111 to 4444); a parent changes them under **PINs** in the same panel, and devices can't be linked until no one uses a starter PIN. Owners and managers must have a PIN, and once a device is linked the starter PINs can't come back. The prototype shows each person's screen time used today from these reports.

## Android app

`android/` is the Tierlock app for Android phones and tablets (Kotlin, Jetpack Compose). GitHub Actions builds it (`.github/workflows/android.yml`) and every push to `main` publishes the APK as the **android-latest** release: https://github.com/yrw2go-ui/tierlock/releases/tag/android-latest

At setup the device is either:

- **Parent device**: shows the parent controls (the web app) with no locks.
- **Family device**: links to a family with a parent's one-time code, becomes the home screen, and shows "Who's using this?". Each person unlocks with their PIN (or fingerprint/face if their login allows it) and sees only the apps their rules allow; owners and managers see every app. Grown-ups can open device settings from the lock screen with their PIN. The device locks itself when the screen turns off.

Owners and managers always unlock with their PIN; fingerprint or face is offered only to children, because Android can't tell whose finger matched. After 5 wrong PINs the keypad waits 30 seconds, doubling with each further miss up to an hour.

With usage access turned on, the app counts each person's minutes per app and sends them to Neon every 15 minutes, so the web app shows screen time used today.

Not yet: enforcing limits, downtime and chore locks, and blocking apps opened another way. A store release will need its own private signing key.
