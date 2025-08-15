@file:Suppress("TooManyFunctions")

package ua.wwind.komap

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ITERABLE
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.withIndent

internal fun processKomapOnClass(
    annotatedClass: KSClassDeclaration,
    annotation: KSAnnotation,
    resolver: Resolver,
): List<FileSpec> {
    val fromDeclarations = classArgsArrayOf(annotation, "from")
    val toDeclarations = classArgsArrayOf(annotation, "to")
    if (fromDeclarations.isEmpty() && toDeclarations.isEmpty()) {
        error("Komap on ${annotatedClass.simpleName.asString()} must specify at least one of 'from' or 'to'")
    }
    val annotatedQn = annotatedClass.qualifiedName?.asString()
    if (fromDeclarations.any { it.qualifiedName?.asString() == annotatedQn }) {
        error("Komap 'from' cannot reference the annotated class itself: $annotatedQn")
    }
    if (toDeclarations.any { it.qualifiedName?.asString() == annotatedQn }) {
        error("Komap 'to' cannot reference the annotated class itself: $annotatedQn")
    }
    val files = mutableListOf<FileSpec>()
    val skipDefaults = skipDefaultsOf(annotation)

    val factoryQualifiers = factoryQualifiersOf(annotation)

    val annotatedParams = annotatedClass.primaryConstructor?.parameters.orEmpty()
    fromDeclarations.forEach { fromDecl ->
        val fileName = mapperFromFileName(annotatedClass, fromDecl)
        val builder = FileSpec.builder(annotatedClass.toClassName().packageName, fileName)
        files += generateMapperFromFile(
            fileBuilder = builder,
            fromClass = fromDecl,
            toClass = annotatedClass,
            skipDefaults = skipDefaults,
            targetParams = annotatedParams,
            annotatedParamsForMapName = annotatedParams,
            factoryCallee = null,
            factoryTopLevelImport = null,
        )
    }
    toDeclarations.forEach { toDecl ->
        val fileName = mapperToFileName(annotatedClass, toDecl)
        val builder = FileSpec.builder(annotatedClass.toClassName().packageName, fileName)
        val resolvedFactory = resolveFactoryForTarget(toDecl, resolver, factoryQualifiers)
        val targetParams = resolvedFactory?.function?.parameters?.toList()
            ?: toDecl.primaryConstructor?.parameters.orEmpty()
        files += generateMapperFromFile(
            fileBuilder = builder,
            fromClass = annotatedClass,
            toClass = toDecl,
            skipDefaults = skipDefaults,
            targetParams = targetParams,
            annotatedParamsForMapName = annotatedParams,
            factoryCallee = resolvedFactory?.callee,
            factoryTopLevelImport = (resolvedFactory?.callee as? FactoryCallee.TopLevel)?.member,
        )
    }
    return files
}

internal fun processKomapOnConstructor(
    constructor: KSFunctionDeclaration,
    annotation: KSAnnotation,
): List<FileSpec> {
    val clazz = constructor.parentDeclaration as? KSClassDeclaration
        ?: error("Komap on non-class constructor")
    val fromDeclarations = classArgsArrayOf(annotation, "from")
    val toDeclarations = classArgsArrayOf(annotation, "to")
    if (fromDeclarations.isEmpty() && toDeclarations.isEmpty()) {
        error("Komap on constructor of ${clazz.simpleName.asString()} must specify at least one of 'from' or 'to'")
    }
    if (toDeclarations.isNotEmpty()) {
        error("Komap 'to' is not allowed on constructors of ${clazz.simpleName.asString()}; use it only on classes")
    }
    val clazzQn = clazz.qualifiedName?.asString()
    if (fromDeclarations.any { it.qualifiedName?.asString() == clazzQn }) {
        error("Komap 'from' cannot reference the annotated class itself: ${clazz.qualifiedName?.asString()}")
    }
    val files = mutableListOf<FileSpec>()
    val skipDefaults = skipDefaultsOf(annotation)

    val params = constructor.parameters.toList()
    val signature = params.joinToString("_") { it.type.resolve().declaration.simpleName.asString() }
    val suffix = "Ctor_" + signature.hashCode().toString().replace('-', 'M')
    fromDeclarations.forEach { fromDecl ->
        val fileName = mapperFromFileName(clazz, fromDecl, suffix)
        val builder = FileSpec.builder(clazz.toClassName().packageName, fileName)
        files += generateMapperFromFile(
            fileBuilder = builder,
            fromClass = fromDecl,
            toClass = clazz,
            skipDefaults = skipDefaults,
            targetParams = params,
            annotatedParamsForMapName = params,
            factoryCallee = null,
            factoryTopLevelImport = null,
        )
    }
    return files
}

