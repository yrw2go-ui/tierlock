# Tierlock

Multi-level permissions for shared phones and tablets. Parents set the rules, kids and teens get their own home screen, and access is earned or requested.

## Prototype

`prototype/index.html` is a self-contained clickable prototype. Open it in a browser. No build step.

Live version: https://yrw2go-ui.github.io/tierlock/ (redeploys automatically when `prototype/` changes on `main`).

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

On the Vercel site, a parent can sign in from the Family account panel. Their family's people, rules, tasks and requests then save to Neon automatically and load on any device. The `api/` functions handle sign-in (a same-site proxy to Neon Auth) and saving (`api/family.js`); the tables are in `db/schema.sql`. They need `NEON_AUTH_BASE_URL` and `DATABASE_URL` set on Vercel. The GitHub Pages copy has no server, so it stays a demo without the panel. Chore proof photos aren't saved yet.

`neon.ts` declares the Neon services for the Tierlock project: Postgres, sign-in (Neon Auth), the AI gateway and a private `subphotos` bucket for chore photo proof. Run `npm install`, then `npx neon link` once and `npx neon deploy` to apply it. Pull requests get their own Neon branch automatically (`.github/workflows/neon-pr-branches.yml`).
