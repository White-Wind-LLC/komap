package ua.wwind.komap

/**
 * Marks a callable as a factory to construct a target type for Komap-generated mappings.
 *
 * Apply to:
 * - top-level functions
 * - object/companion member functions
 * - secondary constructors
 *
 * Resolution and module visibility:
 * - The factory callable MUST be declared in the same Gradle module as the class annotated with
 *   @Komap that will use it. This is due to KSP symbol visibility: during code generation a
 *   processor sees only symbols from the current module.
 *
 * Qualifiers:
 * - Use [qualifier] to label a factory. When a @Komap annotation provides the
 *   corresponding qualifier in its factoryQualifiers list, this factory will be preferred.
 * - If [qualifier] is blank, the factory is considered unqualified and applicable by default.
 *
 * The processor discovers these factories and uses a factory whose return type matches the
 * target class when generating mappers TO that class. For constructors, the owning class must
 * be the target class.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.SOURCE)
public annotation class KomapFactory(val qualifier: String = "")