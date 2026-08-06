package com.duzman46.gridbound.core

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A user-facing message that is resolved against Android resources at render time.
 *
 * ViewModels and repositories produce [UiText] instead of formatted strings, so a message
 * always follows the active locale and never carries a backend error code to the screen.
 */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /** Player-authored content such as usernames and room names. Never translated. */
    data class Raw(val value: String) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): Res = Res(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): Plural =
            Plural(id, count, args.toList())
    }
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res ->
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())

    is UiText.Plural ->
        context.resources.getQuantityString(id, count, *args.toTypedArray())

    is UiText.Raw -> value
}

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
