plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cleanai.debloat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cleanai.debloat"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.6.1"
        // Phones only: leaves out the x86 emulator copies of the photo AI's native libraries.
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // Shizuku: runs pm/settings commands with ADB (shell) privileges, no root needed
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
