<div align="center">

# Aulaa Keyboard

**An open-source Android keyboard that lets merchants create and send an Aulaa
payment link without ever leaving the chat.**

Think "myBCA keyboard", but for [Aulaa](https://aulaa.co) — and with the clean,
neutral look of the Aulaa merchant dashboard.

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Fork of FlorisBoard](https://img.shields.io/badge/fork-FlorisBoard-informational.svg)](https://github.com/florisboard/florisboard)
[![minSdk](https://img.shields.io/badge/minSdk-26%20(Android%208)-brightgreen.svg)](#build--run)
[![Status](https://img.shields.io/badge/status-alpha%20scaffold-orange.svg)](#project-status)

</div>

---

## Table of contents

- [What it is](#what-it-is)
- [Why a keyboard?](#why-a-keyboard)
- [Features](#features)
- [Security model](#security-model)
- [Why open source](#why-open-source)
- [How it works](#how-it-works)
- [Design system](#design-system)
- [Project structure](#project-structure)
- [Build & run](#build--run)
- [Project status](#project-status)
- [Relationship to FlorisBoard](#relationship-to-florisboard)
- [Contributing](#contributing)
- [License & trademark](#license--trademark)
- [Acknowledgements](#acknowledgements)

---

## What it is

Aulaa Keyboard is a system keyboard (IME) for Android. On top of the normal
typing experience it adds an **Aulaa panel** — opened from a brand key on the
keyboard, exactly like the media/sticker panels you already know. From that panel
a merchant can generate an Aulaa payment link and drop it straight into whatever
chat they're in (WhatsApp, Instagram DM, Telegram, …), without switching apps or
opening the web dashboard.

It is a friendly fork of [FlorisBoard](https://github.com/florisboard/florisboard)
(Apache-2.0), so it inherits a mature, privacy-respecting typing engine and adds
the Aulaa payment flow and branding on top.

## Why a keyboard?

Merchants spend their day answering customers in chat apps. Asking them to stop,
switch to a dashboard, create a payment link, copy it, switch back, and paste —
for every order — is friction that costs sales. Putting "create a payment link"
*inside the keyboard* removes every one of those steps: the merchant stays in the
conversation and the link appears in the message box.

## Features

From the Aulaa panel:

- **Transaksi (one-off link).** Enter a nominal, optionally an Order ID (your own
  reference — auto-generated if left blank), pick a project, and tap **Buat Link
  Pembayaran**. You get a `payment.aulaa.co/pay/…` link ready to paste. The
  customer chooses their own payment method (QRIS, virtual account, e-wallet) on
  the Aulaa payment page.
- **Produk (product link).** Browse your existing products and send a product's
  `payment.aulaa.co/buy/…` link. (Creating new products stays in the web
  dashboard — the keyboard only lists them.)
- **Paste into chat.** The finished link is committed directly into the active
  text field via the IME.
- **Light & dark**, following the system theme, with the dashboard's exact palette
  and typography.

> Screenshots will be added under `docs/` once the IME wiring is built. A visual
> design preview of the panel exists (HTML mock) and the Compose UI mirrors it.

## Security model

A keyboard is an always-present, sensitive surface, so Aulaa Keyboard is built to
hold **as little power as possible**:

- **Scoped device token, not a full session.** The keyboard authenticates with a
  token that can *only* create transaction links, list projects/products, and read
  a transaction's status. It **cannot** request payouts, change bank details, or
  touch account settings. Worst case if a device is lost or compromised: an
  attacker can create payment links that collect money **into the merchant's own
  account** — not theirs.
- **Pairing, not passwords.** You never type your merchant password into the
  keyboard. Instead you pair the device from the Aulaa web dashboard
  (**Account Settings → Keyboard devices**): the dashboard shows a short-lived
  code/QR, the companion app claims it, and the backend issues the scoped token.
- **No secrets in the app.** Because this app is open source, nothing sensitive is
  compiled into it — the token is obtained at runtime via pairing and stored in
  Android's `EncryptedSharedPreferences`.
- **Revocable & auditable.** Every paired device is listed in the dashboard with a
  last-used time and can be revoked individually.

## Why open source

A keyboard can read everything you type. The only way to *prove* it isn't quietly
logging or exfiltrating keystrokes is to let anyone read the code and inspect the
network traffic. For a payment-adjacent tool, that transparency is the whole point.

Aulaa Keyboard talks only to Aulaa's documented API over TLS, carries no hidden
telemetry, and bakes in no secrets. See [`FORK.md`](FORK.md) for provenance.

## How it works

```
┌─────────────────────────────┐        ┌──────────────────────────┐
│  Aulaa Keyboard (this app)   │        │  Aulaa backend (api-server)│
│                              │        │                          │
│  IME ── Aulaa panel          │        │  POST /v1/transactions   │
│   │      • Transaksi         │  HTTPS │  GET  /v1/projects       │
│   │      • Produk            │ ─────► │  GET  /v1/products       │
│   │        (Bearer: scoped   │        │  GET  /v1/transactions/… │
│   │         device token)    │        │                          │
│   └─ commitText(link) ─► chat│        │  POST /v1/keyboard/      │
│                              │        │        pairing/claim     │
│  Companion Activity          │        │  (scoped token issued)   │
│   • pair via QR / code       │        └──────────────────────────┘
│   • EncryptedSharedPreferences│
└─────────────────────────────┘
```

The payment feature is intentionally **self-contained** (`co.aulaa.keyboard.payment`)
and does not import FlorisBoard internals; it connects through four small seams
documented in
[`app/src/main/kotlin/co/aulaa/keyboard/payment/INTEGRATION.md`](app/src/main/kotlin/co/aulaa/keyboard/payment/INTEGRATION.md):

1. show the panel from the Smartbar,
2. commit the link to the editor,
3. provide a real `AulaaApi` (OkHttp) carrying the scoped token,
4. pairing + token storage.

The backend pieces (pairing endpoints + scoped-token middleware + the dashboard
"Keyboard devices" screen) live in the main Aulaa repository, not here.

## Design system

Colors, typography, radius and dark/light behavior are taken **directly from the
Aulaa tenant-dashboard** design tokens:

- shadcn **neutral** base (grayscale `oklch` surfaces), mapped to sRGB in
  [`AulaaColor.kt`](app/src/main/kotlin/co/aulaa/keyboard/payment/theme/AulaaColor.kt)
  with each source `oklch` token noted;
- **Aulaa brand blue** (`#2183B9 → #43BAE8`) reserved for the brand mark and
  primary actions only;
- **Geist** type, weights capped at medium (no bold), 10dp corner radius;
- full **light & dark** palettes in
  [`AulaaTheme.kt`](app/src/main/kotlin/co/aulaa/keyboard/payment/theme/AulaaTheme.kt).

## Project structure

```
.
├── app/                        # the keyboard app (FlorisBoard base + Aulaa)
│   └── src/main/kotlin/
│       ├── dev/patrickgold/florisboard/   # upstream IME engine (namespace kept)
│       └── co/aulaa/keyboard/payment/      # ← Aulaa feature (self-contained)
│           ├── theme/   AulaaColor.kt, AulaaTheme.kt
│           ├── data/    AulaaPayment.kt    (AulaaApi, PairingStore, DTOs, stub)
│           ├── ui/      PaymentPanel.kt    (Compose panel)
│           └── INTEGRATION.md              (the 4 wiring seams)
├── lib/, libnative/, benchmark/   # upstream modules
├── brand/                      # Aulaa brand assets (trademarked)
├── FORK.md                     # fork provenance & upstream remote
├── NOTICE                      # attribution (Apache-2.0)
├── TRADEMARK.md                # brand terms
├── README-FLORISBOARD.md       # the original upstream README
└── LICENSE                     # Apache License 2.0
```

The internal `applicationId` is `co.aulaa.keyboard`; the Kotlin `namespace` and the
IME service class intentionally stay `dev.patrickgold.florisboard` so no package
rename was needed.

## Build & run

Standard FlorisBoard toolchain:

- **JDK 11+** and the **Android SDK** (compileSdk 37, minSdk 26 / Android 8).
- Open the project in **Android Studio** and run the `app` module, or:

```bash
./gradlew :app:assembleDebug
```

Then enable "Aulaa Keyboard" in **System Settings → Language & input** and select
it from the input switcher.

> **Heads-up:** the Aulaa additions were scaffolded on a machine without a
> JDK/Android SDK, so they have **not been compiled yet**. Build and verify in
> Android Studio before relying on them.

## Project status

| Area | State |
|------|-------|
| Fork + rebrand (applicationId, app name, project name) | ✅ done |
| Aulaa Compose theme from dashboard tokens (light/dark) | ✅ done |
| Payment panel UI (Compose, self-contained, stub API) | ✅ done |
| IME wiring — Smartbar action + editor commit | ⏳ TODO ([INTEGRATION.md](app/src/main/kotlin/co/aulaa/keyboard/payment/INTEGRATION.md)) |
| Real API (OkHttp) + pairing + EncryptedSharedPreferences | ⏳ TODO — needs backend pairing endpoints |
| Companion-app pairing screen (scan QR / enter code) | ⏳ TODO |
| Backend: pairing + scoped device token | ↗ separate work in the main Aulaa repo |

## Relationship to FlorisBoard

Aulaa Keyboard is forked from FlorisBoard at commit `bac346e8` (base
`v0.6.0-alpha02`). Upstream git history is preserved and the `upstream` git remote
points at FlorisBoard, so changes can be diffed against or pulled from upstream:

```bash
git remote add upstream https://github.com/florisboard/florisboard.git  # already set
git fetch upstream
git diff upstream/main -- <path>
```

Details in [`FORK.md`](FORK.md).

## Contributing

Issues and pull requests are welcome. Please keep Aulaa-specific code under
`co.aulaa.keyboard.*` and keep it free of secrets — this is a public repository and
the app must remain auditable. Changes that touch the upstream engine should stay
close to FlorisBoard's conventions so upstream can still be merged.

## License & trademark

- **Code** is licensed under the **Apache License 2.0** — see [`LICENSE`](LICENSE)
  and [`NOTICE`](NOTICE).
- The **"Aulaa" name, logo and brand assets** are trademarks of Aulaa and are *not*
  covered by the code license. You may fork and redistribute the code, but a
  redistributed build must be renamed and rebranded — see [`TRADEMARK.md`](TRADEMARK.md).

## Acknowledgements

Built on the excellent work of [FlorisBoard](https://github.com/florisboard/florisboard)
and its contributors. Thank you. 💙
