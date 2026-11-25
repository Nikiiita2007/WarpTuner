plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.warptuner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.warptuner"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "4.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes only from CI environment variables (see release.yml).
    // Without them assembleRelease produces an unsigned APK, as before.
    val signingStore = System.getenv("SIGNING_STORE_FILE")
    if (signingStore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(signingStore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (signingStore != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.amneziawg.android)
    implementation(libs.play.services.code.scanner)
}
// The lint tool runs from its own configuration, outside the buildscript classpath, so it
// needs the same patched pins as the root build.gradle.kts. Lint never ships in the APK.
dependencies {
    constraints {
        listOf(
            "org.bouncycastle:bcprov-jdk18on:1.87",
            "org.bouncycastle:bcpkix-jdk18on:1.87",
            "org.bouncycastle:bcutil-jdk18on:1.87",
            "com.google.guava:guava:33.8.0-jre",
            "org.bitbucket.b_c:jose4j:0.9.7",
            "org.jdom:jdom2:2.0.6.1",
            "org.apache.commons:commons-lang3:3.22.0",
            "org.apache.httpcomponents:httpclient:4.5.14",
        ).forEach { add("androidLintTool", it) { because("patched build-tooling advisory") } }
    }
}
