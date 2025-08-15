@file:Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "CognitiveComplexMethod",
    "NestedBlockDepth",
    "ComplexCondition",
    "ReturnCount",
    "MaxLineLength",
)

package ua.wwind.komap

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ITERABLE
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.SET

internal object KomapProcessingState {
    lateinit var mapperRegistry: ProvidedMapperRegistry
}

internal data class ProvidedMapper(
    val function: KSFunctionDeclaration,
    val receiverType: KSType,
    val returnType: KSType,
    val qualifier: String?,
    val packageName: String,
) {
    val functionName: String = function.simpleName.asString()

    fun invocationFor(receiverExpr: String): MapperCall {
        return MapperCall(
            code = "$receiverExpr.$functionName()",
            imports = setOf(MemberName(packageName, functionName))
        )
    }

    fun invocationForSafeCall(receiverExpr: String): MapperCall {
        return MapperCall(
            code = "$receiverExpr?.$functionName()",
            imports = setOf(MemberName(packageName, functionName))
        )
    }
}

internal class ProvidedMapperRegistry(
    private val logger: KSPLogger,
) {
    private val byPair: MutableMap<Triple<String, String, String?>, MutableList<ProvidedMapper>> = mutableMapOf()
    private val unqualifiedCount: MutableMap<Pair<String, String>, Int> = mutableMapOf()

    fun add(mapper: ProvidedMapper, reportNode: KSNode) {
        val srcKey = mapper.receiverType.canonicalKey(preserveAlias = false) ?: return
        val dstKeyExpanded = mapper.returnType.canonicalKey(preserveAlias = false) ?: return
        val dstKeyAlias = mapper.returnType.canonicalKey(preserveAlias = true)
        val qual = mapper.qualifier?.takeIf { it.isNotBlank() }

        fun putKey(dst: String) {
            val key = Triple(srcKey, dst, qual)
            val list = byPair.getOrPut(key) { mutableListOf() }
            list += mapper
            if (qual == null) {
                val k2 = srcKey to dst
                val count = (unqualifiedCount[k2] ?: 0) + 1
                unqualifiedCount[k2] = count
                if (count > 1) {
                    logger.error("Duplicate mapper for $srcKey→$dst; add qualifier", reportNode)
                }
            }
        }
        putKey(dstKeyExpanded)
        if (dstKeyAlias != null && dstKeyAlias != dstKeyExpanded) putKey(dstKeyAlias)
    }

    fun resolve(
        sourceType: KSType,
        targetType: KSType,
        qualifier: String?,
        reportOn: KSNode,
    ): ProvidedMapper? {
        val srcKey = sourceType.canonicalKey(preserveAlias = false) ?: return null
        val dstIsAlias = targetType.declaration is KSTypeAlias
        val dstKey = targetType.canonicalKey(preserveAlias = dstIsAlias) ?: return null
        val qual = qualifier?.takeIf { it.isNotBlank() }

        if (qual != null) {
            val keyed = byPair[Triple(srcKey, dstKey, qual)]
            if (keyed.isNullOrEmpty()) {
                logger.error("No mapper found for $srcKey→$dstKey with qualifier '$qual'", reportOn)
                return null
            }
            return keyed.first()
        }

        val unqualified = byPair[Triple(srcKey, dstKey, null)]
        return when {
            unqualified == null || unqualified.isEmpty() -> null
            unqualified.size == 1 -> unqualified.first()
            else -> null // Already reported duplicate during add()
        }
    }
}

internal data class MapperCall(val code: String, val imports: Set<MemberName> = emptySet())

