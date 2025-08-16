package ua.wwind.komap

/**
 * Marks an extension function as a custom mapper provider.
 *
 * Usage variants (receiver → return):
 * - Property-level: receiver is the source PROPERTY type; return is the target property type.
 * - Entity-level: receiver is the overall SOURCE CLASS of the current mapper; return is the target property type.
 *
 * Requirements:
 * - Must be a top-level extension function (public or internal) visible to the module.
 * - The receiver is treated as the "source" and the return type as the "target" for resolution.
 * - Optional qualifier disambiguates multiple providers for the same (receiver, return) pair.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class KomapProvider(
    val qualifier: String = "",
)
