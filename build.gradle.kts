import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.gradle.versions)
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:${kotlinx.versions.kotlin.get()}")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:${kotlinx.versions.kotlin.get()}")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${kotlinx.versions.kotlin.get()}")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinx.versions.kotlin.get()}")
        }
    }
}

tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java).configure {
    rejectVersionIf {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { candidate.version.uppercase(Locale.ROOT).contains(it) }
        val regex = "^[0-9,.v-]+(-r)?$".toRegex()
        val isStable = stableKeyword || regex.matches(candidate.version)
        isStable.not()
    }
    // optional parameters
    checkForGradleUpdate = true
    outputFormatter = "json"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
