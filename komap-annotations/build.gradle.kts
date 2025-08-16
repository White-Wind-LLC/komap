plugins {
    kotlin("multiplatform")
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(8)
    jvm()
    js {
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
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("src/main/kotlin")
        }
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