internal fun processKomapOnCompanionFunction(
    function: KSFunctionDeclaration,
    annotation: KSAnnotation,
): List<FileSpec> {
    val companionDecl = function.parentDeclaration as? KSClassDeclaration
        ?: error("Komap on function not inside class")
    if (companionDecl.simpleName.asString() != "Companion") {
        error("Komap function must be inside companion object")
    }
    val outerClass = companionDecl.parentDeclaration as? KSClassDeclaration
        ?: error("Companion object without outer class")

    val fromDeclarations = classArgsArrayOf(annotation, "from")
    val toDeclarations = classArgsArrayOf(annotation, "to")
    if (fromDeclarations.isEmpty() && toDeclarations.isEmpty()) {
        error("Komap on function of ${outerClass.simpleName.asString()} must specify at least one of 'from' or 'to'")
    }
    if (toDeclarations.isNotEmpty()) {
        error(
            "Komap 'to' is not allowed on companion functions of " +
                    outerClass.simpleName.asString() +
                    "; use it only on classes"
        )
    }
    val outerQn = outerClass.qualifiedName?.asString()
    if (fromDeclarations.any { it.qualifiedName?.asString() == outerQn }) {
        error("Komap 'from' cannot reference the annotated class itself: ${outerClass.qualifiedName?.asString()}")
    }
    val files = mutableListOf<FileSpec>()
    val skipDefaults = skipDefaultsOf(annotation)

    val params = function.parameters.toList()
    val signature = params.joinToString("_") { it.type.resolve().declaration.simpleName.asString() }
    val suffix = "Func_${function.simpleName.asString()}_" + signature.hashCode().toString().replace('-', 'M')
    fromDeclarations.forEach { fromDecl ->
        val fileName = mapperFromFileName(outerClass, fromDecl, suffix)
        val builder = FileSpec.builder(outerClass.toClassName().packageName, fileName)
        files += generateMapperFromFile(
            fileBuilder = builder,
            fromClass = fromDecl,
            toClass = outerClass,
            skipDefaults = skipDefaults,
            targetParams = params,
            annotatedParamsForMapName = params,
            factoryCallee = FactoryCallee.ObjectMember(
                outerClass.toClassName().nestedClass("Companion"),
                function.simpleName.asString()
            ),
            factoryTopLevelImport = null,
        )
    }
    return files
}

private fun mapperFromFileName(
    baseClass: KSClassDeclaration,
    fromClass: KSClassDeclaration,
    suffix: String = ""
): String = buildString {
    append(baseClass.simpleName.asString())
    append("MapperFrom")
    append(fromClass.simpleName.asString())
    if (suffix.isNotEmpty()) {
        append("_")
        append(suffix)
    }
}

private fun mapperToFileName(
    baseClass: KSClassDeclaration,
    toClass: KSClassDeclaration,
    suffix: String = ""
): String = buildString {
    append(baseClass.simpleName.asString())
    append("MapperTo")
    append(toClass.simpleName.asString())
    if (suffix.isNotEmpty()) {
        append("_")
        append(suffix)
    }
}

private data class ResolvedFactory(
    val callee: FactoryCallee?,
    val function: KSFunctionDeclaration,
)

private sealed class FactoryCallee {
    data class TopLevel(val member: MemberName) : FactoryCallee()
    data class ObjectMember(val className: ClassName, val functionName: String) : FactoryCallee()
}

