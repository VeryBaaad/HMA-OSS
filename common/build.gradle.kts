import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.refine)
    alias(libs.plugins.kotlin.serialization)
}

val configVerCode: Int = rootProject.extra["configVerCode"] as Int
val serviceVerCode: Int = rootProject.extra["serviceVerCode"] as Int
val minBackupVerCode: Int = rootProject.extra["minBackupVerCode"] as Int
val appPackageName: String = rootProject.extra["appPackageName"] as String
val appVerName: String = rootProject.extra["appVerName"] as String
val appVerCode: Int = rootProject.extra["appVerCode"] as Int

configure<LibraryExtension> {
    namespace = "$appPackageName.common"

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("int", "CONFIG_VERSION", configVerCode.toString())
        buildConfigField("int", "SERVICE_VERSION", serviceVerCode.toString())
        buildConfigField("int", "MIN_BACKUP_VERSION", minBackupVerCode.toString())
        buildConfigField("String", "APP_PACKAGE_NAME", "\"$appPackageName\"")
        buildConfigField("String", "APP_VERSION_NAME", "\"$appVerName\"")
        buildConfigField("int", "APP_VERSION_CODE", appVerCode.toString())
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    compileOnly(libs.dev.rikka.hidden.stub)
}
