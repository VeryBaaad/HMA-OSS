package icu.nullptr.hidemyapplist.xposed.hook

import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logV
import icu.nullptr.hidemyapplist.xposed.XposedConstants.ZYGOTE_PROCESS_CLASS
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import icu.nullptr.hidemyapplist.xposed.bridge.hookBefore
import icu.nullptr.hidemyapplist.xposed.bridge.unhookAll
import io.github.libxposed.api.XposedInterface.HookHandle

class ZygoteHook(private val service: HMAService): IFrameworkHook {
    companion object {
        private const val TAG = "ZygoteHook"
    }

    private val hooks = mutableListOf<HookHandle>()

    override fun load() {
        Reflect.findMethodOrNull(ZYGOTE_PROCESS_CLASS) {
            it.name == "start"
        }?.hookBefore("zygote:start") { param ->
            logV(TAG) { "@startZygoteProcess: Starting ${param.args.contentToString()}" }

            // ignore if the GIDs array is null
            val gIDsIndex = param.args.indexOfFirst { it is IntArray }
            if (gIDsIndex < 0) return@hookBefore

            val caller = param.args.lastOrNull { it is String } as String? ?: return@hookBefore
            var perms = service.getRestrictedZygotePermissions(caller) ?: return@hookBefore
            if (perms.isNotEmpty()) {
                val gIDs = param.args[gIDsIndex] as IntArray

                // add more security, reject if not available in GID_PAIRS
                perms = perms.filter { Constants.GID_PAIRS.containsValue(it) }

                logD(TAG) { "@startZygoteProcess: GIDs are ${gIDs.contentToString()}, removing $perms now" }
                param.args[gIDsIndex] = gIDs.filter { it !in perms }.toIntArray()
                service.increaseOthersFilterCount(caller)
            }
        }?.let {
            logD(TAG) { "Loaded ZygoteProcess start hook!" }
            hooks += it
        }
    }

    override fun unload() {
        hooks.unhookAll()
    }
}
