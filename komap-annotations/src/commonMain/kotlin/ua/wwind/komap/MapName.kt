package ua.wwind.komap

import kotlin.reflect.KClass

/**
 * Specifies an alternative source property name for a specific counterpart class (or all counterparts).
 * Can be applied multiple times to the same parameter.
 */
@Repeatable
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapName(
    val name: String,
    vararg val forClasses: KClass<*>
)
