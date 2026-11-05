# Mobile Handler redesign: "Sparky Flow"

Source: ChatGPT, 6 Nov 2026, from our brief (full answer in your ChatGPT history: "App Redesign Blueprint",
https://chatgpt.com/c/6aed0206-75d8-83ee-b224-078ff9721cfe). Summarised here, with notes on what fits this app.

## Design language
- Principles: calm, fast, personal, consistent, private, accessible.
- Keep coral as the accent and mint as the second colour; fewer coloured cards.

| Token | Dark "Obsidian Forest" | Light "Soft Porcelain" |
|---|---|---|
| Canvas | #101512 | #F7F9F5 |
| Surface | #1B231E | #FFFFFF |
| Raised | #29332C | #EAF0E9 |
| Accent (coral) | #FF806B | #B84432 |
| Second (mint / green) | #BDE5B2 | #326B48 |
| Text | #EDF4ED | #19241C |
| Muted text | #AAB9AD | #526357 |
| Outline | #3B4A3E | #CBD6CC |

- Type (Roboto, tabular numbers in calculators): greeting 28sp, screen title 24, section 18, card title 16, body 14, label 12, calculator result 40.
- Corners: chips 8dp, controls 12, cards 16, featured panels 24, sheets 28. Spacing: 4 / 8 / 12 / 16 / 24dp.
- Tonal surfaces instead of shadows; 24dp rounded outline icons, filled only when selected.
- Motion: press 100–150ms, screens 180–240ms, sheets 240–300ms; honour "reduce motion". Light haptic on success.

## Home (task-first)
Top to bottom:
1. Small Sparky portrait + greeting + settings button (no big banner).
2. One search / command box: finds tools, files and Assistant commands.
3. Quick actions: 4 pinned shortcuts (default Scan document, Calculator, Edit PDF, Scan QR) that start the task directly; long-press to change.
4. Continue working: up to 2 real recent files, hidden when empty.
5. Tool categories: Documents, Media, Device, Notes.

Bottom bar: **Home · Tools · Files · Ask** (Settings moves to the Home header; the floating orb goes).

## Each tool: one main action
| Tool | Main action | Layout |
|---|---|---|
| Ad Blocker | Turn protection on | Status on top, providers, then help |
| Calculator | Keypad straight away | Tabs Basic / Scientific / Finance / Currency; history in a sheet |
| Scan QR | Camera straight away | Torch and Gallery below; result sheet with actions |
| Scan Document | Camera straight away | Crop, add pages, Save PDF |
| Photos → PDF | Photo picker straight away | Reorderable pages, Create PDF |
| PDF Editor | Open a PDF / recents | Reading canvas; bottom strip Mark up · Sign · Edit · Pages; Merge, Split, Compress, Watermark, Password in a Tools sheet |
| Word / Excel | New document / workbook | Recents list; editor with toolbar near keyboard |
| Notes | New note | Search and pinned on top, recent below |
| Photo Enhancer | Choose a photo | Big before/after; Enhance / Restore / Colorize |
| Assistant (Ask tab) | Command box | Suggestion chips, recent commands |
| Phone Cleaner + Restore Apps | Scan apps | Merged into one "Device" screen with review before any change |
| My Files | Open a recent file | Filters, search, sort, selection |
| Weather | Today | Hourly, 3 days, "updated at" time |

Rule: one tap *starts* a task; deleting, removing apps, signing or passwords always ask first.

## Small premium touches
1. Scan outline settles on the page with a light haptic.
2. Save shows a small snackbar with "Open", not a full dialog.
3. Sparky twitches an ear when a task finishes (off with reduce motion).
4. After a Wi-Fi QR, the "Connect" action appears straight away.
5. Reopen PDFs at the last page; calculator remembers its last mode.

## Merge or remove
- 15 equal tiles → 4 pinned actions + recents + categories.
- Restore Apps → inside Phone Cleaner ("Device").
- Photos → PDF and Scan Document → under "Create PDF" (both still launchable).
- Floating Assistant orb → Ask tab + search box.
- Settings out of the bottom bar.
All 15 features stay.

## 3D Sparky (ChatGPT images, 6 Nov 2026)
- `sparky/sparky-render.png` (1122×1402, transparent background): full body, waving. Use for Home and empty states.
- `sparky/sparky-poses.png` (1536×1024, off-white background): hello, thinking, happy, sleepy, oops.
- Before use: cut each pose out of the sheet onto a transparent background, and export WebP at 40, 96 and 200dp sizes.
- Pose use: hello on Home, sleepy for the night greeting, thinking while the Assistant works, happy when a task finishes, oops on errors.
- Made with ChatGPT image generation; say so in the README credits.

### Animated Sparky (ChatGPT "Sparky Flow" starter, 6 Nov 2026)
- ChatGPT sent a starter ZIP: 12 WebP frames (stand, blink, wave ×2, think ×2, idea, cheer, yawn, asleep, surprised, oops) and sample Kotlin for time-of-day moods and reaction priority.
- The frames came on a dark background; we cut them out (one shared crop so they line up) into `res/drawable-nodpi/sparky_frame_00…11.webp`. They replace the five single poses above, so Sparky looks the same everywhere.
- In the app: `SparkyMood.kt` holds the rules (morning 05–11, afternoon 12–16, evening 17–20, night 21–04; an error beats a success, a reaction beats "thinking", "thinking" beats the clock), with unit tests. `SparkyArt.kt` plays the frames: a short intro, then a still frame with a small idle move every 7 s; it stops in the background and with reduced motion.
- Used on Home (time of day), in the Assistant (thinking while it works, a cheer or an oops for 2.5 s when a task ends) and still frames in the nav bar and search row.
- Not done yet: a head-only crop for tiny sizes (the 28dp nav icon shows the whole body).

## Our notes (what fits, what to check)
- **Fits well:** the theme tokens, the Home layout, the bottom bar, the PDF tool strip + Tools sheet (we already have most of this), merging Restore into Phone Cleaner, snackbars, resume-where-you-stopped.
- **Already true here:** the Ad Blocker sets Private DNS through Shizuku (ChatGPT assumed an app can't); weather and currency already only go online when "Online mode" is on.
- **Worth doing:** encrypt locked notes with Android Keystore (today the lock hides the note but the file isn't encrypted).
- **Limits to keep in mind:** Private DNS needs Android 9+, so the Ad Blocker should say so on Android 8. ChatGPT's mention of Material 3 1.4.0 is unverified; we stay on the current version unless an upgrade is needed.
- **Suggested order:** 1) theme + Home + bottom bar + Tools screen, 2) everyday tools (Calculator, QR, Scan, Notes, Files), 3) document tools, 4) Device + Photo Enhancer + Ask, 5) accessibility pass (large text, TalkBack, reduce motion).
