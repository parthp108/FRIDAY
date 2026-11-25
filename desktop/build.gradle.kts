plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
                implementation("org.slf4j:slf4j-simple:2.0.12")
                implementation("cafe.adriel.voyager:voyager-navigator:1.0.0")
                implementation("cafe.adriel.voyager:voyager-screenmodel:1.0.0")
                implementation("cafe.adriel.voyager:voyager-tab-navigator:1.0.0")
                implementation("cafe.adriel.voyager:voyager-koin:1.0.0")
                implementation("io.insert-koin:koin-core:3.5.3")
                implementation("com.github.kwhat:jnativehook:2.2.2")
                implementation("net.java.dev.jna:jna:5.14.0")
                implementation("net.java.dev.jna:jna-platform:5.14.0")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.health.friday.desktop.MainKt"
        javaHome = File("C:/Users/parth/.jdks/jbr-25.0.4.1").takeIf { it.exists() }?.absolutePath ?: System.getenv("JAVA_HOME")
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            "-XX:+UseZGC",
            "-Xmx2G",
            "-Xms256M"
        )

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "FRIDAY"
            packageVersion = "2.0.0"
            description = "FRIDAY ECo System Daemon & Control Center"
            copyright = "© 2026 Health Ecosystem"
            vendor = "FRIDAY"

            windows {
                menuGroup = "FRIDAY"
                shortcut = true
                menu = true
                upgradeUuid = "8c878992-b5a8-36ac-f783-6c6ce63e4f4a"
            }
        }
    }
}

tasks.withType<JavaExec> {
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-XX:+UseZGC",
        "-Xmx2G",
        "-Xms256M"
    )
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("**/desktop.ini")
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("**/desktop.ini")
}

// Automatically kill any running Desktop process (FRIDAY.exe or java process holding desktop build files)
// BEFORE any compilation, packaging, or run task starts so Windows releases file locks.
val killRunningDesktopProcess = tasks.register("killRunningDesktopProcess") {
    doLast {
        runCatching {
            val script = """
                Get-Process -ErrorAction SilentlyContinue | Where-Object {
                    (${'$'}_.ProcessName -eq 'FRIDAY' -or ${'$'}_.ProcessName -eq 'java' -or ${'$'}_.ProcessName -eq 'javaw') -and
                    (${'$'}_.ProcessName -ne 'ollama') -and
                    ((${'$'}_.Path -and ${'$'}_.Path -like '*FRIDAY*') -or (${'$'}_.CommandLine -and ${'$'}_.CommandLine -like '*friday*'))
                } | Stop-Process -Force -ErrorAction SilentlyContinue
            """.trimIndent()
            ProcessBuilder("powershell", "-NoProfile", "-Command", script).start().waitFor()
        }
    }
}

tasks.matching {
    it.name != "killRunningDesktopProcess" && (
        it.name.startsWith("compile") ||
        it.name.startsWith("package") ||
        it.name.contains("Distributable") ||
        it.name.contains("Run") ||
        it.name == "run"
    )
}.configureEach {
    mustRunAfter(killRunningDesktopProcess)
    dependsOn(killRunningDesktopProcess)
}

// Configure Android Studio's default `desktopRun` task so clicking Run in IDE compiles, updates FRIDAY.exe, launches it!
tasks.matching { it.name == "runDistributable" }.configureEach {
    dependsOn("createDistributable")
    actions.clear()

    val exeProvider = layout.buildDirectory.file("compose/binaries/main/app/FRIDAY/FRIDAY.exe")
    inputs.file(exeProvider)

    doLast {
        val exeFile = exeProvider.get().asFile
        if (exeFile.exists()) {
            val appDir = exeFile.parentFile
            println("=== Build complete! Launching updated FRIDAY.exe on laptop ===")
            ProcessBuilder("cmd", "/c", "start", "", exeFile.name)
                .directory(appDir)
                .start()
        } else {
            println("Error: FRIDAY.exe was not found at ${exeFile.absolutePath}")
        }
    }
}

abstract class LaunchExeTask : DefaultTask() {
    @get:InputFile
    abstract val exeFile: RegularFileProperty

    @TaskAction
    fun launch() {
        val source = exeFile.get().asFile
        if (source.exists()) {
            val appDir = source.parentFile
            println("=== Build complete! Launching updated FRIDAY.exe from ${appDir.absolutePath} ===")
            ProcessBuilder("cmd", "/c", "start", "", source.name)
                .directory(appDir)
                .start()
        } else {
            println("Error: FRIDAY.exe was not found at ${source.absolutePath}")
        }
    }
}

tasks.register<LaunchExeTask>("runExe") {
    group = "application"
    description = "Builds/updates the native FRIDAY.exe executable and launches it on your laptop"
    dependsOn("createDistributable")
    exeFile.set(layout.buildDirectory.file("compose/binaries/main/app/FRIDAY/FRIDAY.exe"))
}
