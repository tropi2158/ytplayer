import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.example.playlist"
version = "1.1.2"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // 유튜브 오디오(m4a)를 표준 형태로 다시 묶어 재생 호환성을 높임
    implementation("org.mp4parser:isoparser:1.9.56")
    implementation("org.mp4parser:muxer:1.9.56")

    // 오디오 재생 (JavaFX Media, Windows용)
    val fx = "21.0.5"
    listOf("base", "graphics", "media").forEach { implementation("org.openjfx:javafx-$it:$fx:win") }
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.example.playlist.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx1g")
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Playlist"
            packageVersion = "1.1.2"
            description = "YouTube playlist music player"
            vendor = "tropicalgd2158"
            includeAllModules = true
            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
                menuGroup = "Playlist"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f1c2a2e-4b7d-4a8e-9b51-3d7e2f0c9a11"
            }
        }
    }
}
