package com.example.pocastcloni.util

import android.text.SpannableString
import android.text.Spanned
import androidx.core.text.HtmlCompat

/**
 * HIGH-PERFORMANCE string stripper.
 * Uses Regex instead of the heavy Html.fromHtml parser.
 * Safe to use in LazyColumn/RecyclerView binding for list previews.
 */
fun String?.stripHtml(): String {
    if (this.isNullOrEmpty()) return ""

    // 1. Lightweight Entity decoding (Manual replacement is much faster than Html.fromHtml)
    // We only handle the most common ones for list previews.
    val decoded =
        this
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace("<br>", " ")
            .replace("<br/>", " ")

    // 2. Regex remove all other tags.
    // Replaces <p>, </div>, <b>, etc. with a single space to prevent words merging.
    return decoded
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("\\s+"), " ") // Collapse multiple spaces into one
        .trim()
}

/**
 * Behält HTML-Formatierung (Fett, Links, Absätze) bei.
 * Verwendet den "schweren" Parser. Nur für Detail-Screens nutzen (nicht in Listen)!
 */
fun String?.parseHtml(): Spanned {
    if (this.isNullOrEmpty()) return SpannableString("")

    val decoded = decodeDoubleEscapedHtml(this)

    // FROM_HTML_MODE_LEGACY adds decent block separation for <p> and <div>
    return HtmlCompat.fromHtml(decoded, HtmlCompat.FROM_HTML_MODE_LEGACY)
}

/**
 * Private Hilfsfunktion: Repariert "doppelt codiertes" HTML.
 * Wandelt z.B. "&lt;p&gt;" in "<p>" um, falls der Server kaputtes HTML sendet.
 */
private fun decodeDoubleEscapedHtml(html: String): String {
    val needsDecoding = html.contains("&lt;") || html.contains("&gt;")
    return if (needsDecoding) {
        // Here we MUST use the parser to fix broken tags before rendering
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    } else {
        html.replace("\n", "<br>")
    }
}
