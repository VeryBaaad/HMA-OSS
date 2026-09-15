package icu.nullptr.hidemyapplist.xposed

import android.app.ActivityThread
import android.os.Binder
import android.os.Build
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Utils
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect

object Utils4Xposed {
    fun getPackageNameFromPackageSettings(packageSettings: Any?): String? {
        if (packageSettings == null) return null

        return try {
            Reflect.callMethod(packageSettings, "getPackageName") as String?
        } catch (_: Throwable) {
            runCatching {
                Reflect.findField(packageSettings::class.java, true) {
                    it.name == if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "mName" else "name"
                }.get(packageSettings) as? String
            }.getOrNull()
        }
    }

    fun getPackageManager() = ActivityThread.currentActivityThread().application.packageManager!!

    fun getCallingApps(service: HMAService): Array<String> {
        return getCallingApps(service, Binder.getCallingUid())
    }

    fun getCallingApps(service: HMAService, callingUid: Int): Array<String> {
        if (callingUid == Constants.UID_SYSTEM) return arrayOf()
        return Utils.binderLocalScope {
            service.pms.getPackagesForUid(callingUid)
        } ?: arrayOf()
    }
}
