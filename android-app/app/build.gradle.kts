plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes only from the environment (CI secrets). Without it the release
// build is simply unsigned, so forks and pull requests still build. The variable names are
// the same as in PULSE // BATTERY so the same CI workflow and keystore can be reused.
val releaseKeystorePath: String? = System.getenv("PULSE_KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    // The Kotlin/AIDL package stays `cyberappmanager` (history of this repo); only the
    // installed application id is the new product name. They are allowed to differ.
    namespace = "io.github.kreza6173pixel.cyberappmanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.kreza6173pixel.voidapps"
        minSdk = 26
        targetSdk = 36
        // 1.0.0 = first native release. F-Droid reads these two lines (see docs/fdroid).
        versionCode = 100
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("PULSE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PULSE_KEY_ALIAS")
                keyPassword = System.getenv("PULSE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // AGP 8.x disables AIDL generation by default. Required by the Shizuku
        // UserService interface in src/main/aidl.
        aidl = true
    }

    // The encrypted dependency block is readable only by Google. F-Droid asks for it to be
    // removed, and nothing in this app needs it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1,LGPL2.1_}"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        ignoreWarnings = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
