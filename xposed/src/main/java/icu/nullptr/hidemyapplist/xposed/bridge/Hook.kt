package icu.nullptr.hidemyapplist.xposed.bridge

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * Mutable view over an interception chain, modelled after the legacy
 * `XC_MethodHook.MethodHookParam`.
 *
 * The instance is only valid for the duration of a single intercepted call, exactly
 * like [XposedInterface.Chain].
 */
class HookParam internal constructor(val chain: XposedInterface.Chain) {

    /** Method or constructor being intercepted. */
    val method: Executable get() = chain.executable

    /** Name of the intercepted member, `<clinit>` for static initializers. */
    val methodName: String get() = (chain.executable as? Member)?.name ?: "<clinit>"

    /** `this` pointer, `null` for static methods. */
    val thisObject: Any? get() = chain.thisObject

    /** Arguments of the call. Modifications are forwarded when the chain proceeds. */
    var args: Array<Any?> = chain.args.toTypedArray()

    private var resultValue: Any? = null
    private var throwableValue: Throwable? = null

    /**
     * Return value of the call.
     *
     * Assigning it from a `hookBefore` block skips the original executable, assigning
     * it from a `hookAfter` block replaces the value returned by the original call.
     */
    var result: Any?
        get() = resultValue
        set(value) {
            resultValue = value
            resultSet = true
        }

    /** Exception thrown by the original executable, only relevant for `hookAfter`. */
    var throwable: Throwable?
        get() = throwableValue
        set(value) {
            throwableValue = value
            throwableSet = true
        }

    var resultSet: Boolean = false
        private set

    var throwableSet: Boolean = false
        private set

    internal fun setResultInternal(value: Any?) {
        resultValue = value
    }

    internal fun setThrowableInternal(value: Throwable) {
        throwableValue = value
    }
}

private fun Executable.intercept(
    id: String?,
    priority: Int,
    block: (XposedInterface.Chain) -> Any?,
): HookHandle {
    val builder = XposedEnvironment.module.hook(this)
        .setPriority(priority)
        .setExceptionMode(ExceptionMode.PROTECTIVE)

    // `setId` is an API 102 addition, calling it on an API 101 framework would fail.
    if (id != null && XposedEnvironment.supportsApi102) {
        builder.setId(id)
    }

    return builder.intercept(Hooker { chain -> block(chain) })
}

/**
 * Runs [block] before the intercepted executable.
 *
 * If the block assigns [HookParam.result] or [HookParam.throwable] the original
 * executable is skipped, otherwise it is invoked with the possibly modified
 * [HookParam.args].
 */
fun Executable.hookBefore(
    id: String? = null,
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    block: (HookParam) -> Unit,
): HookHandle = intercept(id, priority) { chain ->
    val param = HookParam(chain)
    block(param)

    when {
        param.throwableSet -> throw param.throwable!!
        param.resultSet -> param.result
        else -> chain.proceed(param.args)
    }
}

/**
 * Runs [block] after the intercepted executable.
 *
 * If the original executable throws, [HookParam.throwable] is set before the block
 * runs. Assigning [HookParam.result] replaces the returned value, assigning
 * [HookParam.throwable] replaces the thrown exception.
 */
fun Executable.hookAfter(
    id: String? = null,
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    block: (HookParam) -> Unit,
): HookHandle = intercept(id, priority) { chain ->
    val param = HookParam(chain)

    try {
        param.setResultInternal(chain.proceed(param.args))
    } catch (t: Throwable) {
        param.setThrowableInternal(t)
        block(param)

        when {
            param.resultSet -> return@intercept param.result
            param.throwableSet -> throw param.throwable!!
            else -> throw t
        }
    }

    block(param)

    when {
        param.throwableSet -> throw param.throwable!!
        else -> param.result
    }
}

/** Convenience overloads so that `Constructor`s can be hooked the same way. */
fun Constructor<*>.hookBefore(
    id: String? = null,
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    block: (HookParam) -> Unit,
): HookHandle = (this as Executable).hookBefore(id, priority, block)

fun Constructor<*>.hookAfter(
    id: String? = null,
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    block: (HookParam) -> Unit,
): HookHandle = (this as Executable).hookAfter(id, priority, block)

/** Unhooks every handle and clears the collection, ignoring individual failures. */
fun MutableCollection<HookHandle>.unhookAll() {
    forEach { runCatching { it.unhook() } }
    clear()
}

/** Name of the hooked member, useful for logging. */
val HookHandle.methodName: String
    get() = (executable as? Member)?.name ?: executable.toGenericString()

/** Declaring class of the hooked member. */
val HookHandle.declaringClass: Class<*>
    get() = executable.declaringClass

/** True when the hook targets a static method. */
val Method.isStaticMethod: Boolean
    get() = java.lang.reflect.Modifier.isStatic(modifiers)
