package ua.wwind.komap

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ksp.writeTo

public class KomapProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Build custom mapper registry for this round
        KomapProcessingState.mapperRegistry = collectProvidedMappers(resolver, logger)

        val komapShortName = Komap::class.simpleName!!
        val komapSymbols = resolver
            .getSymbolsWithAnnotation(Komap::class.qualifiedName!!)
            .toList()

        val komapClassFiles = komapSymbols
            .mapNotNull { it as? KSClassDeclaration }
            .flatMap { annotatedClass ->
                annotatedClass.annotations
                    .filter { it.shortName.asString() == komapShortName }
                    .flatMap { ann -> processKomapOnClass(annotatedClass, ann, resolver) }
            }

        val komapFunctionSymbols = komapSymbols
            .mapNotNull { it as? KSFunctionDeclaration }

        val komapCtorFiles = komapFunctionSymbols
            .filter {
                it.parentDeclaration is KSClassDeclaration &&
                        (it.parentDeclaration as KSClassDeclaration).simpleName.asString() != "Companion"
            }
            .flatMap { ctorDecl ->
                ctorDecl.annotations
                    .filter { it.shortName.asString() == komapShortName }
                    .flatMap { ann -> processKomapOnConstructor(ctorDecl, ann) }
            }

        val komapCompanionFiles = komapFunctionSymbols
            .filter {
                it.parentDeclaration is KSClassDeclaration &&
                        (it.parentDeclaration as KSClassDeclaration).simpleName.asString() == "Companion"
            }
            .flatMap { funDecl ->
                funDecl.annotations
                    .filter { it.shortName.asString() == "Komap" }
                    .flatMap { ann -> processKomapOnCompanionFunction(funDecl, ann) }
            }

        (komapClassFiles + komapCtorFiles + komapCompanionFiles)
            .forEach { file -> file.writeTo(codeGenerator, aggregating = false) }

        return emptyList()
    }
}

public class KomapProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return KomapProcessor(environment.codeGenerator, environment.logger)
    }
}
