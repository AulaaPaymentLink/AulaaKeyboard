# Aulaa payment panel — integration architecture & API contract

This package (`co.aulaa.keyboard.payment`) contains the Aulaa payment feature and connects to the FlorisBoard base through four seams:

## 1. Show the panel from the Smartbar (`ImeUiMode.AULAA`)

- `ImeUiMode.AULAA(3)` is registered in `dev.patrickgold.florisboard.ime.ImeUiMode`.
- `ImeActions.ShowAulaaPanel` (`@fl:action/show_aulaa_panel`) and `ImeIcons.AulaaPanel` (`@fl:icon/aulaa_panel`) are wired in `ImeActions.kt`, `ImeIcons.kt`, `Icon3.kt`, `displays-implied.xml`, and `QuickAction.kt`.
- `QuickActionArrangement.Default` sets `ShowAulaaPanel` as the default `stickyAction` on the Smartbar, and `QuickActionArrangement.Serializer` ensures existing arrangements include `ShowAulaaPanel`.
- `ImeWindow.kt` renders `AulaaInputLayout()` when `activeImeUiMode == ImeUiMode.AULAA`, matching the active Floris theme's day/night mode (`activeTheme.config.isNightTheme`).

## 2. Commit the link into the active editor (`onCommitText`)

- In `ImeWindow.kt`, `AulaaInputLayout` implements `onCommitText` by calling `imeController.updateStateBlocking { emitText(link.asK3String()); state = state.copy(flags = state.flags.withImeUiMode(ImeUiMode.TEXT)) }`, inserting the link directly into the active text field (WhatsApp, Instagram, Telegram, etc.) and returning to the keyboard layout.
- Inside `PaymentPanel.kt`, an inline compact numeric keypad (`InlineAmountNumpad`) allows entering custom nominals directly inside the IME window without requiring a second soft keyboard.

## 3. Production HTTP API client (`HttpAulaaApi`)

`HttpAulaaApi` implements `AulaaApi` over `HttpURLConnection` + `kotlinx.serialization.json` against the final backend contract:

| `AulaaApi` method | HTTP endpoint | Scope / Auth |
|-------------------|---------------|--------------|
| `claimPairing(code, deviceName)` | `POST /v1/keyboard/pairing/claim` `{code, device_name}` | Public (returns `aulakb_<64_hex>` + merchant/device/projects/`pay_base_url`) |
| `createTransactionLink(projectId, amount, orderId)` | `POST /v1/transactions` `{project_id, amount, order_id?}` | `Bearer <device_token>` (`transactions:create`) |
| `transactionStatus(id)` | `GET /v1/transactions/{id}` | `Bearer <device_token>` (`transactions:read`) |
| `listProjects()` | `GET /v1/projects` | `Bearer <device_token>` (`projects:read`) |
| `listProducts(projectId, query)` | `GET /v1/products?project_id=...` | `Bearer <device_token>` (`products:read`) |

- Shareable links use `pay_base_url` from the pairing claim response (default `https://payment.aulaa.co`):
  - Transaction invoice link: `{pay_base_url}/pay/{invoice.id}`
  - Product checkout link: `{pay_base_url}/buy/{product.id}`

## 4. Pairing + Android Keystore token storage (`AndroidKeystorePairingStore` & `AulaaScreen`)

- `AndroidKeystorePairingStore` encrypts the `PairingSession` (including `device_token`) with a hardware-backed AES-256-GCM key (`co.aulaa.keyboard.pairing_aes_key`) in `AndroidKeyStore`.
- `AulaaScreen` (`Routes.Settings.Aulaa`, accessible from the top of `HomeScreen` or via the "Hubungkan Perangkat" button in the keyboard panel) lets merchants claim an 8-character pairing code (`ABCD-EFGH`) or scan the dashboard QR deep link (`aulaa-keyboard://pair?code=ABCDEFGH`).
