package ua.wwind.komap

/**
 * Marks a constructor parameter to be ignored by the mapping generator.
 *
 * When applied to a constructor parameter of a class participating in mapping generation,
 * the parameter will be treated as if there is no matching property in the counterpart class.
 * As a result, the generated mapping function will expose this value as its own parameter
 * (unless it is omitted due to defaults handling rules).
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class IgnoreInMapping