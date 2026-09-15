package icu.nullptr.hidemyapplist.xposed

import android.app.ActivityManagerHidden
import android.content.AttributionSource
import android.content.pm.IPackageManager
import android.os.Build
import android.os.Bundle
import android.os.ServiceManager
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Utils
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logE
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.bridge.Reflect
import org.frknkrc44.hma_oss.common.BuildConfig
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.adapter.UidObserverAdapter

object UserService {

    private const val TAG = "HMA-UserService"

    private var appUid = 0

    @Volatile
    private var observerRegistered = false

    /** The running [HMAService], also used by the hot reload hand-over. */
    @Volatile
    var service: HMAService? = null
        private set

    private val uidObserver = object : UidObserverAdapter() {
        override fun onUidActive(uid: Int) {
            val instance = HMAService.instance
            if (instance == null) {
                logE(TAG) { "HMAService instance is not available, maybe stopped" }
                return
            }

            if (uid != appUid) return
            try {
                val provider = ActivityManagerApis.getContentProviderExternal(Constants.PROVIDER_AUTHORITY, 0, null, null)
                assert (provider != null) {
                    "Failed to get provider"
                }
                val extras = Bundle()
                extras.putBinder("binder", instance)
                val reply = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val attr = AttributionSource.Builder(1000).setPackageName("android").build()
                    provider?.call(attr, Constants.PROVIDER_AUTHORITY, "", null, extras)
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                    provider?.call("android", null, Constants.PROVIDER_AUTHORITY, "", null, extras)
                } else {
                    provider?.call("android", Constants.PROVIDER_AUTHORITY, "", null, extras)
                }
                if (reply == null) {
                    logE(TAG) { "Failed to send binder to app" }
                    return
                }
                logI(TAG) { "Send binder to app" }
            } catch (e: Throwable) {
                logE(TAG, e) { "onUidActive" }
            }
        }
    }

    fun register(pms: IPackageManager, pmn: Any?) {
        logI(TAG) { "Initialize HMAService - Version ${BuildConfig.APP_VERSION_NAME}" }
        val created = HMAService(pms, pmn)
        service = created

        try {
            appUid = Utils.getPackageUidCompat(created.pms, BuildConfig.APP_PACKAGE_NAME, 0, 0)
            assert(appUid >= 0) {
                "App UID cannot be -1 or lower"
            }
        } catch (e: Throwable) {
            logE(TAG, e) { "Fatal: Cannot get package details\nCompile this app from source with your changes" }
            return
        }

        logD(TAG) { "Client uid: $appUid" }

        waitActivityService()
        ActivityManagerApis.registerUidObserver(
            uidObserver,
            ActivityManagerHidden.UID_OBSERVER_ACTIVE,
            ActivityManagerHidden.PROCESS_STATE_TOP,
            null
        )
        observerRegistered = true

        logI(TAG) { "Registered observer" }
    }

    /**
     * Releases everything owned by the module so that the framework can retire this
     * generation during a hot reload.
     */
    fun shutdown() {
        if (observerRegistered) {
            runCatching {
                ActivityManagerApis.unregisterUidObserver(uidObserver)
            }.onFailure {
                logE(TAG, it) { "Failed to unregister the UID observer" }
            }
        }
        observerRegistered = false

        HMAService.instance?.shutdown()
        service = null
        appUid = 0
        logI(TAG) { "User service stopped" }
    }

    private fun waitActivityService() {
        // use the new Android method for 11+
        // but use the getService fallback if fails to run
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Reflect.callStaticMethod(
                    ServiceManager::class.java,
                    "waitForService",
                    "activity"
                )

                return
            } catch (_: Throwable) {}
        }

        while (ServiceManager.getService("activity") == null) {
            Thread.sleep(250)
        }
    }
}
