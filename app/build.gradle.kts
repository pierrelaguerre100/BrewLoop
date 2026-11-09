plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.doubleshotdevs.brewloop"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.doubleshotdevs.brewloop"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
        // Local-only variant that routes Firebase Auth and Realtime Database to the
        // emulators of the isolated demo-brewloop project. Never used for release.
        create("emulator") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
    }
    // Instrumented tests target debug unless run with -Pbrewloop.testBuildType=emulator,
    // which keeps emulator-only test writes away from the shared project.
    testBuildType = providers.gradleProperty("brewloop.testBuildType").getOrElse("debug")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}