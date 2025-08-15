plugins {
    kotlin("multiplatform")
}

kotlin {
    explicitApi()
    jvmToolchain(8)
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
