package icu.nullptr.hidemyapplist.xposed

import android.content.pm.IPackageManager
import android.os.Bundle
import android.os.IBinder
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logE
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.Logcat.logW
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import icu.nullptr.hidemyapplist.xposed.bridge.XposedEnvironment
import icu.nullptr.hidemyapplist.xposed.bridge.hookBefore
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import kotlin.concurrent.thread

private const val TAG = "HMA-XposedEntry"

/**
 * Entry class of the module.
 *
 * Migrated from the legacy `IXposedHookZygoteInit` / `IXposedHookLoadPackage` pair to
 * the Modern Xposed API: the class extends [XposedModule] and receives lifecycle
 * callbacks instead of the old `XC_LoadPackage` parameter.
 *
 * The module only declares the `system` scope in `META-INF/xposed/scope.list`, which
 * makes the framework inject it into `system_server` (the settings provider runs there
 * too because it uses `android:process="system"`).
 *
 * Hot reload is an API 102 feature. Every code path touching it is guarded by
 * [XposedEnvironment.supportsApi102] so the module stays fully functional on an API 101
 * framework, where it behaves exactly like before the migration.
 */
@Suppress("unused")
class XposedEntry : XposedModule() {

    private companion object {
        const val STATE_PMS = "pms"
        const val STATE_PMN = "pmn"
        const val SERVICE_PACKAGE = "package"
        const val SERVICE_PACKAGE_NATIVE = "package_native"
    }

    /** Services we are still waiting for, only touched while installing the hook. */
    private val targetsLeft = mutableSetOf(SERVICE_PACKAGE, SERVICE_PACKAGE_NATIVE)
    private val targetStorage = mutableMapOf<String, Any?>()

    private var serviceManagerHook: HookHandle? = null

    @Volatile
    private var serviceThread: Thread? = null

    // ------------------------------------------------------------- lifecycle

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        XposedEnvironment.attach(this)
        logI(TAG) { "Hook entry loaded into ${param.processName} (framework API ${XposedEnvironment.apiVersion})" }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        XposedEnvironment.attach(this)
        XposedEnvironment.setClassLoader(param.classLoader)

        logI(TAG) { "System server is starting, framework API ${XposedEnvironment.apiVersion}" }

