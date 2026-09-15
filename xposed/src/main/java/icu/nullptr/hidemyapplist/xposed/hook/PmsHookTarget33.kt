package icu.nullptr.hidemyapplist.xposed.hook

import android.content.pm.PackageInstaller
import android.os.Build
import androidx.annotation.RequiresApi
import icu.nullptr.hidemyapplist.common.Constants.VENDING_PACKAGE_NAME
import icu.nullptr.hidemyapplist.common.Utils
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.Utils4Xposed.getPackageNameFromPackageSettings
import icu.nullptr.hidemyapplist.xposed.XposedConstants.APPS_FILTER_IMPL_CLASS
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import icu.nullptr.hidemyapplist.xposed.bridge.hookBefore
import icu.nullptr.hidemyapplist.xposed.bridge.methodName

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class PmsHookTarget33(service: HMAService) : PmsHookTargetBase(service) {

    override val TAG = "PmsHookTarget33"

    private val getPackagesForUidMethod by lazy {
        Reflect.findMethod("com.android.server.pm.Computer") {
            it.name == "getPackagesForUid"
        }
    }

    override val fakeSystemPackageInstallSourceInfo: Any by lazy {
        Reflect.findConstructor("android.content.pm.InstallSourceInfo") {
            it.parameterCount == 5
        }.newInstance(
            null,
            null,
            null,
            null,
            PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED,
        )
    }

    override val fakeUserPackageInstallSourceInfo: Any by lazy {
        Reflect.findConstructor("android.content.pm.InstallSourceInfo") {
            it.parameterCount == 5
        }.newInstance(
            VENDING_PACKAGE_NAME,
            psPackageInfo?.signingInfo,
            VENDING_PACKAGE_NAME,
            VENDING_PACKAGE_NAME,
            PackageInstaller.PACKAGE_SOURCE_STORE,
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun load() {
        logI(TAG) { "Load hook" }

        hooks += Reflect.findMethod(APPS_FILTER_IMPL_CLASS, findSuper = true) {
            it.name == "shouldFilterApplication"
        }.hookBefore("pms:shouldFilterApplication") { param ->
            applyPackageHiding(
                param.methodName,
                { param.args[1] as Int? },
                { getPackageNameFromPackageSettings(param.args[3]) },
                {
                    Utils.binderLocalScope {
                        getPackagesForUidMethod.invoke(param.args[0], it) as Array<String>?
                    }
                },
                { param.result = true },
            )
        }

        super.load()
    }
}