@Suppress("LongMethod", "LongParameterList")
private fun generateMapperFromFile(
    fileBuilder: FileSpec.Builder,
    fromClass: KSClassDeclaration,
    toClass: KSClassDeclaration,
    skipDefaults: Boolean,
    targetParams: List<KSValueParameter>,
    annotatedParamsForMapName: List<KSValueParameter>,
    factoryCallee: FactoryCallee?,
    factoryTopLevelImport: MemberName?,
): FileSpec {
    val fromClassName = fromClass.toClassName()
    val targetClassName = toClass.toClassName()

    val builder = fileBuilder

    val neededMapperImports = mutableSetOf<MemberName>()

    val mappings = targetParams.mapNotNull { param ->
        val propertyName = param.name?.asString() ?: return@mapNotNull null
        val targetType = param.type.resolve()

        val mapping = resolveMappingConsideringIgnore(
            param = param,
            propertyName = propertyName,
            skipDefaults = skipDefaults,
            targetHasDefault = param.hasDefault,
        ) {
            buildMappingExpression(
                param = param,
                propertyName = propertyName,
                targetType = targetType,
                fromClass = fromClass,
                neededMapperImports = neededMapperImports,
                skipDefaults = skipDefaults,
                targetHasDefault = param.hasDefault,
                annotatedParamsForMapName = annotatedParamsForMapName,
            )
        }
        Triple(propertyName, targetType, mapping)
    }

    val missingParams = mappings.filter { it.third.requiresParam }
        .map { (name, type, m) -> MissingParam(name, type.toTypeName(), m.hasDefault) }

    val toFunction = buildMainToFunction(
        receiverClassName = fromClassName,
        targetClassName = targetClassName,
        targetSimpleName = targetClassName.simpleName,
        mappings = mappings,
        missingParams = missingParams,
        factoryCallee = factoryCallee,
    )

    val iterableAndWrappers = buildIterableAndWrappers(
        receiverClassName = fromClassName,
        targetClassName = targetClassName,
        targetSimpleName = targetClassName.simpleName,
        missingParams = missingParams,
        mappings = mappings,
        factoryCallee = factoryCallee,
    )

    neededMapperImports.forEach { member ->
        builder.addImport(member.packageName, member.simpleName)
    }
    if (factoryTopLevelImport != null) {
        builder.addImport(factoryTopLevelImport.packageName, factoryTopLevelImport.simpleName)
    }

    return builder
        .addFunction(toFunction)
        .addFunction(iterableAndWrappers.toIterableFunction)
        .apply { iterableAndWrappers.wrapperFunctions.forEach { addFunction(it) } }
        .build()
}

@Suppress("LongParameterList")
private fun buildMainToFunction(
    receiverClassName: ClassName,
    targetClassName: ClassName,
    targetSimpleName: String,
    mappings: List<Triple<String, KSType, MappingExpression>>,
    missingParams: List<MissingParam>,
    factoryCallee: FactoryCallee?,
): FunSpec {
    val toFunctionBuilder = FunSpec.builder("to$targetSimpleName")
        .receiver(receiverClassName)
        .returns(targetClassName)
    missingParams.forEach { mp ->
        val pb = ParameterSpec.builder(mp.name, mp.typeName)
        toFunctionBuilder.addParameter(pb.build())
    }
    return toFunctionBuilder
        .addCode(buildEntityConstructionCode(targetClassName, factoryCallee, mappings))
        .build()
}

private data class MissingParam(
    val name: String,
    val typeName: TypeName,
    val hasDefault: Boolean,
)

// Helper to build MappingExpression when a source property is missing
private fun mappingExpressionForMissing(
    propertyName: String,
    skipDefaults: Boolean,
    targetHasDefault: Boolean,
): MappingExpression = if (targetHasDefault && skipDefaults) {
    MappingExpression(code = propertyName, requiresParam = false, omitFromConstructor = true)
} else {
    MappingExpression(code = propertyName, requiresParam = true, hasDefault = targetHasDefault)
}

@Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "CognitiveComplexMethod",
    "LongParameterList",
    "ReturnCount",
)
private fun buildMappingExpression(
    param: KSValueParameter,
    propertyName: String,
    targetType: KSType,
    fromClass: KSClassDeclaration,
    neededMapperImports: MutableSet<MemberName>,
    skipDefaults: Boolean,
    targetHasDefault: Boolean,
    annotatedParamsForMapName: List<KSValueParameter>,
): MappingExpression {
    val effectiveSourceNameFromAnnotated = annotatedParamsForMapName
        .findSourceNameForTargetProperty(propertyName, fromClass)
    val effectiveSourceName = effectiveSourceNameFromAnnotated
        ?: param.findMappedNameForCounterpart(fromClass)
        ?: propertyName

    val sourceProp = findSourceProperty(fromClass, effectiveSourceName)
        ?: run {
            val qualifier = findQualifierOn(param)
            val entityLevelCustom = tryResolveWithProvidedMapper(
                sourceType = fromClass.asStarProjectedType(),
                targetType = targetType,
                qualifier = qualifier,
                sourceExpr = "this",
                reportOn = param,
            )
            return if (entityLevelCustom != null) {
                neededMapperImports.addAll(entityLevelCustom.imports)
                MappingExpression(code = entityLevelCustom.code)
            } else {
                mappingExpressionForMissing(propertyName, skipDefaults, targetHasDefault)
            }
        }

    val sourceName = sourceProp.simpleName.asString()
    val sourceType = sourceProp.type.resolve()
    val expandedTarget = expandIfAlias(targetType)

    val code: String = when {
        sourceType.isDirectlyAssignableTo(expandedTarget) -> {
            "this.$sourceName"
        }
        else -> {
            val qualifier = findQualifierOn(param)
            // 1) Try a provider where receiver equals the source PROPERTY type
            val custom = tryResolveWithProvidedMapper(
                sourceType = sourceType,
                targetType = targetType,
                qualifier = qualifier,
                sourceExpr = "this.$sourceName",
                reportOn = param,
            )
            if (custom != null) {
                neededMapperImports.addAll(custom.imports)
                custom.code
            } else {
                // 2) Try a provider where receiver equals the overall mapper SOURCE CLASS (entity)
                val entityLevelCustom = tryResolveWithProvidedMapper(
                    sourceType = fromClass.asStarProjectedType(),
                    targetType = targetType,
                    qualifier = qualifier,
                    sourceExpr = "this",
                    reportOn = param,
                )
                if (entityLevelCustom != null) {
                    neededMapperImports.addAll(entityLevelCustom.imports)
                    entityLevelCustom.code
                } else {
                    // 3) Fallback to direct property access to keep compilation going
                    "this.$sourceName"
                }
            }
        }
    }
    return MappingExpression(code = code)
}

private fun findSourceProperty(fromClass: KSClassDeclaration, name: String): KSPropertyDeclaration? =
    fromClass.getAllProperties().firstOrNull { it.simpleName.asString() == name }

private fun expandIfAlias(type: KSType): KSType {
    val decl = type.declaration
    return if (decl is KSTypeAlias) decl.type.resolve() else type
}

private fun KSValueParameter.hasIgnoreInMapping(): Boolean =
    annotations.any { ann ->
        val qn = ann.annotationType.resolve().declaration.qualifiedName?.asString()
        qn == IgnoreInMapping::class.qualifiedName
    }

private fun KSValueParameter.findMappedNameForCounterpart(counterpart: KSClassDeclaration): String? {
    val counterpartQn = counterpart.qualifiedName?.asString()
    val mapNameAnnotations = annotations.filter { ann ->
        val qn = ann.annotationType.resolve().declaration.qualifiedName?.asString()
        qn == MapName::class.qualifiedName
    }
    for (ann in mapNameAnnotations) {
        val nameArg = ann.arguments.find { it.name?.asString() == "name" }?.value as? String
        val classesArg = ann.arguments.find { it.name?.asString() == "forClasses" }?.value
        val classTypes: List<KSType> = when (classesArg) {
            is List<*> -> classesArg.filterIsInstance<KSType>()
            is KSType -> listOf(classesArg)
            else -> emptyList()
        }
        val applies = classTypes.isEmpty() || classTypes.any { kt ->
            val decl = kt.declaration as? KSClassDeclaration
            decl?.qualifiedName?.asString() == counterpartQn
        }
        if (applies && !nameArg.isNullOrEmpty()) {
            return nameArg
        }
    }
    return null
}

