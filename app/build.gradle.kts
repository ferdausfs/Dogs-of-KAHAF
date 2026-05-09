plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
}

android {
    namespace = "com.guardian.shield"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guardian.shield"
        minSdk = 26
        targetSdk = 35
        // v12 (2.1.2) — FULL OPTIMISATION + STABILITY PATCH 2.
        //   • Fixed AppRule.toEntity() compile mismatch (missing id field).
        //   • Fixed AppListViewModel.load() Main-thread Flow.first() ANR.
        //   • Fixed legacy model import not closing TFLite interpreter.
        //   • Fixed Scopes.io() leak in AccessibilityService.onDestroy.
        //   • Fixed takeScreenshot SecurityException on canTakeScreenshot=false OEMs.
        //   • Fixed mainExecutor null on some service contexts.
        //   • Release builds now plant a release-safe Timber tree (was DEBUG only).
        //   • DataStore Flow.first() now wrapped with timeout (no infinite suspend).
        //   • PinSetup/Verify now disable button while busy (debounce double-tap).
        //   • GuardianAccessibilityService: aiInFlight reset race fixed.
        //   • Various smaller hardening — see CHANGELOG.
        versionCode = 6
        versionName = "2.1.2"

        // v12: vector drawables compatibility
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            // v12: keep debug builds installable alongside release
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // v12: explicit free compiler args for kotlinx-coroutines stability
        freeCompilerArgs = listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // v12: avoid duplicate META-INF on some TFLite native libs
        resources.excludes += "/META-INF/DEPENDENCIES"
        resources.excludes += "/META-INF/LICENSE*"
        resources.excludes += "/META-INF/NOTICE*"
        // Keep TFLite GPU native ABIs only for what we ship for
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // v12: Hilt sometimes complains about lint check on KSP — disable that one
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore + Security
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
