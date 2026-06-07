plugins {
    id("yokai.android.library")
    kotlin("multiplatform")
}

android {
    namespace = "yokai.source.novel"
}

kotlin {
    androidTarget()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.core.main)
                implementation(projects.data)
                implementation(kotlinx.serialization.json)
                implementation(libs.jsoup)
                implementation(libs.okhttp)
                implementation(libs.okhttp.logging.interceptor)
                implementation(kotlinx.coroutines.core)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(kotlinx.coroutines.android)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }
}