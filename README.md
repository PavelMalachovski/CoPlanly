# 📅 CoPlanly

<div align="center">

**A modern Android app that helps separated parents coordinate childcare — together.**

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## Overview

**CoPlanly** is a shared-calendar app for divorced or separately living parents. Both parents
see one custody calendar, coordinate events around the child, confirm pickups, track expenses,
chat, and keep medical/school records in one place — with offline-first local storage and
Firebase sync between the two households.

### Key features

- 🗓 **Custody calendar** — Month / Week / Day views, custody model setup (including %-based
  splits), pink/blue color coding for Mom/Dad days
- 👥 **Parent views** — switch between "Mom", "Dad" and the mutual "Both" view
- 🏷 **Event types & filters** — default types (general, medical, school, sports, birthday),
  user-defined types, and per-type visibility filters (e.g. hide school events in July)
- 🔁 **Recurring events** — daily / weekly / biweekly / monthly with an optional end date
- ✅ **Pickup confirmation** — one parent confirms the pickup, the other side sees it
- ⏰ **Reminders** — local notifications 30 min or 1 h before an event
- 🇨🇿 **Czech holidays** — public holidays (computed for any year) and national school
  vacations shown directly in the calendar, with a toggle
- 🔒 **Private events** — events only you can see; they never leave your device
- 🔄 **Sync** — two-way Google Calendar sync + Firestore sync between parents
- 💬 **Chat**, 💰 **expense tracking**, 🏥 **medical records**, 🎓 **school module**,
  🤖 **AI assistance** (Gemini): natural-language event entry, conflict monitoring, suggestions
- 🌍 **Localization** — English, Czech, Russian; light & dark themes

---

## Tech stack

| Category | Technology |
|-----------|------------|
| Language | Kotlin 1.9+ |
| UI | Jetpack Compose, Material 3 (expressive theme, Poppins) |
| Architecture | Clean Architecture (domain / data / presentation), MVVM |
| DI | Hilt |
| Local DB | Room (schema v9, exported schemas in `app/schemas`) |
| Background work | WorkManager (+ HiltWorkerFactory) |
| Backend | Firebase: Auth, Firestore, Cloud Messaging, Crashlytics, Analytics, Remote Config |
| Cloud Functions | `functions/` (Node.js) — notification fan-out |
| APIs | Google Calendar API, Gemini (generative AI) |
| Min / target SDK | 26 / 34 |

### Project structure

```
app/src/main/java/com/coparently/app/
├── data/
│   ├── local/            # Room database, DAOs, entities, migrations, encrypted prefs
│   ├── remote/           # Firebase (auth, Firestore, FCM, pairing), Google, AI clients
│   ├── repository/       # Repository implementations
│   ├── notification/     # FCM setup + event reminder scheduling (WorkManager)
│   └── sync/             # Google Calendar & Firestore sync, conflict resolution
├── domain/
│   ├── model/            # Domain entities (Event, User, CustodyModel, …)
│   ├── holidays/         # Czech public holidays & school vacations provider
│   ├── notification/     # ReminderScheduler abstraction
│   ├── repository/       # Repository interfaces
│   └── usecase/          # Business logic (event CRUD, recurrence expansion, AI, …)
├── presentation/
│   ├── calendar/         # Calendar screens, views, filters, custody helpers
│   ├── event/            # Event list + add/edit screens
│   ├── chat/ expenses/ childinfo/ custody/ pairing/ settings/ ai/ auth/ sync/
│   └── theme/            # Material 3 theme: colors, typography, shapes, dimensions
└── di/                   # Hilt modules
```

---

## Getting started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- A Firebase project with `google-services.json`

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/CoPlanly.git
   cd CoPlanly
   ```

2. **Configure Firebase / Google services**
   - Create a project in the [Firebase Console](https://console.firebase.google.com)
   - Download `google-services.json` into `app/`
   - Follow [docs/google-oauth-setup.md](docs/google-oauth-setup.md) for Google Calendar OAuth

3. **(Optional) Gemini AI** — put `GEMINI_API_KEY=…` into `gradle.properties`
   or export it as an environment variable.

4. **Build & run**
   ```bash
   ./gradlew assembleDebug
   ```
   The APK lands in `app/build/outputs/apk/debug/`.

### Tests & quality

```bash
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator)
./gradlew lint detekt            # static analysis
```

---

## Documentation

| Document | Purpose |
|---|---|
| [Roadmap (MVP phases)](docs/CoPlanly/MVP_phases.md) | **Authoritative roadmap** — MVP 1 done, MVP 2 next |
| [Project audit (July 2026)](docs/AUDIT-2026-07.md) | Full architecture/security/quality audit |
| [CLAUDE.md](CLAUDE.md) | Guide for AI-assisted development in this repo |
| [Firebase setup](docs/firebase-setup.md) · [quick start](docs/firebase-quick-start.md) | Backend configuration |
| [Google OAuth setup](docs/google-oauth-setup.md) | Calendar API auth |
| [Data structure](docs/data-structure.md) | Firestore/Room data model |
| [Deployment guide](docs/deployment-guide.md) | Release process |
| [Contributing](docs/CONTRIBUTING.md) | How to contribute |
| `docs/archive/` | Historical one-off fix notes and stage reports |

---

## Roadmap status

**✅ MVP 1 — Foundation & Core Calendar (complete)**
Custody model setup, Month→Week→Day views, Mom/Both/Dad view switch, custom event types
with filters, recurrence, pickup confirmation, event reminders, Czech holidays & school
vacations, private events, weekend colors.

**🚧 MVP 2 — Communication, Receipts & Dashboards (next)**
Receipts, change requests on events, weekly summary dashboard, first-screen feed of the
last 5 changes, structured chat change-requests, image attachments.

**🎨 UX/UI overhaul — agreed July 2026 (in progress)**
Based on a full design review of the live app (see below), the next UI iteration includes:

- **Bottom navigation bar** (Calendar / Chat / Expenses / Settings) — today the chat,
  expense and budget screens exist but have no entry point in the UI
- **Toolchain upgrade**: compile/target SDK 34 → 36, Compose BOM 2024.11 → 2025.x,
  Material 3 1.4+ (M3 Expressive), predictive back
- **Calendar core**: `HorizontalPager`-based swipes with follow-the-finger physics,
  classic month grid starting at the 1st (horizontal month paging), readable event chips
  in the week view, visible Mom/Dad custody coloring in the month grid, subtler
  holiday/vacation markers
- **Event interactions**: tap opens a preview bottom sheet (details + Edit/Delete),
  sticky Save button in the event form, contextual notification-permission request
  (instead of the on-every-launch system dialog)
- **Fixes surfaced by the review**: missing Room migration path from very old installs
  (v3 → v9 crash), pink "Mom" accent leaking into neutral selected chips, hardcoded
  strings in chat/expense screens, sign-out button placement

**🔮 MVP 3 — Automation & Integrations**
Bakaláři/Edupage import, payments, CSV/PDF exports, intelligent suggestions,
minute-precision drag resizing.

See [MVP_phases.md](docs/CoPlanly/MVP_phases.md) for the full feature matrix.

---

## Contributing

1. Fork and create a feature branch (`feature/amazing-feature`)
2. Follow the project rules (see [CLAUDE.md](CLAUDE.md) / [.cursorrules](.cursorrules)):
   Compose-only UI, stateless components, Hilt DI, KDoc comments, tests for repositories
3. Use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, …)
4. Open a Pull Request

---

## License

MIT — see [LICENSE](LICENSE).

<div align="center">

**Made with ❤️ for parents and kids**

</div>