private fun List<KSValueParameter>.findSourceNameForTargetProperty(
    targetPropertyName: String,
    counterpart: KSClassDeclaration,
): String? {
    val counterpartQn = counterpart.qualifiedName?.asString()
    for (p in this) {
        val anns = p.annotations.filter { ann ->
            val qn = ann.annotationType.resolve().declaration.qualifiedName?.asString()
            qn == MapName::class.qualifiedName
        }
        for (ann in anns) {
            val nameArg = ann.arguments.find { it.name?.asString() == "name" }?.value as? String
            val classesArg = ann.arguments.find { it.name?.asString() == "forClasses" }?.value
            val classTypes: List<KSType> = when (classesArg) {
                is List<*> -> classesArg.filterIsInstance<KSType>()
                is KSType -> listOf(classesArg)
                else -> emptyList()
            }
            val applies = classTypes.isEmpty() || classTypes.any { kt ->
                val decl = kt.declaration as? KSClassDeclaration
                decl?.qualifiedName?.asString() == counterpartQn
            }
            if (applies && !nameArg.isNullOrEmpty() && nameArg == targetPropertyName) {
                return p.name?.asString()
            }
        }
    }
    return null
}

private fun classArgsArrayOf(annotation: KSAnnotation, name: String): List<KSClassDeclaration> {
    val argVal = annotation.arguments.find { it.name?.asString() == name }?.value
    val types: List<KSType> = when (argVal) {
        is List<*> -> argVal.filterIsInstance<KSType>()
        is KSType -> listOf(argVal)
        else -> emptyList()
    }
    return types.mapNotNull { it.declaration as? KSClassDeclaration }
}

private val skipDefaultsOf: (KSAnnotation) -> Boolean = { annotation ->
    (annotation.arguments.find { it.name?.asString() == "skipDefaults" }?.value as? Boolean) ?: false
}

private fun factoryQualifiersOf(annotation: KSAnnotation): List<String> {
    val argVal = annotation.arguments.find { it.name?.asString() == "factoryQualifiers" }?.value
    val values: List<*> = when (argVal) {
        is List<*> -> argVal
        is String -> listOf(argVal)
        else -> emptyList<Any>()
    }
    return values.filterIsInstance<String>().filter { it.isNotBlank() }
}

@Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
)
private fun resolveFactoryForTarget(
    target: KSClassDeclaration,
    resolver: Resolver,
    desiredQualifiers: List<String>,
): ResolvedFactory? {
    val factories = resolver
        .getSymbolsWithAnnotation(KomapFactory::class.qualifiedName!!)
        .filterIsInstance<KSFunctionDeclaration>()
        .toList()

    // Filter by return type (functions) or owner (constructors) matching the target
    val candidates = factories.filter { it.matchesTarget(target) }

    if (candidates.isEmpty()) return null

    // Error if there are duplicates for the same non-blank qualifier
    val duplicates = candidates
        .groupBy { it.factoryQualifier() }
        .filter { (_, list) -> list.size > 1 }
    if (duplicates.isNotEmpty()) {
        val targetName = target.qualifiedName?.asString() ?: target.simpleName.asString()
        val details = duplicates.entries.joinToString("; ") { (q, list) ->
            val names = list.joinToString(", ") { it.qualifiedName?.asString() ?: it.simpleName.asString() }
            "qualifier='$q': $names"
        }
        error("Duplicate @KomapFactory for $targetName with the same qualifier: $details")
    }

    val desiredSet = desiredQualifiers.toSet()

    // Step 1: unique match among desired qualifiers
    if (desiredSet.isNotEmpty()) {
        val matched = candidates.filter { fn -> fn.factoryQualifier()?.let { it in desiredSet } ?: false }
        if (matched.isNotEmpty()) return matched.first().toResolvedFactory()
    }

    // Step 2: unique unqualified factory
    val unqualified = candidates.filter { fn -> fn.factoryQualifier() == null }
    if (unqualified.isNotEmpty()) return unqualified.first().toResolvedFactory()

    // Step 3: fallback to primary constructor (by returning null)
    return null
}

