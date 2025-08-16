plugins {
    kotlin("multiplatform")
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
    jvm()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":komap-annotations"))
                implementation(libs.ksp.api)
                implementation(libs.kotlinpoet.ksp)
            }
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }
    }
}

// Configure Maven Central publishing & signing
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(project.group.toString(), "komap-processor", project.version.toString())

    pom {
        name.set("Komap Processor")
        description.set("KSP symbol processor for Komap")
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
