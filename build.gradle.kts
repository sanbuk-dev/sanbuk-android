plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// Group and version of everything published from here. The artifact the doc
// promises publishers is ir.sanbuk:sdk-android; core ships beside it.
allprojects {
    group = "ir.sanbuk"
    version = "0.1.0"
}
