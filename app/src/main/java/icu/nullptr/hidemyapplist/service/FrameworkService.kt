package icu.nullptr.hidemyapplist.service

import android.os.Bundle
import android.util.Log
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge to the Modern Xposed framework service.
 *
 * The Modern Xposed API ships a dedicated service ([XposedService]) that the framework
 * sends to the module app through [io.github.libxposed.service.XposedProvider]. It is
 * independent from the HMA-OSS `IHMAService` binder, which is used to exchange the
 * configuration with the hooks running inside `system_server`:
 *
 * * `IHMAService`  -> HMA-OSS specific, config synchronisation.
 * * `XposedService` -> framework provided, scope queries and API 102 hot reload.
 */
object FrameworkService {

    private const val TAG = "FrameworkService"

    /** Snapshot of the connected framework. */
    data class FrameworkInfo(
        val name: String,
        val version: String,
        val versionCode: Long,
        val apiVersion: Int,
        val properties: Long,
    ) {
        /** Hot reload is an API 102 feature. */
        val supportsHotReload: Boolean get() = apiVersion >= XposedService.API_102

        val supportsSystemScope: Boolean
            get() = properties and XposedService.PROP_CAP_SYSTEM != 0L
    }

    private val _framework = MutableStateFlow<FrameworkInfo?>(null)

    /** `null` while no Xposed framework has connected to the module app. */
    val framework: StateFlow<FrameworkInfo?> = _framework.asStateFlow()

    @Volatile
    private var service: XposedService? = null

    @Volatile
    private var listenerRegistered = false

    /** The connected framework, or `null` when nothing is connected. */
    val current: XposedService? get() = service

    val currentFramework: FrameworkInfo? get() = _framework.value

    val isConnected: Boolean get() = service != null

    val supportsHotReload: Boolean get() = currentFramework?.supportsHotReload == true

    /**
     * Registers the service listener. Safe to call more than once, the helper itself
     * must only be registered a single time.
     */
    fun init() {
        if (listenerRegistered) return
        listenerRegistered = true

        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    Log.i(TAG, "Xposed service connected")
                    this@FrameworkService.service = service
                    refreshFrameworkInfo(service)
                }

                override fun onServiceDied(service: XposedService) {
                    Log.w(TAG, "Xposed service died")
                    if (this@FrameworkService.service === service) {
                        this@FrameworkService.service = null
                        _framework.value = null
                    }
                }
            })
        }.onFailure {
            Log.e(TAG, "Cannot register the Xposed service listener", it)
        }
    }

    private fun refreshFrameworkInfo(service: XposedService) {
        runCatching {
            FrameworkInfo(
                name = service.frameworkName,
                version = service.frameworkVersion,
                versionCode = service.frameworkVersionCode,
                apiVersion = service.apiVersion,
                properties = service.frameworkProperties,
            )
        }.onSuccess {
            Log.i(TAG, "Framework: ${it.name} ${it.version} (API ${it.apiVersion})")
            _framework.value = it
        }.onFailure {
            Log.e(TAG, "Cannot read the framework info", it)
        }
    }

    /** The scope the framework currently applies to this module. */
    fun scope(): List<String> = runCatching { service?.scope ?: emptyList() }.getOrDefault(emptyList())

    /** Processes currently hooked by HMA-OSS, empty when unsupported or unavailable. */
    fun runningTargets(): List<HookedTarget> {
        val current = service ?: return emptyList()
        if (!supportsHotReload) return emptyList()

        return runCatching { current.runningTargets }
            .onFailure { Log.e(TAG, "Cannot query the running targets", it) }
            .getOrDefault(emptyList())
    }

    /**
     * Asks the framework to hot reload the module in every stale target.
     *
     * @param onResult invoked for every target with the final [HotReloadResult].
     * @return `false` when hot reload is unavailable, for example on an API 101
     * framework where the feature must stay disabled.
     */
    fun hotReloadStaleTargets(onResult: (HookedTarget, HotReloadResult) -> Unit): Boolean {
        val current = service ?: return false
        if (!supportsHotReload) {
            Log.w(TAG, "Hot reload is not supported by API ${currentFramework?.apiVersion}")
            return false
        }

        val targets = runningTargets()
        if (targets.isEmpty()) return false

        // A target that never ran the currently installed code is the only one the
        // framework can reload, `UP_TO_DATE` targets have nothing to swap.
        val reloadable = targets.filter { it.state != HookedTarget.State.UP_TO_DATE }
            .ifEmpty { targets }

        for (target in reloadable) {
            runCatching {
                current.hotReloadModule(target, Bundle(), object : XposedService.HotReloadCallback {
                    override fun onHotReloadResult(target: HookedTarget, result: HotReloadResult) {
                        onResult(target, result)
                    }
                })
            }.onFailure {
                Log.e(TAG, "Cannot request hot reload for ${target.processName}", it)
            }
        }

        return true
    }
}
