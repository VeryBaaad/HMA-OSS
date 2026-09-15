package icu.nullptr.hidemyapplist.xposed.hook

import android.content.Intent
import android.os.Build
import android.os.Bundle
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.XposedConstants.PACKAGE_MANAGER_SERVICE_CLASS
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import icu.nullptr.hidemyapplist.xposed.bridge.hookBefore
import io.github.libxposed.api.XposedInterface.HookHandle

class PmsPackageEventsHook(private val service: HMAService) : IFrameworkHook {
    private var hook: HookHandle? = null

    override fun load() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                hook = Reflect.findMethod("com.android.server.pm.BroadcastHelper") {
                    it.name == "sendPackageBroadcastAndNotify"
                }.hookBefore("pms:sendPackageBroadcastAndNotify") { param ->
                    service.handlePackageEvent(
                        param.args[0] as String?,
                        param.args[1] as String?,
                        param.args[2] as Bundle?,
                    )
                }
            } catch (_: Throwable) {
                hook = Reflect.findMethod("com.android.internal.content.PackageMonitor") {
                    it.name == "onReceive"
                }.hookBefore("pms:packageMonitorOnReceive") { param ->
                    val intent = param.args[1] as? Intent ?: return@hookBefore

                    service.handlePackageEvent(
                        intent.action,
                        intent.data?.encodedSchemeSpecificPart,
                        intent.extras,
                    )
                }
            }
        } else {
            hook = Reflect.findMethod(PACKAGE_MANAGER_SERVICE_CLASS) {
                it.name == "sendPackageBroadcast"
            }.hookBefore("pms:sendPackageBroadcast") { param ->
                service.handlePackageEvent(
                    param.args[0] as String?,
                    param.args[1] as String?,
                    param.args[2] as Bundle?,
                )
            }
        }
    }

    override fun unload() {
        hook?.unhook()
        hook = null
    }
}
