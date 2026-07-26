package com.clocktower.engine

import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * Decodes share links from the official script tool
 * (script.bloodontheclocktower.com/?script=...): the parameter is
 * URL-encoded, base64-encoded, gzipped JSON.
 */
object ScriptLink {

    /** True when the text looks like a script link rather than raw JSON. */
    fun isLink(text: String): Boolean {
        val t = text.trim()
        return t.contains("script=") || t.startsWith("http://") || t.startsWith("https://")
    }

    /** Returns the decoded script JSON, or null if this isn't a valid link. */
    fun decode(text: String): String? {
        val trimmed = text.trim()
        val param = when {
            trimmed.contains("script=") -> trimmed.substringAfter("script=").substringBefore('&')
            else -> return null
        }
        return try {
            val urlDecoded = URLDecoder.decode(param, "UTF-8")
            val bytes = Base64.getDecoder().decode(urlDecoded)
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}
