buildscript {
    dependencies {
        classpath ("com.google.gms:google-services:4.4.4")
        classpath ("com.google.firebase:firebase-crashlytics-gradle:3.0.6")
        classpath ("com.google.firebase:perf-plugin:2.0.2")
    }
}
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
}