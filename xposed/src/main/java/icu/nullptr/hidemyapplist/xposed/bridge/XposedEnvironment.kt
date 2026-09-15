package icu.nullptr.hidemyapplist.xposed.bridge

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * Holds the state shared by every hook of the module.
 *
 * The Modern Xposed API hands out the [XposedModule] instance (which is also the
 * [XposedInterface]) through lifecycle callbacks only, therefore it has to be kept
 * somewhere the hook helpers can reach.
 */
object XposedEnvironment {

    private var moduleRef: XposedModule? = null

    /**
     * Class loader of the process the module was injected into. For HMA-OSS this is
     * always the system server class loader because the module only declares the
     * `system` scope.
     */
    @Volatile
    var classLoader: ClassLoader = XposedEnvironment::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader()
        private set

    /**
     * API version reported by the *running* framework, not by the library the module
     * was compiled against. See [XposedInterface.getApiVersion].
     */
    val apiVersion: Int
        get() = runCatching { moduleRef?.apiVersion ?: XposedInterface.API_101 }
            .getOrDefault(XposedInterface.API_101)

    val module: XposedModule
        get() = moduleRef ?: throw IllegalStateException("Xposed module is not attached yet")

    val isAttached: Boolean get() = moduleRef != null

    /**
     * `true` only when the running framework implements API 102 or newer.
     *
     * Every API 102 exclusive feature ([XposedInterface.HookBuilder.setId],
     * [XposedInterface.HookHandle.replaceHook], [XposedInterface.HookHandle.getId],
     * `detach()` and the hot reload callbacks) must be guarded by this flag so that
     * the module keeps working on an API 101 framework.
     */
    val supportsApi102: Boolean get() = apiVersion >= XposedInterface.API_102

    /** Hot reload is an API 102 feature, it must stay disabled on API 101. */
    val supportsHotReload: Boolean get() = isAttached && supportsApi102

    internal fun attach(module: XposedModule) {
        moduleRef = module
    }

    internal fun setClassLoader(loader: ClassLoader) {
        classLoader = loader
    }

    internal fun detachModule() {
        moduleRef = null
    }
}
