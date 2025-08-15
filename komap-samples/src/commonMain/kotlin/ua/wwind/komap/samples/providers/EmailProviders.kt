package ua.wwind.komap.samples.providers

import ua.wwind.komap.KomapProvider
import ua.wwind.komap.samples.basic.Email

// Custom mapping from Email to raw String
@KomapProvider
fun Email.toRaw(): String = value

// Custom mapping from String to Email
@KomapProvider
fun String.toEmail(): Email = Email(this)