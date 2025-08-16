@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.android.library)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
    jvm()
    androidTarget()
    js {
        nodejs()
    }
    wasmJs {
        nodejs()
    }
    linuxX64()
    linuxArm64()
    mingwX64()
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

android {
    namespace = "ua.wwind.komap.annotations"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }
    publishing {
        singleVariant("release")
    }
}

// Configure Maven Central publishing & signing
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(project.group.toString(), "komap-annotations", project.version.toString())

    pom {
        name.set("Komap Annotations")
        description.set("Annotations for Komap KSP processor")
        inceptionYear.set("2025")
        url.set("https://github.com/White-Wind-LLC/komap")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("White-Wind-LLC")
                name.set("White Wind")
                url.set("https://github.com/White-Wind-LLC")
            }
        }
        scm {
            url.set("https://github.com/White-Wind-LLC/komap")
            connection.set("scm:git:git://github.com/White-Wind-LLC/komap.git")
            developerConnection.set("scm:git:ssh://github.com/White-Wind-LLC/komap.git")
        }
    }
}

// Javadoc jar for Maven Central, based on Dokka HTML output
tasks.register<org.gradle.jvm.tasks.Jar>("javadocJar") {
    dependsOn("dokkaGeneratePublicationHtml")
    from(layout.buildDirectory.dir("dokka/html"))
    archiveClassifier.set("javadoc")
}