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
| 8 | **Calculator** | ✅ (upgraded in 1.7) | iPhone-style basic calculator, Scientific, Finance (EMI, SIP, compound interest, GST) and Currency tabs, plus a floating calculator. | See "Office tools and calculator" below. |
| 9 | **Weather** | ✅ | Open-Meteo current + 3-day forecast, by city name or coarse location. | Shown as chat text only; no dedicated screen. Needs internet. |
| 10 | **My Files** | 🟡 | Lists the app's own scans/PDFs/photos; open, share PDF, share images, delete. | Not a general file manager: no browsing of phone storage, rename, move, copy, search or sort. |

Also present: first-run profile (name, age, place), Settings (theme, Shizuku, restore, update check & notification, keep-alive help, clear chat), About & Privacy screen, in-app update checker against GitHub releases.

## New features (requested) — current state

| # | Feature | Status | Notes |
|---|---|---|---|
| 11 | PDF Reader & Editor | ✅ | Edit right in the reader: change existing text, add text, pen, highlight/underline/strike that snaps to words, shapes, signature, pictures; move, resize, recolour or delete anything added; rotate, reorder, delete and add pages; undo/redo; save in place or as a copy. Merge, extract, remove pages, compress. PdfBox-Android (Apache-2.0). |
| 12 | Word DOCX | ✅ | Small built-in DOCX reader/writer (no Apache POI). Create and edit: title, headings, bullets, numbered lists, bold/italic/underline, alignment. |
| 13 | Excel XLSX / CSV | ✅ | Built-in XLSX/CSV reader/writer and formula engine (SUM, AVERAGE, MIN, MAX, COUNT, IF, ROUND, maths functions, ranges, several sheets). |
| 14 | Scientific & financial calculator | ✅ | Scientific tab (and landscape on the Basic tab), Finance tab. |
| 15 | Floating calculator | ✅ | Tap ⧉: the app steps aside and the calculator floats over the home screen (picture-in-picture style). Tap its title to go back to the full calculator with the same sum. Asks for "Display over other apps" first and starts by itself once allowed; stops when closed. |
| 16 | OCR / document processing | ❌ | ML Kit Text Recognition (on-device) or Tesseract (Apache-2.0). |
| 17 | Image editing & conversion | ❌ | Crop/rotate/resize/convert can be done with Android APIs. |
| 18 | Optional Smart Assistant | 🟡 | The rule-based assistant exists; an optional on-device/online model is not started. |
| 19 | Settings, backup, privacy | 🟡 | Settings exist (Private mode removed 6 Nov 2026 at the owner's request); no backup/export of settings and files. `allowBackup=false`. |
| 20 | About section | ✅ | "Built by Janardan Pal", version, "Connect with me" with GitHub, LinkedIn and Discord links, privacy policy. |
| 21 | Notes | ✅ | Text notes and checklists, colours, pins, tags, search, trash (30 days), notes locked with the phone's fingerprint/screen lock, share and Markdown export. Written for this app (inspired by Notesnook's feature list; no Notesnook code, which is GPL-3.0). |

## Permissions review

`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (update notice), `ACCESS_COARSE_LOCATION` (weather, asked on demand), `QUERY_ALL_PACKAGES` (needed for the cleaner/tracker scan; would need a Play Store declaration). New in this update: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`, used only by the floating calculator, which runs only while it is open. `USE_BIOMETRIC` lets locked notes show the phone's own fingerprint/screen-lock prompt (the app never sees the fingerprint or PIN). No other background services. Office files are opened and saved through the system file picker, so no storage permission is needed. Shizuku provider is exported with the standard Shizuku permission.

## Licences

Real-ESRGAN (BSD-3), DDColor (Apache-2.0), PdfBox-Android (Apache-2.0), JUnit (EPL-1.0, tests only), kXML2 (BSD, tests only), Shizuku API (MIT), LiteRT (Apache-2.0), AndroidX/Compose (Apache-2.0). ML Kit scanners are free but closed-source Google Play services components.

