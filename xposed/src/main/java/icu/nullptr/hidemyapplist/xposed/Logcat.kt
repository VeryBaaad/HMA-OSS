package icu.nullptr.hidemyapplist.xposed

import android.os.SystemProperties
import android.util.Log
import icu.nullptr.hidemyapplist.xposed.bridge.XposedEnvironment
import org.frknkrc44.hma_oss.common.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("SpellCheckingInspection")
object Logcat {
    private var logdReady: Boolean? = null

    fun logV(tag: String, cause: Throwable? = null, msg: () -> String) = logWithLevel(Log.VERBOSE, tag, cause, msg)

    fun logD(tag: String, cause: Throwable? = null, msg: () -> String) = logWithLevel(Log.DEBUG, tag, cause, msg)

    fun logI(tag: String, cause: Throwable? = null, msg: () -> String) = logWithLevel(Log.INFO, tag, cause, msg)

    fun logW(tag: String, cause: Throwable? = null, msg: () -> String) = logWithLevel(Log.WARN, tag, cause, msg)

    fun logE(tag: String, cause: Throwable? = null, msg: () -> String) = logWithLevel(Log.ERROR, tag, cause, msg)

    fun logWithLevel(level: Int, tag: String, cause: Throwable? = null, msg: () -> String) {
        if (level != Log.ERROR && HMAService.instance?.config?.errorOnlyLog == true) return
        if (level <= Log.DEBUG && HMAService.instance?.config?.detailLog == false) return
        if (level == Log.VERBOSE && !BuildConfig.DEBUG) return

        val message = runCatching(msg).getOrElse { return }
        val parsedMsg = parseLog(level, tag, message, cause)

        HMAService.instance?.apply {
            runCatching {
                executor.execute {
                    addLog(parsedMsg)
                    writeToXposedLog(level, tag, message, cause)
                }
            }.onFailure {
                // The executor is shut down while the module is being hot reloaded.
                writeToXposedLog(level, tag, message, cause)
            }
        } ?: writeToXposedLog(level, tag, message, cause)
    }

    private fun parseLog(level: Int, tag: String, msg: String, cause: Throwable? = null) = buildString {
        val levelStr = when (level) {
            Log.VERBOSE -> "VERBS"
            Log.DEBUG   -> "DEBUG"
            Log.INFO    -> " INFO"
            Log.WARN    -> " WARN"
            Log.ERROR   -> "ERROR"
            else        -> "?WTF?"
        }
        val date = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        append("[$levelStr] <$date> ($tag) $msg")
        if (!endsWith('\n')) append('\n')
        if (cause != null) append(Log.getStackTraceString(cause))
        if (!endsWith('\n')) append('\n')
    }

    /**
     * Writes to the Xposed framework log. Replaces `XposedBridge.log` of the legacy
     * API, the Modern Xposed API exposes it through [io.github.libxposed.api.XposedInterface].
     */
    private fun writeToXposedLog(level: Int, tag: String, msg: String, cause: Throwable?) {
        if (logdReady == null) {
            logdReady = SystemProperties.get("init.svc.logd") == "running"
        }

        if (logdReady != true) return
        if (!XposedEnvironment.isAttached) return

        runCatching {
            if (cause != null) {
                XposedEnvironment.module.log(level, tag, msg, cause)
            } else {
                XposedEnvironment.module.log(level, tag, msg)
            }
        }
    }
}
