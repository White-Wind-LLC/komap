package ua.wwind.komap.samples.providers

import ua.wwind.komap.KomapProvider
import ua.wwind.komap.samples.factory.AttributeId

@KomapProvider
fun Long.toAttributeId() = AttributeId(this)

@KomapProvider
fun AttributeId.toAttributeLong() = this.value