package icu.nullptr.hidemyapplist.xposed.hook

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.OSUtils
import icu.nullptr.hidemyapplist.common.Utils
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logE
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.Logcat.logV
import icu.nullptr.hidemyapplist.xposed.Utils4Xposed
import icu.nullptr.hidemyapplist.xposed.XposedConstants.ACTIVITY_STACK_SUPERVISOR_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.ACTIVITY_STARTER_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.ACTIVITY_TASK_SUPERVISOR_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.COMPUTER_ENGINE_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.PACKAGE_MANAGER_SERVICE_CLASS
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import icu.nullptr.hidemyapplist.xposed.bridge.XposedEnvironment
import icu.nullptr.hidemyapplist.xposed.bridge.declaringClass
import icu.nullptr.hidemyapplist.xposed.bridge.hookAfter
import icu.nullptr.hidemyapplist.xposed.bridge.hookBefore
import icu.nullptr.hidemyapplist.xposed.bridge.methodName
import icu.nullptr.hidemyapplist.xposed.bridge.unhookAll
import io.github.libxposed.api.XposedInterface.HookHandle

class ActivityHook(private val service: HMAService) : IFrameworkHook {
    companion object {
        private const val TAG = "ActivityHook"
        private val fakeReturnCode by lazy {
            Reflect.getStaticIntField(
                Reflect.findClassOrNull("android.app.ActivityManager", XposedEnvironment.classLoader)
                    ?: throw ClassNotFoundException("android.app.ActivityManager"),
                "START_CLASS_NOT_FOUND"
            )
        }
    }

    private val hooks = mutableListOf<HookHandle>()

    override fun load() {
        logI(TAG) { "Load hook" }

        hooks += Reflect.findMethod(ACTIVITY_STARTER_CLASS) {
            it.name == "execute"
        }.hookBefore("activity:starterExecute") { param ->
            runCatching {
                val request = Reflect.getObjectField(param.thisObject, "mRequest")
                val caller = Reflect.getObjectField(request, "callingPackage") as String?
                val intent = Reflect.getObjectField(request, "intent") as Intent?
                val targetApp = intent?.component?.packageName

                if (service.shouldHideActivityLaunch(caller, targetApp)) {
                    logD(TAG) {
                        "@executeRequest: insecure query from $caller, target: ${intent?.component}"
                    }
                    param.result = fakeReturnCode
                    service.increaseALFilterCount(caller)
                }
            }.onFailure {
                logE(TAG, it) { "Fatal error occurred, ignore hook" }
                // unload()
            }
        }

        Reflect.findMethodOrNull(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ACTIVITY_TASK_SUPERVISOR_CLASS
        } else {
            ACTIVITY_STACK_SUPERVISOR_CLASS
        }) {
            it.name == "checkStartAnyActivityPermission"
        }?.hookAfter("activity:checkStartAnyActivityPermission") { param ->
            var throwable: Throwable? = param.throwable

            while (throwable != null) {
                val current = throwable
                val oldTrace = current.stackTrace
                val newTrace = oldTrace.filter { item ->
                    !Utils.containsMultiple(
                        item.className,
                        "HookBridge",
                        "LSPHooker",
                        "LSPosed",
                    )
                }

                if (newTrace.size != oldTrace.size) {
                    current.stackTrace = newTrace.toTypedArray()

                    val callingUid = param.args.lastOrNull { it is Int } as Int?

                    logD(TAG) { "@checkStartAnyActivityPermission: ${oldTrace.size - newTrace.size} remnants cleared for $callingUid!" }

                    service.increaseALFilterCount(callingUid)
                }

                throwable = current.cause
            }
        }?.let {
            hooks += it
            logD(TAG) { "Loaded ${it.methodName} hook from ${it.declaringClass}!" }
        }

        if (!OSUtils.isSamsung()) {
            hooks += Reflect.findMethod(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    COMPUTER_ENGINE_CLASS
                } else {
                    PACKAGE_MANAGER_SERVICE_CLASS
                },
                findSuper = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
            ) {
                it.name == "applyPostResolutionFilter"
            }.hookBefore("activity:applyPostResolutionFilter") { param ->
                @Suppress("UNCHECKED_CAST") // I know what I do
                val list = param.args.first() as List<ResolveInfo>?
                if (list.isNullOrEmpty()) return@hookBefore

                val callingUid = param.args.first { it is Int } as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore

                val callingApps = Utils4Xposed.getCallingApps(service, callingUid)
                val caller = callingApps.firstOrNull { service.isHookEnabled(it) }
                if (caller != null) {
                    logV(TAG) { "@${param.methodName}: $caller requested a resolve info" }

                    val filteredList = list.filter { resolveInfo ->
                        val targetApp = Utils.getPackageNameFromResolveInfo(resolveInfo)

                        logV(TAG) { "@${param.methodName}: Checking $targetApp for $caller" }

                        (!service.shouldHideActivityLaunch(caller, targetApp)).apply {
                            if (!this) {
                                logD(TAG) { "@${param.methodName}: Filtered $targetApp from $caller" }
                            }
                        }
                    }

                    if (filteredList.size != list.size) {
                        param.args[0] = filteredList.toList()

                        service.increasePMFilterCount(caller)
                    }
                }
            }
        }
    }

    override fun unload() {
        hooks.unhookAll()
    }
}
