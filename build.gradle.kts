import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("org.jetbrains.compose") version "1.10.1"
}
repositories { mavenCentral(); google() }
kotlin { jvmToolchain(21) }
val desktopTarget = providers.gradleProperty("desktopTarget").orNull
val windowsTarget = desktopTarget == "windows-x64"
val macArmTarget = desktopTarget == "macos-arm64"
dependencies {
    implementation(when {windowsTarget->"org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.10.1";macArmTarget->"org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.10.1";else->compose.desktop.currentOs})
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("com.ibm.icu:icu4j:76.1")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("org.apache.poi:poi-scratchpad:5.5.1")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    val fxPlatform = when {
        macArmTarget -> "mac-aarch64"
        windowsTarget || System.getProperty("os.name").startsWith("Windows") -> {
            check(windowsTarget || System.getProperty("os.arch") in listOf("amd64", "x86_64")) { "Windows builds require an x64 JDK 21. Windows ARM64 and 32-bit Java are not supported by this release." }
            "win"
        }
        System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "mac-aarch64" else "mac"
        else -> "linux"
    }
    // Each JavaFX module is explicit. Avoid Maven's host classifier pulling in a second platform.
    listOf("base", "graphics", "controls", "media", "web", "swing").forEach { implementation("org.openjfx:javafx-$it:21.0.8:$fxPlatform") { isTransitive = false } }
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
}
compose.desktop {
    application {
        mainClass = "com.qingyu.hermescompanion.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx1536m")
        if (System.getProperty("os.name").startsWith("Mac")) jvmArgs += listOf("-Dapple.laf.useScreenMenuBar=true","-Dapple.awt.enableTemplateImages=true")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Hermes"
            packageVersion = "1.8.8"
            vendor = "Jerome"
            description = "Hermes desktop companion"
            includeAllModules = true
            appResourcesRootDir.set(project.layout.projectDirectory.dir("packaging/app-resources"))
            windows {
                iconFile.set(file("packaging/Hermes.ico"))
                perUserInstall = true
                dirChooser = true
                menuGroup = "Hermes"
                shortcut = true
                menu = true
                upgradeUuid = "b41c729f-72a4-4a43-a028-c7525e03f0de"
            }
            macOS {
                if (file("packaging/Hermes.icns").exists()) iconFile.set(file("packaging/Hermes.icns"))
                bundleID = "com.qingyu.hermes.desktop"
                minimumSystemVersion = "13.0"
                dockName = "Hermes"
                infoPlist {
                    extraKeysRawXml = "<key>HermesBuildRevision</key><string>ime-compat-1.8.8</string><key>NSMicrophoneUsageDescription</key><string>Hermes 使用麦克风进行语音输入和对话。</string><key>NSSpeechRecognitionUsageDescription</key><string>Hermes 将录音识别为对话文字。</string>"
                }
            }
        }
    }
}

// Portable distribution: jpackage app-image needs no WiX and bundles its own Java runtime.
tasks.register<Zip>("packageWindowsPortable") {
    dependsOn("createDistributable")
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
    archiveFileName.set("Hermes-Windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("windows"))
    from(layout.buildDirectory.dir("compose/binaries/main/app/Hermes")) { into("Hermes") }
    doFirst {
        check(layout.buildDirectory.file("compose/binaries/main/app/Hermes/Hermes.exe").get().asFile.isFile) { "Hermes.exe was not generated; the portable package was not created." }
    }
}
tasks.test {
    maxHeapSize = "1g"
    // Cloud builds exercise offscreen Compose; desktop Robot tests need a local interactive session.
    systemProperty("java.awt.headless", providers.gradleProperty("headlessTests").getOrElse("false"))
}

// Assemble JVM code with genuine Windows native libraries, without invoking cross-OS jpackage.
tasks.register<Sync>("stageWindowsApplication") {
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("windows-staging/app"))
    from(tasks.jar) { rename { "hermes-desktop.jar" } }
    from(configurations.runtimeClasspath)
    doFirst {
        check(windowsTarget || System.getProperty("os.name").startsWith("Windows")) { "Pass -PdesktopTarget=windows-x64 to stage Windows libraries." }
        check(configurations.runtimeClasspath.get().files.none { it.name.contains("-linux") || it.name.contains("-macos") || it.name.contains("-mac-") }) { "A foreign platform library was found in the Windows classpath." }
    }
}

// Platform-independent bytecode plus only Apple Silicon native libraries.
tasks.register<Sync>("stageMacApplication") {
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("macos-staging/app"))
    from(tasks.jar) {rename {"hermes-desktop.jar"}}
    from(configurations.runtimeClasspath)
    doFirst {
        check(macArmTarget) {"Pass -PdesktopTarget=macos-arm64 to stage Apple Silicon libraries."}
        check(configurations.runtimeClasspath.get().files.none {it.name.contains("-linux")||it.name.contains("-win")||it.name.contains("-macos-x64")}) {"Foreign platform dependency in Apple Silicon staging."}
    }
}

tasks.register<JavaExec>("renderPreviews") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.qingyu.hermescompanion.desktop.RenderPreviews")
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
}

tasks.register<JavaExec>("render170Previews") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.qingyu.hermescompanion.desktop.Render170Previews")
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
}

// Start Mockito with the test JVM; no runtime self-attach is required.
val mockitoAgent by configurations.creating
dependencies { mockitoAgent("org.mockito:mockito-core:5.14.2") { isTransitive = false } }
tasks.test { doFirst { jvmArgs("-javaagent:${mockitoAgent.asPath}") } }

tasks.register<JavaExec>("render180Previews") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.qingyu.hermescompanion.desktop.Render180Previews")
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
}
tasks.register("writeVerificationClasspath") {
    dependsOn(tasks.testClasses)
    doLast {
        layout.buildDirectory.file("verification180/classpath.txt").get().asFile.apply {
            parentFile.mkdirs(); writeText(sourceSets.test.get().runtimeClasspath.asPath)
        }
    }
}

tasks.register<JavaExec>("renderComposer186") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.qingyu.hermescompanion.desktop.RenderComposer186")
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
}

tasks.register<Test>("nativeImeTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*ComposerNativeImeTest") }
    systemProperty("java.awt.headless", "false")
    maxHeapSize = "1g"
}

tasks.register<Test>("systemImeTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*ComposerSystemImeTest") }
    systemProperty("java.awt.headless", "false")
    systemProperty("hermes.systemImeTest", "true")
    maxHeapSize = "1g"
}
