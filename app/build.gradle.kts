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
        versionCode = 5
        versionName = "1.0.0-PROD"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val gitSha = try {
            val p = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
            p.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) {
            "v5prod"
        }
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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

    // Room Database (v5 Double-Entry Ledger)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // Google Play In-App Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // ML Kit On-Device OCR (Rule 29: Staged Statement Scanner)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Android App Widget & Core RemoteViews
    implementation("androidx.core:core-remoteviews:1.1.0")

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
