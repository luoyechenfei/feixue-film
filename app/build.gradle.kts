import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.feixue.tv"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.feixue.tv"
        minSdk = 21
        targetSdk = 35
        versionCode = 10003
        versionName = "1.0.0.3"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("io.coil-kt:coil:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")

    implementation("com.google.zxing:core:3.5.3")
    implementation("tv.danmaku.ijk.media:ijkplayer-java:0.8.8")
    implementation("tv.danmaku.ijk.media:ijkplayer-armv7a:0.8.8")
    implementation("tv.danmaku.ijk.media:ijkplayer-arm64:0.8.8")
    implementation("tv.danmaku.ijk.media:ijkplayer-x86_64:0.8.8")

    testImplementation("junit:junit:4.13.2")
}