internal fun collectProvidedMappers(resolver: Resolver, logger: KSPLogger): ProvidedMapperRegistry {
    val registry = ProvidedMapperRegistry(logger)
    val symbols: Sequence<KSAnnotated> = resolver.getSymbolsWithAnnotation(KomapProvider::class.qualifiedName!!)

    symbols.forEach { sym ->
        val funDecl = sym as? KSFunctionDeclaration ?: return@forEach
        // Must be extension
        val receiver = funDecl.extensionReceiver?.resolve()
        val ret = funDecl.returnType?.resolve()
        if (receiver == null || ret == null) {
            logger.error(
                "@KomapProvide must be an extension function with non-nullable receiver and return type (${funDecl.qualifiedName?.asString() ?: funDecl.simpleName.asString()})",
                funDecl
            )
            return@forEach
        }
        // Visibility: allow default public (no explicit modifier) and internal; reject private
        val isPrivate = funDecl.modifiers.contains(Modifier.PRIVATE)
        if (isPrivate) {
            logger.error(
                "@KomapProvide must be public or internal and visible in module (${funDecl.qualifiedName?.asString() ?: funDecl.simpleName.asString()})",
                funDecl
            )
            return@forEach
        }
        val parent = funDecl.parentDeclaration
        if (parent != null) {
            logger.error("@KomapProvide must be a top-level function", funDecl)
            return@forEach
        }
        val pkgName = funDecl.packageName.asString()
        val qualifier = funDecl.annotations
            .firstOrNull { it.shortName.asString() == "KomapProvide" }
            ?.arguments?.firstOrNull { it.name?.asString() == "qualifier" }?.value as? String

        val mapper = ProvidedMapper(
            function = funDecl,
            receiverType = receiver,
            returnType = ret,
            qualifier = qualifier?.takeIf { it.isNotBlank() },
            packageName = pkgName,
        )
        registry.add(mapper, funDecl)
    }

    return registry
}

// ===========================
// Resolution helper utilities
// ===========================

internal fun KSType.isDirectlyAssignableTo(target: KSType): Boolean {
    // Simple check: same declaration, or target is same but nullable, or subtyping via isAssignableFrom
    val thisDecl = this.declaration
    val targetDecl = target.declaration
    if (thisDecl == targetDecl) {
        if (!this.isMarkedNullable && target.isMarkedNullable) return true
        return this.isMarkedNullable == target.isMarkedNullable || !this.isMarkedNullable && !target.isMarkedNullable
    }
    // KSP has isAssignableFrom on KSType
    return target.isAssignableFrom(this)
}

internal fun findQualifierOn(param: KSValueParameter): String? {
    val ann =
        param.annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == MapQualifier::class.qualifiedName }
    val value = ann?.arguments?.firstOrNull { it.name?.asString() == "value" }?.value as? String
    return value?.takeIf { it.isNotBlank() }
}

