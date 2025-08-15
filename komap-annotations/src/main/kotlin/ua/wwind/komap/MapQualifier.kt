package ua.wwind.komap

/**
 * Qualifies which named custom mapper should be used for a parameter or property.
 *
 * Example: `@MapQualifier("isoDate") val createdAt: Instant`
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapQualifier(
    val value: String,
)
