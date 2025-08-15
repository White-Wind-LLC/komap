plugins {
    kotlin("multiplatform")
}

kotlin {
    explicitApi()
    jvmToolchain(8)
    jvm()
    js {
        nodejs()
    }
    linuxX64()
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
