package icu.nullptr.hidemyapplist.xposed.hook

import android.content.ComponentName
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Utils
import icu.nullptr.hidemyapplist.common.settings_presets.InputMethodPreset
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logE
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.Utils4Xposed
import icu.nullptr.hidemyapplist.xposed.XposedConstants.IMM_SERVICE_CLASS
import icu.nullptr.hidemyapplist.xposed.bridge.HookParam
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import icu.nullptr.hidemyapplist.xposed.bridge.hookBefore
import icu.nullptr.hidemyapplist.xposed.bridge.methodName
import icu.nullptr.hidemyapplist.xposed.bridge.unhookAll
import io.github.libxposed.api.XposedInterface.HookHandle
import java.util.Collections

class ImmHook(private val service: HMAService) : IFrameworkHook {
    companion object {
        private const val TAG = "ImmHook"
    }

    private val hooks = mutableListOf<HookHandle>()

    // TODO: Find a method to get settings activity
    fun getFakeInputMethodInfo(packageName: String): InputMethodInfo {
        val defaultInputMethod = service.getSpoofedSetting(
            packageName,
            Settings.Secure.DEFAULT_INPUT_METHOD,
            Constants.SETTINGS_SECURE,
        )

        if (defaultInputMethod?.value != null) {
            try {
                val component = ComponentName.unflattenFromString(defaultInputMethod.value!!)!!
                logD(TAG) { "Package component: \"$component\"" }

                val pkgManager = Utils4Xposed.getPackageManager()
                val kbdPackage = Utils.binderLocalScope {
                    pkgManager.getApplicationInfo(component.packageName, 0)
                }

                return InputMethodInfo(
                    component.packageName,
                    component.className,
                    kbdPackage.loadLabel(pkgManager),
                    null,
                )
            } catch (e: Throwable) {
                logE(TAG, e) { e.message ?: "" }
            }
        }

        return InputMethodInfo(
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin.LatinIME",
            "Gboard",
            null,
        )
    }

    override fun load() {
        logI(TAG) { "Load hook" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Reflect.findMethodOrNull(IMM_SERVICE_CLASS) {
                it.name == "getCurrentInputMethodInfoAsUser"
            }?.hookBefore("imm:getCurrentInputMethodInfoAsUser") { param ->
                val callingApps = Utils4Xposed.getCallingApps(service)

                val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
                if (caller != null) {
                    logD(TAG) { "@${param.methodName} spoofed input method for $caller" }

                    param.result = getFakeInputMethodInfo(caller)
                    service.increaseSettingsFilterCount(caller)
                }
            }?.let {
                logD(TAG) { "@${it.methodName} is hooked!" }
                hooks += it
            }
        }

        (Reflect.findMethodOrNull(IMM_SERVICE_CLASS) {
            it.name == "getInputMethodListInternal"
        } ?: Reflect.findMethodOrNull(IMM_SERVICE_CLASS) {
            it.name == "getInputMethodList" && it.returnType.simpleName != "InputMethodInfoSafeList"
        })?.hookBefore("imm:getInputMethodList") { param ->
            listHook(param)
        }?.let {
            logD(TAG) { "@${it.methodName} is hooked!" }
            hooks += it
        }

        (Reflect.findMethodOrNull(IMM_SERVICE_CLASS) {
            it.name == "getEnabledInputMethodListInternal"
        } ?: Reflect.findMethodOrNull(IMM_SERVICE_CLASS) {
            it.name == "getEnabledInputMethodList" && it.returnType.simpleName != "InputMethodInfoSafeList"
        })?.hookBefore("imm:getEnabledInputMethodList") { param ->
            listHook(param)
        }?.let {
            logD(TAG) { "@${it.methodName} is hooked!" }
            hooks += it
        }

        Reflect.findMethodOrNull(IMM_SERVICE_CLASS) {
            it.name == "getCurrentInputMethodSubtype"
        }?.hookBefore("imm:getCurrentInputMethodSubtype") { param ->
            subtypeHook(param)
        }?.let {
            logD(TAG) { "@${it.methodName} is hooked!" }
            hooks += it
        }

        Reflect.findMethodOrNull(IMM_SERVICE_CLASS) {
            it.name == "getLastInputMethodSubtype"
        }?.hookBefore("imm:getLastInputMethodSubtype") { param ->
            subtypeHook(param)
        }?.let {
            logD(TAG) { "@${it.methodName} is hooked!" }
            hooks += it
        }

        (Reflect.findMethodOrNull(IMM_SERVICE_CLASS) {
            it.name == "getEnabledInputMethodSubtypeListInternal"
        } ?: Reflect.findMethodOrNull(IMM_SERVICE_CLASS) {
            it.name == "getEnabledInputMethodSubtypeList"
        })?.hookBefore("imm:getEnabledInputMethodSubtypeList") { param ->
            subtypeListHook(param)
        }?.let {
            logD(TAG) { "@${it.methodName} is hooked!" }
            hooks += it
        }
    }

    private fun listHook(param: HookParam) {
        val callingApps = if (param.methodName.endsWith("Internal")) {
            val callingUid = param.args.last() as Int
            Utils4Xposed.getCallingApps(service, callingUid)
        } else {
            Utils4Xposed.getCallingApps(service)
        }

        val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
        if (caller != null) {
            logD(TAG) { "@${param.methodName} spoofed input method for $caller" }

            param.result = listOf(getFakeInputMethodInfo(caller))
            service.increaseSettingsFilterCount(caller)
        }
    }

    private fun subtypeHook(param: HookParam) {
        val callingApps = Utils4Xposed.getCallingApps(service)

        val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
        if (caller != null) {
            logD(TAG) { "@${param.methodName} spoofed input method subtype for ${callingApps.contentToString()}" }

            // TODO: Find a method to get exact value for spoofed input method
            param.result = null
            service.increaseSettingsFilterCount(caller)
        }
    }

    private fun subtypeListHook(param: HookParam) {
        val callingApps = Utils4Xposed.getCallingApps(service)

        val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
        if (caller != null) {
            logD(TAG) { "@${param.methodName} spoofed input method subtype for ${callingApps.contentToString()}" }

            // TODO: Find a method to get exact list for spoofed input method
            param.result = Collections.emptyList<InputMethodSubtype>()
            service.increaseSettingsFilterCount(caller)
        }
    }

    private fun callerIsSpoofed(caller: String) =
        service.getEnabledSettingsPresets(caller).contains(InputMethodPreset.NAME)

    override fun unload() {
        hooks.unhookAll()
    }
}
