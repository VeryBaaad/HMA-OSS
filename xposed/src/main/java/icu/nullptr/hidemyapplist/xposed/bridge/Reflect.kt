package icu.nullptr.hidemyapplist.xposed.bridge

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** `true` when the member was declared `static`. */
val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)

/**
 * Small reflection helper replacing `de.robv.android.xposed.XposedHelpers` and the
 * reflection part of EzXHelper.
 *
 * The Modern Xposed API intentionally does not ship a helper library anymore, so the
 * few primitives HMA-OSS needs live here.
 */
@Suppress("unused")
object Reflect {

    // ---------------------------------------------------------------- classes

    fun findClassOrNull(name: String): Class<*>? = try {
        Class.forName(name, false, XposedEnvironment.classLoader)
    } catch (_: Throwable) {
        null
    }

    fun findClass(name: String): Class<*> =
        findClassOrNull(name) ?: throw ClassNotFoundException("$name (class loader: ${XposedEnvironment.classLoader})")

    fun findClassOrNull(name: String, classLoader: ClassLoader): Class<*>? = try {
        Class.forName(name, false, classLoader)
    } catch (_: Throwable) {
        null
    }

    // ---------------------------------------------------------------- methods

    private fun Class<*>.hierarchy(findSuper: Boolean): Sequence<Class<*>> = sequence {
        var current: Class<*>? = this@hierarchy
        while (current != null && current != Any::class.java) {
            yield(current)
            if (!findSuper) break
            current = current.superclass
        }
    }

    fun findMethodOrNull(
        clazz: Class<*>,
        findSuper: Boolean = false,
        predicate: (Method) -> Boolean,
    ): Method? {
        for (current in clazz.hierarchy(findSuper)) {
            for (method in current.declaredMethods) {
                if (predicate(method)) {
                    method.isAccessible = true
                    return method
                }
            }
        }
        return null
    }

    fun findMethodOrNull(
        className: String,
        findSuper: Boolean = false,
        predicate: (Method) -> Boolean,
    ): Method? = findMethodOrNull(findClass(className), findSuper, predicate)

    fun findMethod(
        clazz: Class<*>,
        findSuper: Boolean = false,
        predicate: (Method) -> Boolean,
    ): Method = findMethodOrNull(clazz, findSuper, predicate)
        ?: throw NoSuchMethodException("No method matching the predicate in ${clazz.name}")

    fun findMethod(
        className: String,
        findSuper: Boolean = false,
        predicate: (Method) -> Boolean,
    ): Method = findMethod(findClass(className), findSuper, predicate)

    fun findMethodOrNull(
        clazz: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>,
    ): Method? = try {
        clazz.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
    } catch (_: Throwable) {
        null
    }

    fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method =
        findMethodOrNull(clazz, name, *parameterTypes)
            ?: throw NoSuchMethodException("${clazz.name}#$name")

    fun findStaticMethodOrNull(clazz: Class<*>, name: String): Method? =
        findMethodOrNull(clazz) { it.name == name && Modifier.isStatic(it.modifiers) }

    // ----------------------------------------------------------- constructors

    fun findConstructorOrNull(clazz: Class<*>, predicate: (Constructor<*>) -> Boolean): Constructor<*>? {
        for (constructor in clazz.declaredConstructors) {
            if (predicate(constructor)) {
                constructor.isAccessible = true
                return constructor
            }
        }
        return null
    }

    fun findConstructorOrNull(
        className: String,
        predicate: (Constructor<*>) -> Boolean,
    ): Constructor<*>? = findConstructorOrNull(findClass(className), predicate)

    fun findConstructor(
        clazz: Class<*>,
        predicate: (Constructor<*>) -> Boolean,
    ): Constructor<*> = findConstructorOrNull(clazz, predicate)
        ?: throw NoSuchMethodException("No constructor matching the predicate in ${clazz.name}")

    fun findConstructor(
        className: String,
        predicate: (Constructor<*>) -> Boolean,
    ): Constructor<*> = findConstructor(findClass(className), predicate)

    // ---------------------------------------------------------------- fields

    fun findFieldOrNull(clazz: Class<*>, findSuper: Boolean = false, predicate: (Field) -> Boolean): Field? {
        for (current in clazz.hierarchy(findSuper)) {
            for (field in current.declaredFields) {
                if (predicate(field)) {
                    field.isAccessible = true
                    return field
                }
            }
        }
        return null
    }

    fun findField(
        clazz: Class<*>,
        findSuper: Boolean = false,
        predicate: (Field) -> Boolean,
    ): Field = findFieldOrNull(clazz, findSuper, predicate)
        ?: throw NoSuchFieldException("No field matching the predicate in ${clazz.name}")

    fun findFieldOrNull(clazz: Class<*>, name: String): Field? =
        findFieldOrNull(clazz, findSuper = true) { it.name == name }

    fun findField(clazz: Class<*>, name: String): Field =
        findFieldOrNull(clazz, name) ?: throw NoSuchFieldException("${clazz.name}#$name")

    fun getObjectField(instance: Any?, name: String): Any? {
        val target = instance ?: return null
        return findField(target.javaClass, name).get(target)
    }

    fun getBooleanField(instance: Any?, name: String): Boolean =
        getObjectField(instance, name) as? Boolean ?: false

    fun setBooleanField(instance: Any?, name: String, value: Boolean) {
        val target = instance ?: return
        findField(target.javaClass, name).setBoolean(target, value)
    }

    fun getIntField(instance: Any?, name: String): Int = getObjectField(instance, name) as? Int ?: 0

    fun setIntField(instance: Any?, name: String, value: Int) {
        val target = instance ?: return
        findField(target.javaClass, name).setInt(target, value)
    }

    fun getStaticObjectField(clazz: Class<*>, name: String): Any? = findField(clazz, name).get(null)

    fun getStaticIntField(clazz: Class<*>, name: String): Int = getStaticObjectField(clazz, name) as? Int ?: 0

    // -------------------------------------------------------------- invoking

    fun callMethodOrNull(instance: Any?, name: String, vararg args: Any?): Any? {
        val target = instance ?: return null
        val method = findMethodOrNull(target.javaClass, findSuper = true) {
            it.name == name && it.parameterCount == args.size
        } ?: return null

        return method.invoke(target, *args)
    }

    fun callMethod(instance: Any?, name: String, vararg args: Any?): Any? =
        callMethodOrNull(instance, name, *args)
            ?: throw NoSuchMethodException("${instance?.javaClass?.name}#$name/${args.size}")

    fun callStaticMethodOrNull(clazz: Class<*>, name: String, vararg args: Any?): Any? {
        val method = findMethodOrNull(clazz) {
            it.name == name && Modifier.isStatic(it.modifiers) && it.parameterCount == args.size
        } ?: return null

        return method.invoke(null, *args)
    }

    fun callStaticMethod(clazz: Class<*>, name: String, vararg args: Any?): Any? =
        callStaticMethodOrNull(clazz, name, *args)
            ?: throw NoSuchMethodException("${clazz.name}#$name/${args.size}")
}
