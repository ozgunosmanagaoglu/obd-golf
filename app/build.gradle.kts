plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.obdgolf"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.obdgolf"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // Two editions from the same code:
    //  - personal: the owner's Golf build with the 3D garage (never uploaded to a store)
    //  - store:    "Revora", the public build for Google Play, without car-maker branding
    // Code and assets only one edition needs live in src/personal or src/store.
    flavorDimensions += "edition"
    productFlavors {
        create("personal") {
            dimension = "edition"
            applicationId = "app.obdgolf" // unchanged, so new APKs install over the old app
            resValue("string", "app_name", "Golf OBD")
        }
        create("store") {
            dimension = "edition"
            // Fixed for good once the app is on Google Play: pick it before the first upload.
            applicationId = "app.revora"
            resValue("string", "app_name", "Revora")
        }
    }

    signingConfigs {
        // A fixed debug key in the repo, so each new APK installs over the previous one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Google Play upload key. Comes from GitHub Secrets in CI, never from the repo.
        val uploadKeystore = System.getenv("UPLOAD_KEYSTORE_FILE")
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            signingConfig = signingConfigs.findByName("upload")
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
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Only the 3D garage (personal edition) uses a WebView.
    "personalImplementation"("androidx.webkit:webkit:1.12.1")
}
