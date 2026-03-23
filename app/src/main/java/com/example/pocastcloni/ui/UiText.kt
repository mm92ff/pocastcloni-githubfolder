package com.example.pocastcloni.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
sealed class UiText {
    @Immutable
    data class DynamicString(val value: String) : UiText()

    @Immutable
    data class StringResource(
        @get:StringRes val resId: Int,
        val args: ImmutableList<Any>
    ) : UiText() {
        // Secondary constructor for vararg (easier to use)
        constructor(
            @StringRes resId: Int,
            vararg args: Any
        ) : this(resId, args.toList().toImmutableList())
    }

    @Immutable
    data class PluralResource(
        @get:PluralsRes val resId: Int,
        val count: Int,
        val args: ImmutableList<Any>
    ) : UiText() {
        constructor(
            @PluralsRes resId: Int,
            count: Int,
            vararg args: Any
        ) : this(resId, count, args.toList().toImmutableList())
    }

    /**
     * Resolves the text inside a Composable.
     * Uses Compose resources and reacts automatically to language changes.
     */
    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(resId, *args.toTypedArray())
            is PluralResource -> pluralStringResource(resId, count, *args.toTypedArray())
        }
    }

    /**
     * Resolves the text in non-composable classes (e.g. WorkManager, ViewModel, NotificationHelper).
     * Requires a Context.
     */
    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args.toTypedArray())
            is PluralResource -> context.resources.getQuantityString(resId, count, *args.toTypedArray())
        }
    }

    companion object {
        /**
         * Helper for simple plurals (e.g. "5 Songs") where the count is also the argument.
         */
        fun fromCount(
            @PluralsRes resId: Int,
            count: Int
        ): UiText {
            return PluralResource(resId, count, count)
        }
    }
}

/**
 * PRODUCTION-GRADE EXTENSION:
 * Converts nullable Strings or errors from the domain layer directly into UiText.
 * Prevents "null" from being displayed in the UI.
 */
fun String?.asUiText(): UiText {
    return if (this.isNullOrBlank()) {
        // Fallback for null or empty values
        UiText.DynamicString("")
    } else {
        UiText.DynamicString(this)
    }
}
