plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.guardian.shield"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guardian.shield"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "2.5.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    androidResources { noCompress += "tflite" }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// ---------------------------------------------------------------------------
// FAIL-FAST RELEASE-TAG GUARD (v2.4.2 & v2.5.0 incidents, COMPILE_REVIEW_REPORT.md)
//
// GitHub releases are immutable once published. The `Create GitHub Release` step
// of .github/workflows/build.yml publishes tag `v<versionName>`; if that tag (or
// a release for it) already exists on the remote, the step fails with HTTP 422
// ("tag_name was used by an immutable release") AFTER ~4 minutes of building.
//
// This task runs as a dependency of `preReleaseBuild`, i.e. before any Kotlin
// compilation starts, and fails loudly with a fix instruction when the target
// tag is already taken. It checks the same URLs a human would:
//   - https://github.com/<repo>/releases/tag/v<version>  (200 = release exists)
//   - https://github.com/<repo>/tree/v<version>          (200 = tag exists)
// The github.com web endpoints are NOT subject to the 60 req/hour/IP rate limit
// of the unauthenticated REST API, so CI runs cannot be flaked by rate limits.
// Results are also emitted as GitHub Actions workflow commands (::error::/
// ::warning::) so they surface as check-run annotations.
//
// Escape hatch for local/offline release builds:
//     ./gradlew assembleRelease -PskipReleaseTagCheck
// ---------------------------------------------------------------------------
val checkReleaseTagAvailable by tasks.registering {
    group = "verification"
    description = "Fails the release build if the GitHub tag/release v<versionName> already exists"
    doFirst {
        if (project.hasProperty("skipReleaseTagCheck")) {
            logger.warn("::warning::checkReleaseTagAvailable SKIPPED via -PskipReleaseTagCheck")
            return@doFirst
        }
        val versionName = android.defaultConfig.versionName
        val tag = "v$versionName"
        val repo = System.getenv("GITHUB_REPOSITORY") ?: "ferdausfs/Dogs-of-KAHAF"
        fun httpGet(url: String): Int = try {
            val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "GuardianShieldReleaseTagGuard")
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.responseCode
        } catch (e: Exception) {
            logger.error("checkReleaseTagAvailable: HTTP failure for $url: ${e.message}")
            -1
        }
        val relCode = httpGet("https://github.com/$repo/releases/tag/$tag")
        val refCode = httpGet("https://github.com/$repo/tree/$tag")
        when {
            relCode == 200 || refCode == 200 -> {
                logger.error(
                    "::error::RELEASE TAG COLLISION: $tag already exists on GitHub " +
                        "(release page HTTP $relCode, tree page HTTP $refCode). Published GitHub " +
                        "releases are immutable; the Create GitHub Release step would fail with " +
                        "HTTP 422. Bump versionName in app/build.gradle.kts, commit, and push again. " +
                        "(Local-only builds may bypass with -PskipReleaseTagCheck.)"
                )
                throw GradleException(
                    "RELEASE TAG COLLISION: $tag already exists on GitHub " +
                        "(release page HTTP $relCode, tree page HTTP $refCode). " +
                        "Bump versionName in app/build.gradle.kts and push again."
                )
            }
            relCode == 404 && refCode == 404 -> logger.lifecycle(
                "::warning::Release tag check: $tag is free (release page 404, tree page 404) - proceeding."
            )
            else -> {
                logger.error(
                    "::error::Could not verify release tag availability for $tag " +
                        "(release page HTTP $relCode, tree page HTTP $refCode). Aborting the " +
                        "release build rather than risk publishing over an existing immutable " +
                        "release. Re-run, or bypass local builds with -PskipReleaseTagCheck."
                )
                throw GradleException(
                    "Could not verify release tag availability for $tag " +
                        "(release page HTTP $relCode, tree page HTTP $refCode)."
                )
            }
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(checkReleaseTagAvailable)
}