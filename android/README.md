# Clean AI — chat-based debloater & tracker blocker

An Android app where an AI agent chats with you, scans your phone, recommends what to
remove (★), lets **you** decide Remove / Disable / Keep per app, and blocks ads &
trackers system-wide. No root needed.

## Why it can't brick your phone
1. **Only curated packages are offered.** Anything not in `BloatDb.kt` is never shown.
2. **System-critical apps are locked** (`Guard.kt`): Play Services, System UI, Settings,
   phone, package installer, network stack, etc.
3. **Your default apps are locked at scan time**: home screen, keyboard, SMS, phone, browser.
4. **"Caution" apps can only be disabled**, never removed.
5. **Removal is `pm uninstall -k --user 0`**: the original copy stays on the system
   partition. Every change is recorded and can be undone from *Restore removed apps*
   (and a factory reset brings everything back).

## Install on your phone
1. Copy `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it
   (allow "Install unknown apps" for your file manager when asked).
2. Or with USB debugging on: `adb install -r app-debug.apk`

## Using the AI agent — step by step
| Step | What you do | What the agent does |
|---|---|---|
| 1 | Open Clean AI, tap **Clean my phone ★** | Checks whether Shizuku is connected |
| 2 | Tap **Get Shizuku** and install it | Opens the Play Store / Shizuku |
| 3 | Tap **Open About phone** → tap *Build number* 7× (Xiaomi: *OS version*) | Developer options turn on |
| 4 | Tap **Open Developer options** → turn on *Wireless debugging* (Xiaomi: also *USB debugging (Security settings)*) | — |
| 5 | In Shizuku: *Pairing* → enter the code from Wireless debugging → **Start** | — |
| 6 | Back in Clean AI tap **I've started Shizuku** → **Allow** | Connects, scans all apps |
| 7 | Tap **Let me review each app ★** | Shows cards: 🟢 Recommended · 🔵 Your choice · 🟠 Caution |
| 8 | On each card tap Remove / Disable / Keep (★ = recommendation) | Remembers your choices |
| 9 | Tap **Apply my choices** → read the plan → **Yes, apply** | Removes/disables, shows ✓/✕ per app |
| 10 | Tap **Block ads & trackers** → **AdGuard DNS ★** | Lists apps with tracker SDKs, turns on Private DNS |

After a phone restart, open Shizuku and tap **Start** again before using Clean AI.

You can also type: "clean", "block ads", "restore", or an app name like "Glance".
Add a Claude API key in ⚙ to ask anything in free text (optional).

## Build from source
Needs JDK 17 and the Android SDK (`local.properties` points at `D:\Android\Sdk`).
```
set JAVA_HOME=C:\Users\janar\AppData\Local\Programs\jdk17\jdk-17.0.20.1+1
gradlew.bat assembleDebug
```
Or open the folder in Android Studio and press Run.

## Adding more bloatware
Add a line to `app/src/main/java/com/cleanai/debloat/data/BloatDb.kt`:
`rec(...)` = recommended to remove, `opt(...)` = your choice, `cau(...)` = disable only.
Only add packages you have verified are safe.
