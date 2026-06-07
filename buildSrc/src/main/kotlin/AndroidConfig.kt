import org.gradle.api.JavaVersion as GradleJavaVersion

object AndroidConfig {
    const val COMPILE_SDK = 36
    const val MIN_SDK = 23
    const val TARGET_SDK = 36
    const val NDK = "28.0.13004108"
    val JavaVersion = GradleJavaVersion.VERSION_17
}
