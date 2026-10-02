plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "com.personal.inout"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.personal.inout"
        minSdk = 26
        targetSdk = 35

        // Injected dynamically by GitHub Actions workflow, fallback for local dev
        val passedVersionCode = project.findProperty("VERSION_CODE")?.toString()?.toIntOrNull() ?: 1
        val passedGitSha = project.findProperty("GIT_SHA")?.toString()?.trim() ?: "localdev"

        versionCode = passedVersionCode
        versionName = "1.0.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Rule: Expose exact Git SHA to AppUpdateEngine without breaking in-app updates
        buildConfigField("String", "GIT_SHA", "\"$passedGitSha\"")
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("debug.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "androiddebug"
                keyAlias = "androiddebugkey"
                keyPassword = "androiddebug"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // Google Play In-App Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // ML Kit On-Device OCR (Rule 29: Statement & Receipt Scanner)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // OkHttp (Required for AppUpdateEngine GitHub API rolling release downloads)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Android App Widget & Core RemoteViews
    implementation("androidx.core:core-remoteviews:1.1.0")

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
