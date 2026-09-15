import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    // Kotlin support is built into AGP 9, applying org.jetbrains.kotlin.android here is
    // an error since AGP 9.0.
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.nav.safeargs.kotlin) apply false
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val out = providers.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
    }
    return out.standardOutput.asText.get().trim()
}

val localProperties = Properties()
localProperties.load(file("local.properties").inputStream())
val ciBuild = providers.environmentVariable("CI").isPresent
val officialBuild = localProperties.getProperty("officialBuild", "false") == "true"
val localBuild = localProperties.getProperty("localBuild", "false") == "true"

fun getUncommittedSuffix(): String {
    if (officialBuild) return ""

    if (ciBuild) {
        val headRefVal = providers.environmentVariable("GITHUB_HEAD_REF").orElse("HEAD").get()
        return "-$headRefVal"
    }

    var returnedVal = ""

    try {
        val branch = "git rev-parse --abbrev-ref HEAD".execute().split("/").last()
        if (branch != "master") {
            returnedVal += "-$branch"
        }
    } catch (_: Throwable) {}

    val result = "git status -s".execute()
    if (result.isEmpty()) {
        return returnedVal
    }

    return "$returnedVal-dirty+${result.count { it == '\n' } + 1}"
}

val gitHasUncommittedSuffix = getUncommittedSuffix()
val gitCommitCount = "git rev-list refs/remotes/origin/master --count".execute().toInt()

// 432 is the count of commits before license changed
val gitCommitCountAfterOss = gitCommitCount - 432

val minSdkVer = 29
val targetSdkVer = 36

/*
 * The Modern Xposed artifacts (io.github.libxposed:api / :service, 102.0.0) declare
 * minCompileSdk 37 in their AAR metadata, therefore the project has to compile against
 * android-37 even though it still targets android-36. The minor API level (37.1) is
 * requested through the `compileSdk { }` block in configureBaseExtension().
 */
val compileSdkVer = 37
val compileSdkMinorVer = 1

val appVerCode = gitCommitCount + 0x6f7373 // commit count + 0xOSS
val appVerName = "oss-${gitCommitCountAfterOss}${gitHasUncommittedSuffix}"

/*
 * configVerCode, serviceVerCode and minBackupVerCode is used by other build.gradle.kts files
 *
 * DO NOT REMOVE THESE LINES
*/

val configVerCode = 93
val serviceVerCode = 102
val minBackupVerCode = 65
val appPackageName = "org.frknkrc44.hma_oss"

val crowdinProjectId = localProperties.getProperty("crowdinProjectId", "")
val crowdinApiKey = localProperties.getProperty("crowdinApiKey", "")

/*
 * Shared build configuration, published through `extra` so that the subprojects can read
 * it with `rootProject.extra["name"]`. The `val name by extra(value)` delegate syntax is
 * deprecated since Gradle 9 and will be removed in Gradle 10.
 */
mapOf<String, Any>(
    "minSdkVer" to minSdkVer,
    "targetSdkVer" to targetSdkVer,
    "compileSdkVer" to compileSdkVer,
    "compileSdkMinorVer" to compileSdkMinorVer,
    "appVerCode" to appVerCode,
    "appVerName" to appVerName,
    "configVerCode" to configVerCode,
    "serviceVerCode" to serviceVerCode,
    "minBackupVerCode" to minBackupVerCode,
    "appPackageName" to appPackageName,
    "localBuild" to localBuild,
    "officialBuild" to officialBuild,
    "crowdinProjectId" to crowdinProjectId,
    "crowdinApiKey" to crowdinApiKey,
).forEach { (name, value) -> extra.set(name, value) }

val androidSourceCompatibility = JavaVersion.VERSION_21
val androidTargetCompatibility = JavaVersion.VERSION_21

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

fun Project.configureBaseExtension() {
    val minSdkVer: Int = rootProject.extra["minSdkVer"] as Int
    val targetSdkVer: Int = rootProject.extra["targetSdkVer"] as Int
    val compileSdkVer: Int = rootProject.extra["compileSdkVer"] as Int
    val compileSdkMinorVer: Int = rootProject.extra["compileSdkMinorVer"] as Int
    val appVerCode: Int = rootProject.extra["appVerCode"] as Int
    val appVerName: String = rootProject.extra["appVerName"] as String

    // `compileSdkVersion(Int)` is the legacy accessor. Only the modern `compileSdk { }`
    // block can request a minor API level, which is what android-37.1 needs.
    extensions.findByType(CommonExtension::class.java)?.apply {
        compileSdk {
            version = release(compileSdkVer) {
                minorApiLevel = compileSdkMinorVer
            }
        }
    }

    // The signing configuration is shared by the application and the library branch.
    var releaseSigningConfig: ApkSigningConfig? = null

    val createSigningConfig: NamedDomainObjectContainer<out ApkSigningConfig>.() -> Unit = {
        releaseSigningConfig = localProperties.getProperty("fileDir")?.let { keyStore ->
            create("config") {
                storeFile = file(keyStore)
                storePassword = localProperties.getProperty("storePassword")
                keyAlias = localProperties.getProperty("keyAlias")
                keyPassword = localProperties.getProperty("keyPassword")
            }
        }
    }

    extensions.findByType<ApplicationExtension>()?.run {
        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }

        defaultConfig {
            minSdk = minSdkVer
            targetSdk = targetSdkVer
            versionCode = appVerCode
            versionName = appVerName
        }

        signingConfigs(createSigningConfig)

        buildTypes {
            all {
                signingConfig = releaseSigningConfig ?: signingConfigs["debug"]
            }
            named("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
        }

        dependenciesInfo {
            // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
            includeInApk = false
            // Disables dependency metadata when building Android App Bundles (for Google Play)
            includeInBundle = false
        }
    }

    extensions.findByType<LibraryExtension>()?.run {
        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }

        defaultConfig {
            minSdk = minSdkVer
            consumerProguardFiles("proguard-rules.pro")
        }

        signingConfigs(createSigningConfig)

        buildTypes {
            all {
                signingConfig = releaseSigningConfig ?: signingConfigs["debug"]
            }
            named("release") {
                isMinifyEnabled = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
        }
    }
}

subprojects {
    plugins.withId("com.android.application") {
        configureBaseExtension()
    }
    plugins.withId("com.android.library") {
        configureBaseExtension()
    }
}
