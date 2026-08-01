package com.duzman46.gridbound.util

inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    value?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

