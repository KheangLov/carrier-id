plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.carrierid.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.carrierid.app"
        minSdk = 29          // Android 10: needed for the call-screening role
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing key comes from environment variables (GitHub Secrets in CI),
    // so the key never lives in this public repository. Without them the release
    // build falls back to the debug key, which still installs but changes per machine.
    val ks = System.getenv("CARRIERID_KEYSTORE")
    signingConfigs {
        if (ks != null && file(ks).exists()) {
            create("release") {
                storeFile = file(ks)
                storePassword = System.getenv("CARRIERID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CARRIERID_KEY_ALIAS") ?: "carrierid"
                keyPassword = System.getenv("CARRIERID_KEY_PASSWORD")
                    ?: System.getenv("CARRIERID_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: removes unused code and resources and optimises the rest.
            // Compose runs several times faster than in a debug build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // Installs Compose's ahead-of-time startup profiles even for APKs installed outside Play
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
