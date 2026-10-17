plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.dzynecode.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dzynecode.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.2.3"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            // A small, useful set of pure-Python packages. Android-native wheels
            // are not universally available, so this is intentionally curated.
            install("requests==2.32.4")
            install("beautifulsoup4==4.13.4")
            install("rich==14.1.0")
            install("sympy==1.14.0")
            install("packaging==24.2")
        }
    }
}