private fun KSFunctionDeclaration.toResolvedFactory(): ResolvedFactory {
    val parent = this.parentDeclaration
    val callee = when {
        this.simpleName.asString() == "<init>" -> null
        parent is KSClassDeclaration -> FactoryCallee.ObjectMember(parent.toClassName(), this.simpleName.asString())
        else -> FactoryCallee.TopLevel(MemberName(this.packageName.asString(), this.simpleName.asString()))
    }
    return ResolvedFactory(callee, this)
}

private fun KSFunctionDeclaration.matchesTarget(target: KSClassDeclaration): Boolean {
    val isCtor = simpleName.asString() == "<init>"
    return if (!isCtor) {
        val ret = returnType?.resolve()?.declaration as? KSClassDeclaration
        ret?.qualifiedName?.asString() == target.qualifiedName?.asString()
    } else {
        parentDeclaration == target
    }
}

private fun KSFunctionDeclaration.factoryQualifier(): String? {
    val ann = annotations.firstOrNull { it.shortName.asString() == "KomapFactory" }
    val q = ann?.arguments?.firstOrNull { it.name?.asString() == "qualifier" }?.value as? String
    return q?.takeIf { it.isNotBlank() }
}

// Reusable code generator for entity construction to remove duplication
private fun buildEntityConstructionCode(
    targetClassName: ClassName,
    factoryCallee: FactoryCallee?,
    mappings: List<Triple<String, KSType, MappingExpression>>,
    omitParamNames: Set<String> = emptySet(),
): CodeBlock {
    val cb = CodeBlock.builder()
    when (factoryCallee) {
        null -> cb.addStatement("val entity = %T(", targetClassName)
        is FactoryCallee.TopLevel -> cb.addStatement("val entity = %M(", factoryCallee.member)
        is FactoryCallee.ObjectMember -> cb.addStatement(
            "val entity = %T.%L(",
            factoryCallee.className,
            factoryCallee.functionName
        )
    }
    cb.withIndent {
        mappings.forEach { (propertyName, _, mapping) ->
            val shouldOmit = mapping.omitFromConstructor || omitParamNames.contains(propertyName)
            if (!shouldOmit) {
                add("$propertyName = ${mapping.code},\n")
            }
        }
    }
    cb.addStatement(")")
    cb.addStatement("return entity")
    return cb.build()
}

private data class MappingExpression(
    val code: String,
    val requiresParam: Boolean = false,
    val hasDefault: Boolean = false,
    val omitFromConstructor: Boolean = false,
)

private fun resolveMappingConsideringIgnore(
    param: KSValueParameter,
    propertyName: String,
    skipDefaults: Boolean,
    targetHasDefault: Boolean,
    buildMapping: () -> MappingExpression,
): MappingExpression {
    return if (param.hasIgnoreInMapping()) {
        mappingExpressionForMissing(
            propertyName = propertyName,
            skipDefaults = skipDefaults,
            targetHasDefault = targetHasDefault,
        )
    } else {
        buildMapping()
    }
}

private data class IterableAndWrappers(
    val toIterableFunction: FunSpec,
    val wrapperFunctions: List<FunSpec>,
)

