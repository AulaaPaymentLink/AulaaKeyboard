# Aulaa payment panel — integration seams

This package (`co.aulaa.keyboard.payment`) is **self-contained**: it does not import
any FlorisBoard internals. To wire it into the keyboard, connect these four seams.
File paths are relative to the repo root.

## 1. Show the panel from the Smartbar

FlorisBoard renders panels (emoji/media) in the region below the text row. Add an
Aulaa quick action that swaps that region for `PaymentPanel`.

- Quick actions live under `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/`.
- The media/panel surface is `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/`.
- Add an action whose `onClick` sets keyboard UI state to an `Aulaa` panel mode,
  and in the panel host render:

```kotlin
AulaaTheme(darkTheme = /* follow the active Floris theme or the system */) {
    PaymentPanel(
        api = aulaaApi,                 // see seam 3
        onCommitText = { editor.commitText(it) },  // see seam 2
        onClose = { /* restore the normal keyboard layout */ },
    )
}
```

## 2. Commit the link into the active editor (`onCommitText`)

The link is pasted into whatever field the user is typing in (WhatsApp, etc.).

- Editor logic: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard3/ImeEditor.kt`
  and the service `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`.
- Implement `onCommitText` by calling the editor's existing commit path (ultimately
  `InputConnection.commitText(text, 1)`). Prefer the Floris `ImeEditor` wrapper over
  touching `currentInputConnection` directly, so autospace/undo behave consistently.

## 3. Provide a real `AulaaApi` (seam to the backend)

Replace `StubAulaaApi` with an OkHttp implementation of `AulaaApi` that calls the
existing merchant endpoints with the scoped device token as a Bearer header:

| AulaaApi call            | HTTP                                            |
|--------------------------|-------------------------------------------------|
| `createTransactionLink`  | `POST /v1/transactions` `{project_id, amount, order_id?}` |
| `listProjects`           | `GET /v1/projects`                              |
| `listProducts`           | `GET /v1/products`                              |
| `transactionStatus`      | `GET /v1/transactions/{id}`                      |

- Link shapes: `https://payment.aulaa.co/pay/{invoiceId}` and `…/buy/{productId}`.
- The token comes from `PairingStore` (seam 4). Send it as `Authorization: Bearer <token>`.
- The token is scoped server-side to exactly these four capabilities — see the
  backend "device pairing & scoped token" work in the main repo.

## 4. Pairing + token storage (`PairingStore`)

The IME must never collect the merchant password. Instead:

1. Add a **companion-app Activity** (a normal settings screen, not the IME surface)
   that scans a QR / enters a pairing code shown in the Aulaa web dashboard
   (**Account Settings → Keyboard devices**), then calls
   `POST /v1/keyboard/pairing/claim {code, device_name}` and stores the returned
   `device_token`.
2. Implement `PairingStore` with **EncryptedSharedPreferences**. The Activity and
   the `FlorisImeService` share the same app package/process sandbox
   (`co.aulaa.keyboard`), so both read/write the same store — no secret is baked
   into the APK (important: this app is open-source).
3. If `PairingStore.isPaired()` is false, the panel should show a short "pair this
   keyboard in the Aulaa app" prompt instead of the form.

## Notes / TODO
- `BackupDefaults.PACKAGE_NAME_PREFIX` still reads `dev.patrickgold.florisboard`;
  revisit if keyboard backups must carry the Aulaa package prefix.
- Localized `app_name` strings (`values-*/strings.xml`) still say "FlorisBoard";
  only the default `values/strings.xml` was rebranded. Update per-locale as needed.
