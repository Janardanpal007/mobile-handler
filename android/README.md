# Mobile Handler (Android app)

Source code of **Mobile Handler 2.0.5** (build 18), one private Android app to clean,
protect and handle your phone. Android 8.0 or newer, ARM phones (`arm64-v8a`, `armeabi-v7a`).
The package name is still `com.cleanai.debloat` from the app's first version; changing it would
make Android treat it as a different app.

## What's inside

| Tool | Code |
|---|---|
| **Phone Cleaner** and **Restore Apps**: switch off or remove preinstalled apps through Shizuku, undo any change | `data/BloatDb.kt`, `data/Guard.kt`, `data/ShizukuShell.kt`, `data/ChangeStore.kt` |
| **Ad Blocker**: Android's Private DNS (AdGuard, AdGuard Family, Mullvad) and a tracker scan of installed apps | `agent/AgentViewModel.kt`, `data/TrackerScanner.kt` |
| **Photo Enhancer**: HD (4×) and restore with Real-ESRGAN models in `assets/`; colorize with DDColor (one-time download) | `data/PhotoAi.kt`, `ui/PhotoScreen.kt` |
| **Scan Document**, **Scan QR**, **Photos → PDF** (Google Play services scanners, no camera permission) | `MainActivity.kt`, `agent/AgentViewModel.kt` |
| **PDF Editor** (edit text, sign, stamp, annotate, pages, merge, split, compress, password) | `ui/Pdf*.kt`, `data/Pdf*.kt` |
| **Word** (.docx) and **Excel** (.xlsx, CSV, formulas) editors | `data/Docx*.kt`, `data/Sheets.kt`, `data/XlsxSave.kt`, `data/Formula.kt` |
| **Calculator** (basic, scientific, finance, currency, units) and the floating calculator | `ui/Calculator*.kt`, `overlay/FloatingCalcService.kt` |
| **Notes** (checklists, tags, pins, fingerprint lock), **Weather**, **My Files** | `ui/NotesScreen.kt`, `data/Answers.kt`, `ui/FilesScreen.kt` |
| **Ask Sparky**: the in-app assistant that opens the right tool and searches the web for help (no API key) | `agent/`, `data/WebHelp.kt`, `ui/ChatScreen.kt` |
| Update check against the latest GitHub release | `data/Updates.kt` |

All paths are under `app/src/main/java/com/cleanai/debloat/`. Home-screen tiles are listed in
`features/Features.kt`.

## Why Phone Cleaner can't brick your phone

1. **Only curated packages are offered.** Anything not in `BloatDb.kt` is never shown.
2. **System-critical apps are locked** (`Guard.kt`): Play services, System UI, Settings, phone,
   package installer, network stack and more.
3. **Your default apps are locked**: home screen, keyboard, SMS, phone and browser.
4. **"Caution" apps can only be disabled**, never removed.
5. **Removal is `pm uninstall -k --user 0`**: the original copy stays on the system partition.
   Every change is recorded and can be undone from **Restore Apps**, and a factory reset brings
   everything back.

Shizuku must be running (after every phone restart, open Shizuku and tap **Start**). The app walks
you through the setup.

## Build from source

You need **JDK 17** (or newer) and the **Android SDK** with platform 34. Either open this `android/`
folder in Android Studio, or tell Gradle where the SDK is with the `ANDROID_HOME` environment
variable or a `local.properties` file (git-ignored) containing `sdk.dir=/path/to/Android/Sdk`.

Run all commands from this `android/` folder. On Windows use `gradlew.bat` instead of `./gradlew`.
On macOS/Linux, if `./gradlew` says *Permission denied*, run `chmod +x gradlew` once (or use
`sh gradlew`).

### Debug build (no key needed)

```
./gradlew assembleDebug
./gradlew test                # unit tests
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Install it with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy it to the phone and open it.
Debug builds are signed with your computer's own debug key, so only use them for testing.

### Release build (signed with your release key)

Release builds are signed with a release key that **you** create and keep. The key and its
passwords are never put in Git: `keystore.properties`, `*.jks` and `*.keystore` are git-ignored.
Without a key, `assembleRelease` stops with *"Release signing is not configured"* and a list of
what is missing. Release builds are never debuggable.

**1. Create the key once** (`keytool` comes with the JDK). Keep the file outside this repository:

```
keytool -genkeypair -v -keystore ~/keys/mobile-handler-release.jks -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 -alias mobilehandler
```

Choose a strong password when asked. **Back up the `.jks` file and the password somewhere safe.**
If you lose either, you can never publish an update that installs over the existing app.

**2. Tell the build where it is**, either in `android/keystore.properties`:

```
storeFile=/home/you/keys/mobile-handler-release.jks
storePassword=your-keystore-password
keyAlias=mobilehandler
keyPassword=your-key-password
```

(a relative `storeFile` is read from this `android/` folder; with a PKCS12 key, `keyPassword` is the
same as `storePassword`), or with environment variables, for example on a build server:

```
export MH_KEYSTORE=/home/you/keys/mobile-handler-release.jks
export MH_KEYSTORE_PASSWORD=...
export MH_KEY_ALIAS=mobilehandler
export MH_KEY_PASSWORD=...
```

A value in `keystore.properties` wins; any value missing there is taken from the environment.

**3. Build and check:**

```
./gradlew assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

(`apksigner` is in the SDK's `build-tools/<version>/` folder.) The certificate shown must be your
release key, not `CN=Android Debug`.

**4. Publish:** do not commit the APK (`*.apk` is git-ignored).

1. Create a GitHub release with tag `vX.Y.Z` (matching `versionName`).
2. Attach the signed `app-release.apk`, renamed to exactly `MobileHandler.apk`.
3. The website's Download button points to
   `https://github.com/Janardanpal007/mobile-handler/releases/latest/download/MobileHandler.apk`,
   which only works while the latest release has an asset with that exact name.

The app's update check (`data/Updates.kt`) offers the first `.apk` attached to the latest release.

### One-time reinstall when moving to the release key

Version 2.0.5 and earlier were published signed with a debug key. Android only installs an update
signed with the same key, so the first release-signed APK will **not** install over those
versions ("App not installed" / "package conflicts"). Users must uninstall the old Mobile Handler
once and then install the new APK. Uninstalling deletes the app's data (notes, saved signatures,
settings, the Restore Apps history), so tell users to first export their notes (Notes can export
to Markdown) and to use **Restore Apps** for any removed app they may want back: apps removed with
Phone Cleaner stay removed, and the new install's Restore Apps list starts empty. Later updates
signed with the same release key install normally.

## Adding more bloatware

Add a line to `app/src/main/java/com/cleanai/debloat/data/BloatDb.kt`:
`rec(...)` = recommended to remove, `opt(...)` = your choice, `cau(...)` = disable only.
Only add packages you have verified are safe.
