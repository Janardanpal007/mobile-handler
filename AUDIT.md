# Mobile Handler – Source Audit (v1.6.1 → v1.7)

Date: 2026-11-02 · Audited code: `android/` (package `com.cleanai.debloat`, ~6,500 lines of Kotlin, Jetpack Compose)

## Where the source was

The GitHub repository previously held only the landing site (`index.html`, screenshots) and the APK releases.
The Android source for v1.6.1 lived only in a temporary folder on the development PC. It is now in
`android/` in this repository, so it has Git history from this point on. Build outputs, `local.properties`,
APKs, keystores and old development screenshots are git-ignored.

Build: JDK 17, Android Gradle Plugin with Gradle 8.9, `compileSdk 34`, `minSdk 26`, arm64/armv7 only.
`./gradlew assembleDebug` builds successfully (APK ≈ 41 MB, mostly the two photo AI models in `assets/`).

Legend: ✅ works · 🟡 partly done · ❌ not started

## Existing features

| # | Feature | Status | What exists | What is missing / needs work |
|---|---|---|---|---|
| 1 | **Sparky** (Fire-Fox mascot + assistant) | 🟡 | `ui/Sparky.kt` animated mascot on Home (tap reaction). `agent/AgentViewModel.kt` routes typed text with rules to **real** actions: open any feature, scan QR/doc, make PDF, weather, maths, currency, clean, ad blocker, restore, settings, clear chat. No fake replies. | Commands are typed in the Assistant chat screen, not on Home itself. Matching is regex-based (fine offline) but brittle for phrasing variety. Greeting on Home exists ("Good night, <name>"). |
| 2 | **Phone Cleaner** | 🟡 | Shizuku-based `pm disable-user` / `pm uninstall -k --user 0` (`data/ShizukuShell.kt`). Curated list of 121 packages (`data/BloatDb.kt`); critical/default apps locked (`data/Guard.kt`); every change recorded (`data/ChangeStore.kt`) and undoable (Restore Apps). Step-by-step Shizuku setup guide incl. Xiaomi/ColorOS. | Needs more device testing per OEM. Without Shizuku nothing can be disabled (Android does not allow it) — the UI already explains this. List coverage for Samsung/Vivo/Motorola is thin. |
| 3 | **Ad Blocker** | 🟡 | Private DNS: AdGuard, AdGuard Family, Mullvad. Set automatically through Shizuku, or manual guided steps. DNS status check. Tracker scan of installed apps (`data/TrackerScanner.kt`). | As stated in the requirements: DNS cannot remove every in-app ad (same-domain ads). Tracker scan is signature-based; no per-app blocking. |
| 4 | **Photo Enhancer** | ✅/🟡 | On-device LiteRT: Real-ESRGAN 4× (`enhance_x4.tflite`) and restore model (`restore_x4.tflite`), DDColor colorize (separate ~100 MB download). Saves to My Files / gallery. | Slow on low-end phones; no before/after compare slider; no face restoration. |
| 5 | **Scan Document** | ✅ | Google ML Kit Document Scanner (edge detection, perspective fix, filters) → PDF in My Files. | Relies on Google Play services (not fully open source). No OCR yet. |
| 6 | **Photos → PDF** | ✅ | `data/PdfTools.kt` combines picked images (scaled, EXIF-rotated) into one PDF. | No page reordering/margins/page size options. |
| 7 | **Scan QR** | ✅ | ML Kit code scanner (no camera permission). Handles links, Wi-Fi, text, copy/open. | Relies on Play services. No scan history. |
| 8 | **Calculator** | ✅ (upgraded in 1.7) | Basic maths + currency converter (live rates; off in Private mode). | Scientific and financial modes and the floating overlay are still to do. |
| 9 | **Weather** | ✅ | Open-Meteo current + 3-day forecast, by city name or coarse location. | Shown as chat text only; no dedicated screen. Needs internet (Private mode off). |
| 10 | **My Files** | 🟡 | Lists the app's own scans/PDFs/photos; open, share PDF, share images, delete. | Not a general file manager: no browsing of phone storage, rename, move, copy, search or sort. |

Also present: first-run profile (name, age, place), Settings (theme, Private mode, Shizuku, restore, update check & notification, keep-alive help, clear chat), About & Privacy screen, in-app update checker against GitHub releases.

## New features (requested) — current state

| # | Feature | Status | Notes |
|---|---|---|---|
| 11 | PDF Reader & Editor | ❌ | Android `PdfRenderer` can view offline; editing needs a library (e.g. PdfBox-Android, Apache-2.0). |
| 12 | Word DOCX | ❌ | Apache POI is heavy on Android; consider a minimal DOCX writer. |
| 13 | Excel XLSX / CSV | ❌ | CSV is easy; XLSX via a light OOXML reader/writer. |
| 14 | Scientific & financial calculator | ❌ | Next step after the iOS-style redesign (landscape scientific like iOS). |
| 15 | Floating calculator | ❌ | Needs `SYSTEM_ALERT_WINDOW` permission and an overlay service started only on demand. |
| 16 | OCR / document processing | ❌ | ML Kit Text Recognition (on-device) or Tesseract (Apache-2.0). |
| 17 | Image editing & conversion | ❌ | Crop/rotate/resize/convert can be done with Android APIs. |
| 18 | Optional Smart Assistant | 🟡 | The rule-based assistant exists; an optional on-device/online model is not started. |
| 19 | Settings, backup, privacy | 🟡 | Settings and Private mode exist; no backup/export of settings and files. `allowBackup=false`. |
| 20 | About section | 🟡 | About screen shows "Built by Janardan / TSE", version and privacy policy. **Missing:** full name "Janardan Pal" and clickable GitHub, LinkedIn and Discord links. |

## Permissions review

`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (update notice), `ACCESS_COARSE_LOCATION` (weather, asked on demand), `QUERY_ALL_PACKAGES` (needed for the cleaner/tracker scan; would need a Play Store declaration). No background services. Shizuku provider is exported with the standard Shizuku permission.

## Licences

Real-ESRGAN (BSD-3), DDColor (Apache-2.0), Shizuku API (Apache-2.0), LiteRT (Apache-2.0), AndroidX/Compose (Apache-2.0). ML Kit scanners are free but closed-source Google Play services components.

## Changes in v1.7 (this update)

- **Calculator redesigned to look and behave like the iPhone calculator:** black screen, round keys in iOS colours (grey AC/±/%, orange operators, dark digits, wide 0), selected operator turns white until the next number, AC ↔ C, ± and iOS-style % (`200 + 10 %` = 220), × and ÷ before + and −, repeated `=` repeats the last step, swipe the display to delete a digit, thousands separators, 9-digit limit, numbers shrink to fit and overflow to `8.88889e9`, `Error` on divide by zero, grey expression line above the result, key-press haptics and press highlight, white status-bar icons on the black screen.
- Currency converter kept, restyled with the same keys and an iOS segmented control.
- Version 1.7 (code 9).

Tested on a connected phone (1080×2400): `12 + 3 × 4 = 24`, repeat `=` → 96, `987,654,321 × 9 = 8.88889e9`, `3 ÷ 0 = Error`, Currency tab layout.

## Suggested next steps (in the agreed priority order)

1. Home: let Sparky take commands directly on the Home screen.
2. Calculator: landscape scientific mode, financial tools, floating overlay.
3. About: full name and the three clickable links.
4. Phone Cleaner / Ad Blocker: OEM testing and list coverage.
5. My Files: real storage browsing, rename/move/copy.
