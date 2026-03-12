package com.example.pocastcloni.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.pocastcloni.R
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
        // Sekundärer Konstruktor für vararg (einfachere Nutzung)
        constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList().toImmutableList())
    }

    @Immutable
    data class PluralResource(
        @get:PluralsRes val resId: Int,
        val count: Int,
        val args: ImmutableList<Any>
    ) : UiText() {
        constructor(@PluralsRes resId: Int, count: Int, vararg args: Any) : this(resId, count, args.toList().toImmutableList())
    }

    /**
     * Löst den Text innerhalb einer Composable auf.
     * Nutzt die Compose-Resources, reagiert automatisch auf Sprachwechsel.
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
     * Löst den Text in normalen Klassen (z.B. WorkManager, ViewModel, NotificationHelper) auf.
     * Benötigt einen Context.
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
         * Helper für einfache Plurale (z.B. "5 Songs"), wo die Zahl auch der Parameter ist.
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
 * Hilft dabei, nullable Strings oder Errors aus der Domain-Layer direkt in UiText zu wandeln.
 * Verhindert "null" Anzeigen in der UI.
 */
fun String?.asUiText(): UiText {
    return if (this.isNullOrBlank()) {
        // Fallback, falls null oder leer (Erstelle diesen String in strings.xml falls gewünscht)
        UiText.DynamicString("")
    } else {
        UiText.DynamicString(this)
    }
}