import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "be.vandeas"
version = "1.1.0"

kotlin {
    android {
        namespace = "be.vandeas.kalendar.kit"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
//    iosX64()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.androidx.core.ktx)
            }
        }

        val commonMain by getting {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.ui.tooling.preview)

                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "kalendar-kit", version.toString())

    pom {
        name = "KalendarKit"
        description = """
            KalendarKit is a Compose Multiplatform library designed to simplify the presentation of events for users to add. Using native APIs, KalendarKit allows developers to present a modal EventKit interface on 
            iOS and open the default calendar application on Android, making it easier to add events.
        """.trimIndent()
        inceptionYear = "2025"
        url = "https://github.com/LotuxPunk/KalendarKit/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "lotuxpunk"
                name = "Clément VANDENDAELEN"
                url = "https://vandeas.be"
            }
        }
        scm {
            url = "https://github.com/LotuxPunk/KalendarKit/"
            connection = "scm:git:git://github.com/LotuxPunk/KalendarKit.git"
            developerConnection = "scm:git:ssh://git@github.com:LotuxPunk/KalendarKit.git"
        }
    }
}
