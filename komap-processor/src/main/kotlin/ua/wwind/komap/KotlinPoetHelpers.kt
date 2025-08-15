package ua.wwind.komap

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.withIndent

internal fun FunSpec.Builder.addCode(block: CodeBlock.Builder.() -> Unit): FunSpec.Builder = apply {
    addCode(CodeBlock.builder().apply(block).build())
}

internal fun CodeBlock.Builder.controlFlow(
    controlFlow: String,
    vararg args: Any?,
    block: CodeBlock.Builder.() -> Unit
): CodeBlock.Builder = apply {
    beginControlFlow(controlFlow, *args).withIndent { block() }.endControlFlow()
}