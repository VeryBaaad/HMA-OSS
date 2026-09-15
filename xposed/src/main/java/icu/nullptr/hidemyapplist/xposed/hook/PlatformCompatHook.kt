package icu.nullptr.hidemyapplist.xposed.hook

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.RequiresApi
import icu.nullptr.hidemyapplist.common.PropertyUtils
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logE
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.XposedConstants.PLATFORM_COMPAT_CLASS
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import icu.nullptr.hidemyapplist.xposed.bridge.hookBefore
import io.github.libxposed.api.XposedInterface.HookHandle
import org.frknkrc44.hma_oss.common.BuildConfig

@RequiresApi(Build.VERSION_CODES.R)
class PlatformCompatHook(private val service: HMAService) : IFrameworkHook {

    companion object {
        private const val TAG = "PlatformCompatHook"
    }

    private val sAppDataIsolationEnabled by lazy {
        PropertyUtils.isAppDataIsolationEnabled || service.config.altAppDataIsolation
    }

    private var hook: HookHandle? = null

    override fun load() {
        if (!service.config.forceMountData) return
        logI(TAG) { "Load hook" }
        logI(TAG) { "App data isolation enabled: $sAppDataIsolationEnabled" }
        hook = Reflect.findMethod(PLATFORM_COMPAT_CLASS) {
            it.name == "isChangeEnabled"
        }.hookBefore("platformCompat:isChangeEnabled") { param ->
            runCatching {
                if (!sAppDataIsolationEnabled) return@hookBefore

                val changeId = param.args[0] as Long
                if (changeId != 143937733L) return@hookBefore

                val appInfo = param.args[1] as ApplicationInfo
                val app = appInfo.packageName
                if (app == BuildConfig.APP_PACKAGE_NAME || app in service.systemApps) return@hookBefore
                if (service.isHookEnabled(app)) {
                    param.result = true
                    logD(TAG) { "force mount data: ${appInfo.uid} $app" }
                }
            }.onFailure {
                logE(TAG, it) { "Fatal error occurred, disable hooks" }
                unload()
            }
        }
    }

    override fun unload() {
        hook?.unhook()
        hook = null
    }

    override fun onConfigChanged() {
        if (service.config.forceMountData) {
            if (hook == null) load()
        } else {
            if (hook != null) unload()
        }
    }
}
