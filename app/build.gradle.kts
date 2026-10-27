import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.fitness"
    compileSdk = 36

    defaultConfig {
        applicationId = "tw.edu.fju.myfitnessapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ★★★ 關鍵修正：讀取 local.properties 的正確寫法 ★★★
        val localProperties = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localProperties.load(localFile.inputStream())
        }

        // 這裡會去抓 local.properties 裡面的 geminiApiKey
        // 如果讀不到，就會是空字串，導致連不上 Gemini
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY") ?: ""}\"")

        // URL 比較不敏感，如果放在 gradle.properties 可以用 project.findProperty，或者統一都放 local.properties
        buildConfigField("String", "GEMINI_API_URL", "\"${localProperties.getProperty("geminiApiUrl") ?: ""}\"")
        buildConfigField("String", "EDAMAM_APP_ID", "\"${localProperties.getProperty("EDAMAM_APP_ID") ?: ""}\"")
        buildConfigField("String", "EDAMAM_APP_KEY", "\"${localProperties.getProperty("EDAMAM_APP_KEY") ?: ""}\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }


    signingConfigs {
        create("release") {
            storeFile = file("release/my-release-key")
            storePassword = project.findProperty("storePassword")?.toString() ?: "defaultPassword"
            keyAlias = project.findProperty("keyAlias")?.toString() ?: "defaultAlias"
            keyPassword = project.findProperty("keyPassword")?.toString() ?: "defaultPassword"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi"
        )
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Google Play Services Ads (16KB-compatible)
    implementation("com.google.android.gms:play-services-ads:24.2.0")

    // Firebase (16KB-compatible BOM)
    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-inappmessaging-display")
    implementation("com.google.firebase:firebase-analytics")

    implementation("com.google.firebase:firebase-messaging")

    // Auth & AI (16KB-compatible)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")

    // Networking & Utils (16KB-compatible)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.guava:guava:33.4.0-android")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")

    // RevenueCat (16KB-compatible)
    implementation("com.revenuecat.purchases:purchases:8.12.1")

    // Coil (16KB-compatible)
    implementation("io.coil-kt:coil-compose:2.7.0")


    // AndroidX & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Adaptive layout - WindowSizeClass for phone/tablet/foldable
    implementation("androidx.compose.material3:material3-window-size-class:1.3.2")
    implementation("androidx.window:window:1.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // ML Kit & Camera (16KB-compatible)
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    // Other UI
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("com.google.zxing:core:3.5.3")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.intuit.sdp:sdp-android:1.0.6")
    implementation("com.makeramen:roundedimageview:2.3.0")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}