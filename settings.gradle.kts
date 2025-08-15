pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "komap"

include("komap-annotations")
include("komap-processor")
include("komap-samples")