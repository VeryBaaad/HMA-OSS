package icu.nullptr.hidemyapplist.xposed.hook

import android.os.Binder
import android.os.Build
import androidx.annotation.RequiresApi
import icu.nullptr.hidemyapplist.common.Constants.VENDING_PACKAGE_NAME
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.Utils4Xposed.getCallingApps
import icu.nullptr.hidemyapplist.xposed.Utils4Xposed.getPackageNameFromPackageSettings
import icu.nullptr.hidemyapplist.xposed.XposedConstants.APPS_FILTER_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.PACKAGE_MANAGER_SERVICE_CLASS
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import icu.nullptr.hidemyapplist.xposed.bridge.hookBefore
import icu.nullptr.hidemyapplist.xposed.bridge.methodName

@RequiresApi(Build.VERSION_CODES.R)
class PmsHookTarget30(service: HMAService) : PmsHookTargetBase(service) {

    override val TAG = "PmsHookTarget30"

    override val fakeSystemPackageInstallSourceInfo: Any by lazy {
        Reflect.findConstructor("android.content.pm.InstallSourceInfo") {
            it.parameterCount == 4
        }.newInstance(
            null,
            null,
            null,
            null,
        )
    }

    override val fakeUserPackageInstallSourceInfo: Any by lazy {
        Reflect.findConstructor("android.content.pm.InstallSourceInfo") {
            it.parameterCount == 4
        }.newInstance(
            VENDING_PACKAGE_NAME,
            psPackageInfo?.signingInfo,
            VENDING_PACKAGE_NAME,
            VENDING_PACKAGE_NAME,
        )
    }

    override fun load() {
        logI(TAG) { "Load hook" }

        Reflect.findMethodOrNull(PACKAGE_MANAGER_SERVICE_CLASS, findSuper = true) {
            it.name == "getPackageSetting"
        }?.hookBefore("pms:getPackageSetting") { param ->
            applyPackageHiding(
                param.methodName,
                { Binder.getCallingUid() },
                { param.args[0] as String? },
                { getCallingApps(service, it) },
                { param.result = null },
            )
        }?.let {
            hooks += it
        }

        hooks += Reflect.findMethod(APPS_FILTER_CLASS) {
            it.name == "shouldFilterApplication"
        }.hookBefore("pms:shouldFilterApplication") { param ->
            applyPackageHiding(
                param.methodName,
                { param.args[0] as Int },
                { getPackageNameFromPackageSettings(param.args[2]) },
                { getCallingApps(service, it) },
                { param.result = true },
            )
        }

       super.load()
    }
}
