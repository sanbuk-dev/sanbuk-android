plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // Android has org.json in the framework, so on a phone this costs nothing.
    // compileOnly keeps it out of the artifact; the JVM tests bring their own.
    compileOnly(libs.json)
    testImplementation(libs.json)
    testImplementation(kotlin("test"))
}

kotlin {
    // 1.8 bytecode: the floor Android's toolchain still desugars cleanly, and
    // nothing here needs anything newer.
    compilerOptions {
        jvmToolchain(17)
    }
    explicitApi()
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
