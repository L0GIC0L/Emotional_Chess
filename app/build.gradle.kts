import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.paparazzi")
}

// One codebase, three apps: Android (phone/fold), desktop (Windows/macOS/Linux) and web (WebAssembly).
kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig { outputFileName = "emotionalchess.js" }
        }
        binaries.executable()
    }

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.core:core-ktx:1.15.0")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
            // Two phones in the same room: Bluetooth / Wi-Fi Direct / shared Wi-Fi, no server.
            implementation("com.google.android.gms:play-services-nearby:19.3.0")
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
        }
        wasmJsMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
            // Online play: WebRTC between the two browsers, introduced by the free PeerJS server.
            implementation(npm("peerjs", "1.5.4"))
        }
        androidUnitTest.dependencies {
            implementation("junit:junit:4.13.2")
        }
        desktopTest.dependencies {
            implementation("junit:junit:4.13.2")
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "com.emotionalchess"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emotionalchess"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// The desktop build is kept only to run the app quickly on a PC (`./gradlew :app:run`) and for
// the off-screen render tests in src/desktopTest; it is not packaged or shipped.
compose.desktop {
    application {
        mainClass = "com.emotionalchess.MainKt"
    }
}
