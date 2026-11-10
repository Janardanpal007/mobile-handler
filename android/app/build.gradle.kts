import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: read from android/keystore.properties (git-ignored, never commit it) or, when a
// value is missing there, from the MH_KEYSTORE / MH_KEYSTORE_PASSWORD / MH_KEY_ALIAS / MH_KEY_PASSWORD
// environment variables. Debug builds don't need any of this. See android/README.md.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(env)?.trim()?.takeIf { it.isNotEmpty() }

val releaseStorePath = signingValue("storeFile", "MH_KEYSTORE")
val releaseStorePassword = signingValue("storePassword", "MH_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "MH_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "MH_KEY_PASSWORD")
// A relative storeFile is relative to the android/ folder.
val releaseStoreFile = releaseStorePath?.let { rootProject.file(it) }
val releaseSigningProblems = buildList {
    if (releaseStorePath == null) add("storeFile (or MH_KEYSTORE) is not set")
    else if (releaseStoreFile?.isFile != true) add("keystore file not found: ${releaseStoreFile?.absolutePath}")
    if (releaseStorePassword == null) add("storePassword (or MH_KEYSTORE_PASSWORD) is not set")
    if (releaseKeyAlias == null) add("keyAlias (or MH_KEY_ALIAS) is not set")
    if (releaseKeyPassword == null) add("keyPassword (or MH_KEY_PASSWORD) is not set")
}

android {
    namespace = "com.cleanai.debloat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cleanai.debloat"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "2.0.5"
        // Phones only: leaves out the x86 emulator copies of the photo AI's native libraries.
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("release") {
            if (releaseSigningProblems.isEmpty()) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimize and obfuscate. Keep rules live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            // Never falls back to the debug key: without a release key the release build stops (below).
            signingConfig = if (releaseSigningProblems.isEmpty()) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        // Duplicate license files shipped by several libraries.
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
}

// Stop a release APK/AAB build early with a clear message when the release key isn't set up.
// Only packaging tasks count, so assembleDebug, test and lint still work without a keystore.
val releasePackagingTasks = setOf("packageRelease", "packageReleaseBundle", "signReleaseBundle")
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { it.project == project && it.name in releasePackagingTasks }
    if (wantsRelease && releaseSigningProblems.isNotEmpty()) {
        throw GradleException(
            "Release signing is not configured:\n" +
                releaseSigningProblems.joinToString("\n") { "  - $it" } +
                "\nCreate android/keystore.properties (storeFile, storePassword, keyAlias, keyPassword) " +
                "or set MH_KEYSTORE, MH_KEYSTORE_PASSWORD, MH_KEY_ALIAS and MH_KEY_PASSWORD. " +
                "See android/README.md, \"Release build\"."
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Photo Enhancer: LiteRT (TensorFlow Lite) runs the open-source photo models on the phone
    implementation("com.google.ai.edge.litert:litert:1.4.0")

    // Tools: Google's QR and document scanners (camera UI runs in Play services, no camera permission)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // PDF Reader & Editor: merge, split, compress and save annotations (Apache-2.0). Viewing uses Android's PdfRenderer.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    // Real XML parser for the .docx / .xlsx tests (Android supplies its own on the phone).
    testImplementation("net.sf.kxml:kxml2:2.3.0")

    // Shizuku: runs pm/settings commands with ADB (shell) privileges, no root needed
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
