 
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.egyvpn.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.egyvpn.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // libbox bundles large native libraries; publish separate installable APKs
    // for the two Android ABIs commonly used by phones, not a 300+ MB universal APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}


val downloadDnsttAssets by tasks.registering {
    val dnsttAar = file("libs/dnstt.aar")
    val slipstreamSo = file("src/main/jniLibs/arm64-v8a/libslipstream_client.so")
    outputs.files(dnsttAar, slipstreamSo)
    doLast {
        dnsttAar.parentFile.mkdirs()
        slipstreamSo.parentFile.mkdirs()
        if (!dnsttAar.exists()) {
            ant.get(
                src = "https://raw.githubusercontent.com/dnstt-xyz/dnstt_xyz_app/8155cde0353433646aceacca1414913fc9a304a7/android/app/libs/dnstt.aar",
                dest = dnsttAar
            )
        }
        if (!slipstreamSo.exists()) {
            ant.get(
                src = "https://raw.githubusercontent.com/dnstt-xyz/dnstt_xyz_app/8155cde0353433646aceacca1414913fc9a304a7/android/app/src/main/jniLibs/arm64-v8a/libslipstream_client.so",
                dest = slipstreamSo
            )
        }
        check(dnsttAar.length() > 1024) { "DNSTT AAR download is missing or incomplete" }
        check(slipstreamSo.length() > 1024) { "Slipstream arm64 binary download is missing or incomplete" }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadDnsttAssets)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.github.singbox-android:libbox:1.14.4")
    implementation(files("libs/dnstt.aar"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
