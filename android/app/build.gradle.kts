import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.securechat"
    compileSdk = libs.versions.compileSdk.get().toInt()

    // Pinned so AGP can find llvm-strip. Without an explicit ndkVersion the strip
    // task reports "missing strip tool for ABI" and packages the libsignal
    // natives unstripped — about 240 MB of debug symbols in the release APK.
    // Android Studio installs this NDK on sync if it is absent.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        // Must match the Firebase app registration in google-services.json
        // (project chatgo-364a2). The code namespace stays com.securechat.
        applicationId = "com.trisentricai.securechat"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // versionCode is monotonic: the device already carries a build with code
        // 5, and Android refuses to install anything lower unless the app is
        // uninstalled first (losing local data). Bump this before every install
        // that must replace an existing one.
        versionCode = 6
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = false
        dataBinding = false
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
            // libsignal ships its JVM (desktop) natives inside the dependency's
            // JAR resources: macOS dylibs and Windows DLLs for aarch64/amd64.
            // Only the JVM unit tests need them, and packaging them added about
            // 110 MB of binaries no Android device can load.
            excludes += "**/*.dylib"
            excludes += "**/*.dll"
        }
        jniLibs {
            // libsignal_jni_testing.so is the instrumented-test build of the
            // native library, with test-only entry points compiled in. Nothing in
            // this app loads it - production uses libsignal_jni.so - and it was
            // ~269 MB across four ABIs.
            excludes += "**/libsignal_jni_testing.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by libsignal-android: it uses java.time and other APIs that
        // need back-porting below the minSdk level it declares.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
                // androidx.lifecycle.ExperimentalLifecycleApi deliberately absent:
                // it only exists from lifecycle 2.8.0, this project pins 2.7.0, and
                // nothing uses it — the flag just emitted an "unresolved opt-in
                // marker" warning on every compilation.
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
            )
        }
    }

    // Signing material is read from android/keystore.properties, which is
    // gitignored. Keeping passwords out of this file means a repository leak
    // cannot hand over a publishable signing identity.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }
    val hasReleaseKeystore = keystorePropertiesFile.exists() &&
        keystoreProperties.getProperty("storeFile") != null

    signingConfigs {
        // Local debug convenience keystore, generated outside the repo at
        // ~/.android/securechat.keystore. Its password is deliberately trivial
        // because it only ever signs local debug builds — it must never be
        // promoted to a release identity.
        create("securechat") {
            storeFile = file("${System.getProperty("user.home")}/.android/securechat.keystore")
            storePassword = keystoreProperties.getProperty("debugStorePassword") ?: "securechat"
            keyAlias = keystoreProperties.getProperty("debugKeyAlias") ?: "securechat"
            keyPassword = keystoreProperties.getProperty("debugKeyPassword") ?: "securechat"
        }

        // Real release identity. Absent keystore.properties the release variant
        // stays unsigned on purpose: previously `release` had no signingConfig
        // at all, so the documented `bundleRelease` step silently produced an
        // unsigned AAB that could not be uploaded.
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            // No applicationIdSuffix: google-services.json registers only
            // com.trisentricai.securechat, and debug installs use the same id.
            versionNameSuffix = "-debug"
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("securechat")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            matchingFallbacks += "debug"
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    namespace = "com.securechat"
}

dependencies {
    // Android & Kotlin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.navigation)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    // OkHttp engine: unlike the HttpURLConnection-based Android engine it
    // implements WebSocketCapability, which the realtime client needs.
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.websockets)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Coil
    implementation(libs.coil.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)

    // Credential Manager (Google Sign-In)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // Signal Protocol (E2EE phase E1+: key generation and the session ratchet).
    // Official library, so the crypto is Signal's own rather than a port.
    implementation(libs.libsignal.android)

    // Back-ports the Java APIs libsignal uses onto older Android releases.
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)
}