## Changes in v1.7 (this update)

- **Calculator redesigned to look and behave like the iPhone calculator:** black screen, round keys in iOS colours (grey AC/±/%, orange operators, dark digits, wide 0), selected operator turns white until the next number, AC ↔ C, ± and iOS-style % (`200 + 10 %` = 220), × and ÷ before + and −, repeated `=` repeats the last step, swipe the display to delete a digit, thousands separators, 9-digit limit, numbers shrink to fit and overflow to `8.88889e9`, `Error` on divide by zero, grey expression line above the result, key-press haptics and press highlight, white status-bar icons on the black screen.
- Currency converter kept, restyled with the same keys and an iOS segmented control.
- Version 1.7 (code 9).

Tested on a connected phone (1080×2400): `12 + 3 × 4 = 24`, repeat `=` → 96, `987,654,321 × 9 = 8.88889e9`, `3 ÷ 0 = Error`, Currency tab layout.

## Office tools and calculator (this update)

**PDF Reader & Editor** (`ui/PdfScreen.kt`, `ui/PdfEditorScreen.kt`, `data/PdfDoc.kt`, `data/PdfEdit.kt`)
- Open any PDF from the phone or one already in My Files. Pages scroll like a reader, two fingers zoom, and every tool works right on the page.
- **Edit text:** tap a line of existing text, change or delete it. The old words are covered with the page colour and the new text is written in a matching font (sans, serif or mono, bold), size and colour.
- **Add:** text boxes (4 sizes, bold, 6 colours), pen (3 widths), highlight / underline / strike-out that snaps to the words (a see-through marker on scans), rectangle, oval, line, arrow, signatures (up to 6 kept privately on the phone to pick from, each one can be deleted), stamps (Approved, Paid, Draft, Confidential and more, or your own words; red, green or blue; optional date), pictures from the gallery. Eraser.
- **Change what you added:** tap or drag to select and move, drag the corner to resize, recolour, edit or delete. Undo and redo.
- **Pages:** rotate, reorder, delete, add blank pages.
- Signatures and pictures go on the page you are looking at (the page under the middle of the screen), where you are looking on it; the page counter uses the same page.
- **Save** writes back to the same file (or *Save as a copy* when the folder doesn't allow changes); asks before leaving with unsaved work. Added items are saved as standard PDF annotations with their own appearance, so other PDF apps show them, and this app can still move or delete them later.
- Merge (choose order), extract pages, remove pages (`1-3, 5` style ranges), compress (re-saves pictures as JPEG; keeps the original if it would not get smaller), watermark (diagonal text on every page, 4 colours, 3 strengths, any language), password: lock a copy with AES-256, or save an unlocked copy when you know the password (checked before saving; the password is never stored).
- Limits: an edited line's original text is covered, not removed, so search or copy-paste in other apps can still find it; text editing works one line at a time and not on pages turned inside the editor until saved; scanned pages have no text to edit; password-protected PDFs open in the editor only after the Password tool saves an unlocked copy; no form filling.

**Notes** (`ui/NotesScreen.kt`, `data/Notes.kt`)
- Text notes and checklists (Enter adds an item, ticked items move to the bottom, clear ticked), switch a note between text and checklist.
- Colours: 6 quick pastels or any colour from a 12-hue colour chart (text turns white on dark colours), pin to the top, tags with filter chips, search (title, text, items, tags), trash with restore that empties itself after 30 days.
- Lock a note: it shows as "🔒 Locked" in the list and opens only after the phone's fingerprint/face/screen-lock prompt; screenshots are blocked while it is open. Needs a screen lock on the phone.
- Share a note, save it as a Markdown file, or export all notes to one Markdown file (locked notes are left out).
- Stored in one file in the app's private storage, written safely (temporary file, then rename). Nothing is uploaded.
- Limits: plain text only (no bold, images or attachments); a lock hides the note in the app but the file itself is not encrypted; no sync between devices.

**Word** (`ui/WordScreen.kt`, `data/Docx.kt`)
- New document or open a `.docx`; title, heading 1/2, text, bullet and numbered lists, bold/italic/underline, left/centre/right; Save, Save as, Share; asks before leaving with unsaved changes.
- Limits: pictures, tables, headers/footers and footnotes in a file you open are not kept when you save (a banner warns you); fonts and colours are not kept.

**Excel** (`ui/SheetScreen.kt`, `data/Sheets.kt`, `data/Formula.kt`)
- New sheet or open `.xlsx` / `.csv`; formula bar, AutoSum and function chips, tap a cell to insert its reference while typing a formula, several sheets; save as `.xlsx` (formulas with their results) or `.csv`.
- Errors shown like Excel: `#DIV/0!`, `#NAME?`, `#VALUE!`, `#REF!`, `#NUM!`, plus `#CYCLE!` for circular references.
- Limits: cell formatting, column widths, merged cells and charts are not kept; CSV saves values only.

**Calculator** (`ui/CalculatorScreen.kt`, `ui/CalculatorModes.kt`, `overlay/FloatingCalcService.kt`)
- Tabs: Basic (iPhone-style), Scientific (sin/cos/tan, ln/log, √, xʸ, x!, π, e, Ans, Deg/Rad), Finance (Loan EMI, SIP, compound interest, GST add/remove with CGST/SGST), Currency.
- Edit a calculation like on Samsung phones: tap the sum or the answer, tap where the cursor should go, change it with the keypad (⌫ deletes, C empties the sum and then turns into AC, which clears everything), and the answer updates as you type; = keeps it. Back leaves editing unchanged. On the Scientific tab, tapping the sum opens a small box with the same live answer.
- History: the last 100 calculations from Basic, Scientific and the floating calculator, kept privately on the phone; tap one to edit and reuse it, or clear them all.
- Floating calculator: tap ⧉; draggable, can be collapsed, long-press the display to copy. Runs as a foreground service with a notification only while it is open.

**Sparky** understands: "merge PDFs", "open Word", "new spreadsheet", "make a note" / "to-do list", "loan EMI", "scientific calculator", "floating calculator" and opens the real tool.

**Tests**: `app/src/test/.../OfficeTest.kt` — 10 JVM unit tests (formula maths and errors, sheet formulas, XLSX round trip, reading an `.xlsx` made by openpyxl, CSV quoting, DOCX round trip, reading a `.docx` made by python-docx). All pass.

**Checked on the phone (1080×2400):** merge 2 PDFs → 5 pages; annotate + Save copy (stroke and text found in the saved file); compress 617 KB → 368 KB; extract pages 2-3 → 2 pages; Word document with title, list and bold saved and read back with python-docx; spreadsheet formulas and `.xlsx` save read back with openpyxl; EMI, SIP and GST results match the standard formulas; Scientific `2×sin(30)+√16 = 5`; floating-calculator button opens the "Display over other apps" screen.
**Checked on the phone after the editor and Notes update:** edit an existing line (12 → 25 percent), add a text box, move it, highlight snapped to a sentence, pen stroke, signature placed and resized, page rotate / reorder / blank page, save in place → reopened with every item; the saved file read with pypdf has the new text on the page, FreeText/Highlight/Ink/Stamp annotations and the right page order and rotation. Floating calculator over the home screen, 12×3 = 36, title tap returns to the app's calculator with 36. Notes: text note, checklist, colour, tag, pin, lock (the phone's fingerprint prompt appears; cancelling keeps the note closed), trash, saved file survives a restart.
**Not checked on the phone:** placing a picture from the gallery (system photo picker), opening a locked note with a real fingerprint, picking files in the system file picker (it does not accept automated taps on this phone).

## Suggested next steps (in the agreed priority order)

1. Home: let Sparky take commands directly on the Home screen.
2. Phone Cleaner / Ad Blocker: OEM testing and list coverage.
3. My Files: real storage browsing, rename/move/copy.
4. OCR (feature 16) and image editing (feature 17).
