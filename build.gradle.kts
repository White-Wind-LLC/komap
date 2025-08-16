plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

allprojects {
    group = "ua.wwind.komap"
    version = libsCatalog.findVersion("version").get().requiredVersion

    repositories {
        mavenCentral()
        google()
    }

    // Apply Dokka to root and all subprojects so documentation can be generated across modules
    apply(plugin = "org.jetbrains.dokka")
}

// Convenience task to generate documentation for the whole project
tasks.register("generateDocs") {
    // Dokka 2.0.0 provides a unified task `dokkaGenerate` for single- and multi-module builds
    dependsOn(":dokkaGenerate")
}