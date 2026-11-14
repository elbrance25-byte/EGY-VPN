import java.net.URL
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

 
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
    val upstreamEngineAar = file("libs/egyengine-upstream.aar")
    val engineAar = file("libs/egyengine.aar")
    val slipstreamSo = file("src/main/jniLibs/arm64-v8a/libslipstream_client.so")
    outputs.files(upstreamEngineAar, engineAar, slipstreamSo)
    doLast {
        engineAar.parentFile.mkdirs()
        slipstreamSo.parentFile.mkdirs()
        check(upstreamEngineAar.exists() && upstreamEngineAar.length() > 1024L) {
            "Unified DNSTT/MasterDnsVPN AAR missing; build it with gomobile before Gradle"
        }
        // libbox already supplies go.Seq; keep the single shared runtime classes once.
        if (!engineAar.exists() || engineAar.lastModified() < upstreamEngineAar.lastModified()) {
            val filteredClasses = file("$buildDir/egyengine-aar-filter/classes.jar")
            filteredClasses.parentFile.mkdirs()
            ZipFile(upstreamEngineAar).use { aar ->
                val classesEntry = aar.getEntry("classes.jar") ?: error("Unified engine AAR has no classes.jar")
                ZipInputStream(aar.getInputStream(classesEntry)).use { zin ->
                    ZipOutputStream(FileOutputStream(filteredClasses)).use { zout ->
                        while (true) {
                            val entry = zin.nextEntry ?: break
                            if (!entry.name.startsWith("go/")) {
                                zout.putNextEntry(ZipEntry(entry.name))
                                zin.copyTo(zout)
                                zout.closeEntry()
                            }
                        }
                    }
                }
                ZipOutputStream(FileOutputStream(engineAar)).use { zout ->
                    val entries = aar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        zout.putNextEntry(ZipEntry(entry.name))
                        if (entry.name == "classes.jar") {
                            filteredClasses.inputStream().use { it.copyTo(zout) }
                        } else {
                            aar.getInputStream(entry).use { it.copyTo(zout) }
                        }
                        zout.closeEntry()
                    }
                }
            }
        }
        if (!slipstreamSo.exists()) {
            URL("https://raw.githubusercontent.com/dnstt-xyz/dnstt_xyz_app/8155cde0353433646aceacca1414913fc9a304a7/android/app/src/main/jniLibs/arm64-v8a/libslipstream_client.so").openStream().use { input ->
                slipstreamSo.outputStream().use { output -> input.copyTo(output) }
            }
        }
        check(engineAar.length() > 1024L) { "Filtered unified engine AAR is missing or incomplete" }
        check(slipstreamSo.length() == 5923312L) { "Slipstream arm64 binary size mismatch; pinned upstream asset may have changed" }
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
    implementation(files("libs/egyengine.aar"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
