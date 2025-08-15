package ua.wwind.komap

import kotlin.reflect.KClass

/**
 * Unified annotation to generate mapping code in one or both directions relative to the
 * annotated class.
 *
 * Effects:
 * - If [from] is specified: generate mappers from each class in [from] to the annotated class
 *   (or to the class whose constructor/companion function is annotated).
 * - If [to] is specified: generate mappers from the annotated class to each class in [to].
 * - At least one of [from] or [to] must be non-empty; by default both are empty arrays.
 * - Do not specify the annotated class itself in [from] or [to].
 *
 * Notes:
 * - Custom conversions must be supplied via @KomapProvide provider functions.
 * - If a target constructor parameter is missing in the source and has a default value, it will
 *   be exposed as a parameter of the generated mapper function. Iterable overloads will accept
 *   lambdas to provide those values.
 * - You can mark any top-level function, object/companion member function, or additional
 *   constructor with @KomapFactory. When mapping TO a class in [to], the processor will prefer
 *   a discovered factory returning that class over the primary constructor. If multiple
 *   factories exist, the processor chooses one deterministically (implementation detail).
 *
 * @param from Generate mappings FROM these classes TO the annotated class. Leave empty to skip.
 * @param to Generate mappings FROM the annotated class TO these classes. Leave empty to skip.
 * @param factoryQualifiers Optional list of qualifiers to select between multiple
 * factories. If empty, unqualified factories always apply. Qualified factories are matched
 * only if their qualifier is present in this list.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
public annotation class Komap(
    public val from: Array<KClass<*>> = [],
    public val to: Array<KClass<*>> = [],
    val skipDefaults: Boolean = false,
    val factoryQualifiers: Array<String> = [],
)

