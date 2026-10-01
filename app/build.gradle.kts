plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-kapt")
}

val runNumber = (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull() ?: 1
val gitSha = (project.findProperty("GIT_SHA") as? String) ?: "localdev"

android {
    namespace = "com.personal.inout"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.personal.inout"
        minSdk = 26
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"

        vectorDrawables {
            useSupportLibrary = true
        }
        
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "androiddebug"
            keyAlias = "androiddebugkey"
            keyPassword = "androiddebug"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.android.billingclient:billing-ktx:6.2.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
