import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.refine)
}

val appPackageName: String = rootProject.extra["appPackageName"] as String

configure<LibraryExtension> {
    namespace = "$appPackageName.xposed"

    buildFeatures {
        buildConfig = false
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.common)

    implementation(libs.androidx.annotation.jvm)
    implementation(libs.dev.rikka.hidden.compat)
    compileOnly(libs.dev.rikka.hidden.stub)

    // The Modern Xposed API is injected into the target process by the framework,
    // therefore it must never be packaged into the APK.
    compileOnly(libs.io.github.libxposed.api)
}