private fun buildIterableAndWrappers(
    receiverClassName: ClassName,
    targetClassName: ClassName,
    targetSimpleName: String,
    missingParams: List<MissingParam>,
    mappings: List<Triple<String, KSType, MappingExpression>>,
    factoryCallee: FactoryCallee?,
): IterableAndWrappers {
    val receiverIterableType: ParameterizedTypeName = ITERABLE
        .parameterizedBy(receiverClassName)
    val returnListType: ParameterizedTypeName = LIST
        .parameterizedBy(targetClassName)

    val toIterableFunction = buildIterableToFunction(
        receiverIterableType = receiverIterableType,
        returnListType = returnListType,
        receiverClassName = receiverClassName,
        targetSimpleName = targetSimpleName,
        missingParams = missingParams,
    )

    val defaultableMissing = missingParams.filter { it.hasDefault }
    val nonDefaultableMissing = missingParams.filter { !it.hasDefault }
    val wrapperFunctions = buildWrapperOverloads(
        receiverClassName = receiverClassName,
        receiverIterableType = receiverIterableType,
        returnListType = returnListType,
        targetClassName = targetClassName,
        targetSimpleName = targetSimpleName,
        defaultableMissing = defaultableMissing,
        nonDefaultableMissing = nonDefaultableMissing,
        mappings = mappings,
        factoryCallee = factoryCallee,
    )
    return IterableAndWrappers(toIterableFunction, wrapperFunctions)
}

@Suppress("LongParameterList")
private fun buildIterableToFunction(
    receiverIterableType: ParameterizedTypeName,
    returnListType: ParameterizedTypeName,
    receiverClassName: ClassName,
    targetSimpleName: String,
    missingParams: List<MissingParam>,
): FunSpec {
    val toIterableBuilder = FunSpec.builder("to$targetSimpleName")
        .receiver(receiverIterableType)
        .returns(returnListType)
    missingParams.forEach { mp ->
        val lambdaType = LambdaTypeName.get(
            parameters = listOf(ParameterSpec.unnamed(receiverClassName)),
            returnType = mp.typeName,
        )
        val pb = ParameterSpec.builder(mp.name, lambdaType)
        toIterableBuilder.addParameter(pb.build())
    }
    return toIterableBuilder
        .addCode {
            if (missingParams.isEmpty()) {
                addStatement("val list = map(%T::to%L)", receiverClassName, targetSimpleName)
            } else {
                val args = missingParams.joinToString(", ") { "${it.name}(it)" }
                addStatement("val list = map { it.to%L(%L) }", targetSimpleName, args)
            }
            addStatement("return list")
        }
        .build()
}

@Suppress("LongParameterList")
private fun buildWrapperOverloads(
    receiverClassName: ClassName,
    receiverIterableType: ParameterizedTypeName,
    returnListType: ParameterizedTypeName,
    targetClassName: ClassName,
    targetSimpleName: String,
    defaultableMissing: List<MissingParam>,
    nonDefaultableMissing: List<MissingParam>,
    mappings: List<Triple<String, KSType, MappingExpression>>,
    factoryCallee: FactoryCallee?,
): List<FunSpec> {
    val wrapperFunctions = mutableListOf<FunSpec>()
    if (defaultableMissing.isNotEmpty()) {
        val wrapper = FunSpec.builder("to$targetSimpleName")
            .receiver(receiverClassName)
            .returns(targetClassName)
        nonDefaultableMissing.forEach { mp -> wrapper.addParameter(mp.name, mp.typeName) }
        wrapper.addCode(
            buildEntityConstructionCode(
                targetClassName = targetClassName,
                factoryCallee = factoryCallee,
                mappings = mappings,
                omitParamNames = defaultableMissing.map { it.name }.toSet(),
            )
        )
        wrapperFunctions += wrapper.build()

        val wrapperIterable = FunSpec.builder("to$targetSimpleName")
            .receiver(receiverIterableType)
            .returns(returnListType)
        nonDefaultableMissing.forEach { mp ->
            val lambdaType = LambdaTypeName.get(
                parameters = listOf(ParameterSpec.unnamed(receiverClassName)),
                returnType = mp.typeName,
            )
            wrapperIterable.addParameter(mp.name, lambdaType)
        }
        wrapperIterable.addCode {
            val args = nonDefaultableMissing.joinToString(", ") { "${it.name}(it)" }
            addStatement("val list = map { it.to%L(%L) }", targetSimpleName, args)
            addStatement("return list")
        }
        wrapperFunctions += wrapperIterable.build()
    }
    return wrapperFunctions
}
