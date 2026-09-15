package icu.nullptr.hidemyapplist.xposed.hook

import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.Utils4Xposed.getCallingApps
import icu.nullptr.hidemyapplist.xposed.Utils4Xposed.getPackageNameFromPackageSettings
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import icu.nullptr.hidemyapplist.xposed.bridge.hookBefore
import icu.nullptr.hidemyapplist.xposed.bridge.methodName

class PmsHookTarget29(service: HMAService) : PmsHookTargetBase(service) {

    override val TAG = "PmsHookTarget29"

    // not required until SDK 30
    override val fakeSystemPackageInstallSourceInfo = null
    override val fakeUserPackageInstallSourceInfo = null

    @Suppress("UNCHECKED_CAST")
    override fun load() {
        logI(TAG) { "Load hook" }

        hooks += Reflect.findMethod(service.pms::class.java, findSuper = true) {
            it.name == "filterAppAccessLPr" && it.parameterCount == 5
        }.hookBefore("pms:filterAppAccessLPr") { param ->
            applyPackageHiding(
                param.methodName,
                { param.args[1] as Int? },
                { getPackageNameFromPackageSettings(param.args[0]) },
                { getCallingApps(service, it) },
                { param.result = true },
            )
        }

        super.load()
    }
}