internal fun tryResolveWithProvidedMapper(
    sourceType: KSType,
    targetType: KSType,
    qualifier: String?,
    sourceExpr: String,
    reportOn: KSNode,
): MapperCall? {
    val registry = KomapProcessingState.mapperRegistry

    // Handle collections and maps recursively
    val srcQn = sourceType.declaration.qualifiedName?.asString()
    val dstQn = targetType.declaration.qualifiedName?.asString()

    val iterableNames = listOf(LIST.canonicalName, SET.canonicalName, ITERABLE.canonicalName)

    if (srcQn in iterableNames && dstQn in iterableNames) {
        val srcArg = sourceType.arguments.firstOrNull()?.type?.resolve()
        val dstArg = targetType.arguments.firstOrNull()?.type?.resolve()
        if (srcArg != null && dstArg != null) {
            val elemExpr = "it"
            val mappedElem = tryResolveWithProvidedMapper(srcArg, dstArg, qualifier, elemExpr, reportOn)
                ?: if (srcArg.isDirectlyAssignableTo(dstArg)) MapperCall(code = elemExpr) else null
            if (mappedElem != null) {
                val baseCode = "$sourceExpr.map { ${mappedElem.code} }"
                val finalCode = if (dstQn == SET.canonicalName) "$baseCode.toSet()" else baseCode
                return MapperCall(code = finalCode, imports = mappedElem.imports)
            }
        }
        return null
    }

    if (srcQn == MAP.canonicalName && dstQn == MAP.canonicalName) {
        val srcKey = sourceType.arguments.getOrNull(0)?.type?.resolve()
        val srcVal = sourceType.arguments.getOrNull(1)?.type?.resolve()
        val dstKey = targetType.arguments.getOrNull(0)?.type?.resolve()
        val dstVal = targetType.arguments.getOrNull(1)?.type?.resolve()
        if (srcKey != null && srcVal != null && dstKey != null && dstVal != null) {
            val keyMapped = tryResolveWithProvidedMapper(srcKey, dstKey, qualifier, "k", reportOn)
                ?: if (srcKey.isDirectlyAssignableTo(dstKey)) MapperCall(code = "k") else null
            val valMapped = tryResolveWithProvidedMapper(srcVal, dstVal, qualifier, "v", reportOn)
                ?: if (srcVal.isDirectlyAssignableTo(dstVal)) MapperCall(code = "v") else null
            if (keyMapped != null && valMapped != null) {
                val code = "$sourceExpr.map { (k, v) -> ${keyMapped.code} to ${valMapped.code} }.toMap()"
                return MapperCall(code = code, imports = keyMapped.imports + valMapped.imports)
            }
        }
        return null
    }

    // Direct resolution first (exact nullability match)
    registry.resolve(sourceType, targetType, qualifier, reportOn)?.let { return it.invocationFor(sourceExpr) }

    // 1) Source is nullable: try provider defined on non-null receiver; prefer safe-call invocation
    if (sourceType.isMarkedNullable) {
        val nonNullSrc = sourceType.makeNotNullable()
        // 1a) Keep target as-is
        registry.resolve(nonNullSrc, targetType, qualifier, reportOn)
            ?.let { return it.invocationForSafeCall(sourceExpr) }
        // 1b) If target is nullable, also allow provider returning non-null
        if (targetType.isMarkedNullable) {
            val nonNullTgt = targetType.makeNotNullable()
            registry.resolve(nonNullSrc, nonNullTgt, qualifier, reportOn)
                ?.let { return it.invocationForSafeCall(sourceExpr) }
        }
    }

    // 2) Source is non-null: allow providers declared on nullable receiver type
    if (!sourceType.isMarkedNullable) {
        val nullableSrc = sourceType.makeNullable()
        // 2a) Keep target as-is
        registry.resolve(nullableSrc, targetType, qualifier, reportOn)?.let { return it.invocationFor(sourceExpr) }
        // 2b) If target is nullable, accept provider with non-null return
        if (targetType.isMarkedNullable) {
            val nonNullTgt = targetType.makeNotNullable()
            registry.resolve(nullableSrc, nonNullTgt, qualifier, reportOn)?.let { return it.invocationFor(sourceExpr) }
        }
    }

    // 3) Relax return nullability only: if target is nullable, accept provider returning non-null
    if (targetType.isMarkedNullable) {
        val nonNullTgt = targetType.makeNotNullable()
        registry.resolve(sourceType, nonNullTgt, qualifier, reportOn)?.let { return it.invocationFor(sourceExpr) }
    }

    return null
}

private fun KSType.canonicalKey(preserveAlias: Boolean): String? {
    val type: KSType = if (preserveAlias && this.declaration is KSTypeAlias) this else run {
        val d = this.declaration
        if (d is KSTypeAlias) d.type.resolve() else this
    }
    val qn = type.declaration.qualifiedName?.asString() ?: return null
    val args = type.arguments
    val argsRendered = if (args.isEmpty()) "" else args.joinToString(prefix = "<", postfix = ">") { arg ->
        val argType = arg.type?.resolve()
        argType?.canonicalKey(preserveAlias) ?: "*"
    }
    val nullability = if (type.isMarkedNullable) "?" else ""
    return qn + argsRendered + nullability
}
