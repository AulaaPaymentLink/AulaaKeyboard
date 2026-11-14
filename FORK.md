# Aulaa Keyboard — fork provenance

**Aulaa Keyboard** is an open-source fork of **FlorisBoard**, an Apache-2.0 licensed
keyboard for Android.

| | |
|---|---|
| Upstream | https://github.com/florisboard/florisboard |
| Forked at | `bac346e8` (tag base `v0.6.0-alpha02`) |
| Upstream license | Apache License 2.0 (unchanged — see `LICENSE`) |
| Our additions license | Apache License 2.0 |

The upstream git history is preserved, and the `upstream` git remote points at
FlorisBoard, so changes can be diffed against or pulled from upstream:

```bash
git remote -v                 # upstream -> github.com/florisboard/florisboard
git fetch upstream
git diff upstream/main -- <path>
```

## Why open source

The keyboard reads everything the user types. Shipping it open-source lets anyone
verify there is no keystroke exfiltration and that the app only talks to Aulaa's
documented API over TLS. For a payment-adjacent tool this transparency is the point,
not a side effect. Consequently **no secret is ever baked into this client** — the
scoped device token is obtained at runtime via pairing (see
`app/src/main/kotlin/co/aulaa/keyboard/payment/INTEGRATION.md`).

## What we changed vs. upstream (so far)

- `applicationId` → `co.aulaa.keyboard` (internal `namespace` and the IME service
  class intentionally stay `dev.patrickgold.florisboard`; provider authorities
  follow `BuildConfig.APPLICATION_ID`).
- Default app name → "Aulaa Keyboard" (`app/src/main/res/values/strings.xml`).
- `rootProject.name` → `AulaaKeyboard`.
- Added the payment feature under `app/src/main/kotlin/co/aulaa/keyboard/payment/`
  (theme from the Aulaa tenant-dashboard tokens, panel UI, API/pairing contracts).

See `NOTICE` and `TRADEMARK.md` for attribution and brand terms.
