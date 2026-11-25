pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // The web build adds its own Node.js and Yarn download repositories at the root project.
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "EmotionalChess"
include(":app")
