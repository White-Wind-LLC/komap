plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

allprojects {
    group = "ua.wwind.komap"
    version = libsCatalog.findVersion("version").get().requiredVersion

    repositories {
        mavenCentral()
        google()
    }
}