        waitForPackageManagerService()
    }

    // ---------------------------------------------------------- hot reloading

    /**
     * Called in the *old* code before the framework retires this generation.
     *
     * Returns `false` on an API 101 framework, which is exactly the "disable hot reload
     * on API 101" requirement: the framework then reports `FAILED` and keeps running the
     * current generation.
     */
    override fun onHotReloading(param: HotReloadingParam): Boolean {
        if (!XposedEnvironment.supportsApi102) {
            logW(TAG) {
                "Hot reload requested but the framework only implements API " +
                        "${XposedEnvironment.apiVersion}, refusing to reload"
            }
            return false
        }

        logI(TAG) { "Hot reload requested, tearing the current generation down" }

        return runCatching { prepareForReload(param) }
            .onFailure { logE(TAG, it) { "Failed to prepare for hot reload" } }
            .getOrDefault(false)
    }

    /**
     * Called in the *new* code after the framework swapped the generations.
     *
     * The new generation never receives [onSystemServerStarting] again, so everything
     * has to be rebuilt from the state handed over by [onHotReloading].
     */
    override fun onHotReloaded(param: HotReloadedParam) {
        if (!XposedEnvironment.supportsApi102) {
            logW(TAG) { "Hot reloaded callback on API ${XposedEnvironment.apiVersion}, ignoring" }
            return
        }

        XposedEnvironment.attach(this)

        // The hooks of the previous generation are still installed, drop them first.
        param.oldHookHandles.forEach { handle ->
            runCatching { handle.unhook() }
                .onFailure { logE(TAG, it) { "Failed to unhook a hook of the previous generation" } }
        }

        // System server is never announced again, recover its class loader from the
        // executable of an old hook instead.
        val loader = param.oldHookHandles.firstNotNullOfOrNull { handle ->
            runCatching { handle.executable.declaringClass.classLoader }.getOrNull()
        }

        if (loader != null) {
            XposedEnvironment.setClassLoader(loader)
        }

        logI(TAG) {
            "Hot reloaded into ${param.processName}, dropped ${param.oldHookHandles.size} old hook(s)"
        }

        val state = param.savedInstanceState as? Bundle
        val pmsBinder = state?.getBinder(STATE_PMS)
        val pmnBinder = state?.getBinder(STATE_PMN)

        if (pmsBinder != null) {
            targetStorage[SERVICE_PACKAGE] = IPackageManager.Stub.asInterface(pmsBinder)
            targetStorage[SERVICE_PACKAGE_NATIVE] = pmnBinder
            startUserService()
        } else {
            // Nothing was handed over, fall back to the regular bootstrap.
            logW(TAG) { "No saved service state, waiting for the package manager service again" }
            targetsLeft += SERVICE_PACKAGE
            targetsLeft += SERVICE_PACKAGE_NATIVE
            waitForPackageManagerService()
        }
    }

    /**
     * Stops every module owned resource so the old generation can be retired, and hands
     * class loader neutral references over to the next generation.
     */
    private fun prepareForReload(param: HotReloadingParam): Boolean {
        val service = UserService.service ?: HMAService.instance
        val pms = service?.pms ?: (targetStorage[SERVICE_PACKAGE] as? IPackageManager)
        val pmn = service?.pmn ?: targetStorage[SERVICE_PACKAGE_NATIVE]

        // Only binders are handed over: they are created by the framework and stay valid
        // across generations, unlike anything loaded by the old module class loader.
        val state = Bundle()
        runCatching { pms?.asBinder() }.getOrNull()?.let { state.putBinder(STATE_PMS, it) }
        (pmn as? IBinder)?.let { state.putBinder(STATE_PMN, it) }

        serviceManagerHook?.unhook()
        serviceManagerHook = null

        serviceThread?.let {
            it.interrupt()
            runCatching { it.join(1_000) }
        }
        serviceThread = null

        UserService.shutdown()
        HMAService.instance?.shutdown()

        targetStorage.clear()
        targetsLeft.clear()

        param.setSavedInstanceState(state)

        // The new generation gets its own XposedModule instance.
        XposedEnvironment.detachModule()

        logI(TAG) { "Ready to be reloaded" }
        return true
    }

    // ------------------------------------------------------------- bootstrap

    /**
     * Waits until `system_server` publishes the package manager service and captures it.
     *
     * The legacy implementation hooked `ServiceManager.addService` from
     * `handleLoadPackage`; the Modern Xposed API replaced that callback with
     * [onSystemServerStarting], which fires before the bootstrap services are created.
     */
    private fun waitForPackageManagerService() {
        if (serviceManagerHook != null) return

        val serviceManager = runCatching { Reflect.findClass("android.os.ServiceManager") }
            .getOrElse {
                logE(TAG, it) { "Cannot resolve android.os.ServiceManager" }
                return
            }

        val addService = Reflect.findMethodOrNull(serviceManager) {
            it.name == "addService" && it.parameterCount >= 2
        }

        if (addService == null) {
            logE(TAG) { "Cannot find ServiceManager.addService, module disabled" }
            return
        }

        serviceManagerHook = addService.hookBefore("hma:service-manager") { param ->
            val name = param.args.getOrNull(0) as? String ?: return@hookBefore
            if (!targetsLeft.contains(name)) return@hookBefore

            if (name == SERVICE_PACKAGE || name == SERVICE_PACKAGE_NATIVE) {
                targetStorage[name] = param.args.getOrNull(1)
                targetsLeft.remove(name)
                logD(TAG) { "Captured service $name" }
            }

            if (targetsLeft.isEmpty()) {
                serviceManagerHook?.unhook()
                serviceManagerHook = null
                startUserService()
            }
        }

        logI(TAG) { "Waiting for the package manager service" }
    }

    private fun startUserService() {
        val pms = targetStorage[SERVICE_PACKAGE] as? IPackageManager
        val pmn = targetStorage[SERVICE_PACKAGE_NATIVE]

        if (pms == null) {
            logE(TAG) { "Package manager service is not available" }
            return
        }

        // When `package` is the local PackageManagerService object its class loader is the
        // system server one. That is the loader needed to resolve `com.android.server.*`,
        // and the only chance to recover it after a hot reload, where
        // onSystemServerStarting is not replayed. A Binder proxy is loaded by the boot
        // class loader and returns null here, in which case the previous value is kept.
        pms.javaClass.classLoader?.let { XposedEnvironment.setClassLoader(it) }

        targetStorage.clear()

        logD(TAG) { "Got pms: $pms, $pmn" }

        serviceThread = thread(name = "hma-service-init") {
            runCatching {
                UserService.register(pms, pmn)
            }.onFailure {
                logE(TAG, it) { "System service crashed" }
            }
        }
    }
}
