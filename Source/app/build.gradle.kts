plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

android {
    namespace = "me.syncrex.goldv2ray"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "me.syncrex.goldv2ray"
        minSdk = 23
        targetSdk = 35
        versionCode = 95
        versionName = "95:1.10.24"
        multiDexEnabled = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    // V2RayNG /////////////////////////////////////////////////////////////////////////////////////
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.github.jorgecastilloprz:fabprogresscircle:1.01@aar")
    implementation("com.github.GrenderG:Toasty:1.5.2")
    implementation("com.blacksquircle.ui:editorkit:2.9.0")
    implementation("com.blacksquircle.ui:language-base:2.9.0")
    implementation("com.blacksquircle.ui:language-json:2.9.0")
    implementation("io.github.g00fy2.quickie:quickie-bundled:1.11.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.work:work-multiprocess:2.11.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Gold V2Ray //////////////////////////////////////////////////////////////////////////////////
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-inappmessaging-display")
    implementation("com.google.firebase:firebase-messaging:25.0.1")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("com.github.GoodieBag:Pinview:v1.5")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("com.codesgood:justifiedtextview:2.0.1")
    implementation("com.github.ybq:Android-SpinKit:1.4.0")
    implementation("com.getkeepsafe.relinker:relinker:1.4.5")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    // Special Version /////////////////////////////////////////////////////////////////////////////
    implementation("com.tencent:mmkv:1.3.14")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    // Ads & Mediation /////////////////////////////////////////////////////////////////////////////
    implementation("com.google.android.gms:play-services-ads:24.7.0")
    implementation("com.google.ads.mediation:unity:4.16.3.0")
    implementation("com.google.ads.mediation:vungle:7.6.0.0")
    implementation("com.google.ads.mediation:ironsource:9.0.0.1")
    implementation("com.google.ads.mediation:applovin:13.5.0.0")
    implementation("com.google.ads.mediation:mintegral:16.10.11.0")
    implementation("com.unity3d.ads:unity-ads:4.16.3")
    implementation("com.vungle:vungle-ads:7.6.0")
    implementation("com.appnext.sdk:banners:2.7.6.473")
    implementation("com.appnext.sdk:ads:2.7.6.473")
}