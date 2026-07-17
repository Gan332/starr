import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Release signing config.
// Reads credentials from environment variables (CI) or local.properties (local dev):
//   KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
// If any are missing, the release build falls back to unsigned APK.
val keystoreFile = System.getenv("KEYSTORE_FILE")
val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
val keyAlias = System.getenv("KEY_ALIAS")
val keyPassword = System.getenv("KEY_PASSWORD")
val hasSigningConfig = keystoreFile != null && File(keystoreFile).exists()

android {
    namespace = "com.auth2fa.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.auth2fa.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = File(keystoreFile!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

// ─── Rust build integration ───
// Run: ./gradlew :app:buildRust
// Or:  ./gradlew :app:assembleDebug (will build Rust first)
tasks.register<Exec>("buildRust") {
    workingDir = file("${rootProject.projectDir}/rust")
    val ndkHome = System.getenv("NDK_HOME") ?: System.getenv("ANDROID_HOME")?.let { androidHome ->
        // Scan $ANDROID_HOME/ndk/ and pick the highest installed version.
        val ndkRoot = File("$androidHome/ndk")
        if (ndkRoot.exists() && ndkRoot.isDirectory) {
            ndkRoot.listFiles()
                ?.filter { it.isDirectory }
                ?.maxByOrNull { it.name }
                ?.path
        } else null
    }
    if (ndkHome != null) {
        environment("NDK_HOME", ndkHome)
    }
    val osName = System.getProperty("os.name").lowercase()
    val isWindows = osName.contains("windows")
    if (isWindows) {
        commandLine("powershell", "-ExecutionPolicy", "Bypass", "-File", "build-android.ps1")
    } else {
        commandLine("bash", "build-android.sh")
    }
}

tasks.named("preBuild") {
    dependsOn("buildRust")
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // CameraX for QR scanning
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // ML Kit for QR code detection (on-device, no Firebase needed)
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Biometric authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
