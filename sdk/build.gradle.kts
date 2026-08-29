plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ir.sanbuk.sdk"
    compileSdk = 34

    defaultConfig {
        // API 21 covers essentially every phone still receiving traffic in
        // Iran, and nothing here needs anything newer.
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
        }
    }

    // core is compiled INTO the aar rather than depended on as a module.
    //
    // A gradle dependency is fine for a publisher whose build can reach Maven
    // Central. Plenty of ours cannot — a Unity plugin in particular ships as a
    // dropped-in .aar with no resolution step at all — and an aar that throws
    // NoClassDefFoundError on first launch is worse than no aar. So the shared
    // sources are part of this artifact, and :core stays a real module purely
    // so the same code is compiled and unit-tested on a plain JVM.
    sourceSets["main"].kotlin.srcDir("../core/src/main/kotlin")

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // No dependencies at all, compile-time included: org.json is part of
    // android.jar, so the shared sources compile against the platform itself.
    // The :core module declares it compileOnly because a plain JVM has no
    // such thing — the same code, two ways of finding the same classes.
    testImplementation(kotlin("test"))
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

kotlin {
    explicitApi()
}
