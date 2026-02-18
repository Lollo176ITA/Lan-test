import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-transfer"))
    implementation(project(":core-sync"))
    implementation(project(":core-media"))
    implementation(project(":core-network"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

compose.desktop {
    application {
        mainClass = "com.lanshare.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "LanShare"
            // DMG packaging rejects 0.x.y (major must be > 0), keep native installer version >= 1.
            packageVersion = "1.0.0"
            description = "LAN desktop sharing and streaming"
            vendor = "LanShare"
            // Reduce runtime-missing startup issues on packaged Windows builds.
            includeAllModules = true

            macOS {
                packageVersion = "1.0.0"
                dmgPackageVersion = "1.0.0"
            }
        }
    }
}
