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
    val upstreamDnsttAar = file("libs/dnstt-upstream.aar")
    val dnsttAar = file("libs/dnstt.aar")
    val upstreamMasterAar = file("libs/masterdnsvpn-upstream.aar")
    val masterAar = file("libs/masterdnsvpn.aar")
    val slipstreamSo = file("src/main/jniLibs/arm64-v8a/libslipstream_client.so")
    outputs.files(upstreamDnsttAar, dnsttAar, upstreamMasterAar, masterAar, slipstreamSo)
    doLast {
        dnsttAar.parentFile.mkdirs()
        slipstreamSo.parentFile.mkdirs()
        if (!upstreamDnsttAar.exists()) {
            URL("https://raw.githubusercontent.com/dnstt-xyz/dnstt_xyz_app/8155cde0353433646aceacca1414913fc9a304a7/android/app/libs/dnstt.aar").openStream().use { input ->
                upstreamDnsttAar.outputStream().use { output -> input.copyTo(output) }
            }
        }
        // Both libbox and the upstream DNSTT AAR bundle gomobile go.Seq runtime classes.
        // Keep one copy (the one shipped with libbox) to avoid duplicate DEX classes.
        if (!dnsttAar.exists() || dnsttAar.lastModified() < upstreamDnsttAar.lastModified()) {
            val filteredClasses = file("$buildDir/dnstt-aar-filter/classes.jar")
            filteredClasses.parentFile.mkdirs()
            ZipFile(upstreamDnsttAar).use { aar ->
                val classesEntry = aar.getEntry("classes.jar") ?: error("DNSTT AAR has no classes.jar")
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
                ZipOutputStream(FileOutputStream(dnsttAar)).use { zout ->
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
        check(upstreamDnsttAar.length() == 7274524L) { "DNSTT AAR size mismatch; pinned upstream asset may have changed" }
        check(dnsttAar.length() > 1024L) { "Filtered DNSTT AAR is missing or incomplete" }
        check(slipstreamSo.length() == 5923312L) { "Slipstream arm64 binary size mismatch; pinned upstream asset may have changed" }
        // MasterDnsVPN gomobile AAR is built from its pinned Go source in CI.
        check(upstreamMasterAar.exists() && upstreamMasterAar.length() > 1024L) {
            "MasterDnsVPN AAR missing; run the Go/gomobile build step before Gradle"
        }
        if (!masterAar.exists() || masterAar.lastModified() < upstreamMasterAar.lastModified()) {
            val filteredMasterClasses = file("$buildDir/masterdns-aar-filter/classes.jar")
            filteredMasterClasses.parentFile.mkdirs()
            ZipFile(upstreamMasterAar).use { aar ->
                val classesEntry = aar.getEntry("classes.jar") ?: error("MasterDnsVPN AAR has no classes.jar")
                ZipInputStream(aar.getInputStream(classesEntry)).use { zin ->
                    ZipOutputStream(FileOutputStream(filteredMasterClasses)).use { zout ->
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
                ZipOutputStream(FileOutputStream(masterAar)).use { zout ->
                    val entries = aar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        zout.putNextEntry(ZipEntry(entry.name))
                        if (entry.name == "classes.jar") {
                            filteredMasterClasses.inputStream().use { it.copyTo(zout) }
                        } else {
                            aar.getInputStream(entry).use { it.copyTo(zout) }
                        }
                        zout.closeEntry()
                    }
                }
            }
        }
        check(masterAar.length() > 1024L) { "Filtered MasterDnsVPN AAR is missing or incomplete" }
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
    implementation(files("libs/masterdnsvpn.aar"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
