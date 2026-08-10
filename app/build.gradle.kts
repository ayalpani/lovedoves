import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.protobuf")
}

val loveDovesProperties = Properties().apply {
    val file = rootProject.file("lovedoves.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun quoted(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.yalpani.lovedoves"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yalpani.lovedoves"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
        buildConfigField(
            "String",
            "RELAY_BOOTSTRAP_TOKEN",
            quoted(loveDovesProperties.getProperty("relay.bootstrapToken", "")),
        )
        buildConfigField("String", "FIREBASE_APP_ID", quoted(loveDovesProperties.getProperty("firebase.appId", "")))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(loveDovesProperties.getProperty("firebase.apiKey", "")))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(loveDovesProperties.getProperty("firebase.projectId", "")))
        buildConfigField("String", "FIREBASE_SENDER_ID", quoted(loveDovesProperties.getProperty("firebase.senderId", "")))
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "RELAY_URL", quoted("http://127.0.0.1:8787"))
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        getByName("release") {
            buildConfigField("String", "RELAY_BOOTSTRAP_TOKEN", quoted(""))
            buildConfigField("String", "RELAY_URL", quoted("https://lovedoves.yalpani.com"))
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs.excludes += "**/libsignal_jni_testing.so"
        resources.excludes += setOf("**/*.dylib", "**/*.dll")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-core:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-video:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.emoji2:emoji2-emojipicker:1.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.firebase:firebase-messaging:25.1.1")
    implementation("com.google.firebase:firebase-installations:19.1.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.protobuf:protobuf-javalite:4.35.1")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.guava:guava:33.4.8-android")
    implementation("me.saket.telephoto:zoomable-image:0.19.0")
    implementation("net.zetetic:sqlcipher-android:4.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.signal:libsignal-android:0.100.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    testImplementation("junit:junit:4.13.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.35.1"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
