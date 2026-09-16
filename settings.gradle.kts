pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal(); google() }
    resolutionStrategy { eachPlugin {
        when (requested.id.id) {
            "org.jetbrains.kotlin.jvm" -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            "org.jetbrains.kotlin.plugin.compose" -> useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
            "org.jetbrains.compose" -> useModule("org.jetbrains.compose:compose-gradle-plugin:${requested.version}")
        }
    } }
}
rootProject.name = "HermesDesktop"
