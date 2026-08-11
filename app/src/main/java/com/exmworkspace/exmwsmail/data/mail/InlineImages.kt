package com.exmworkspace.exmwsmail.data.mail

import java.net.URLEncoder

/**
 * Rewrites `cid:` references in an email body into absolute API URLs (§4.10).
 *
 * Over IMAP the inline parts arrived with the message and could be resolved locally; the
 * REST backend serves them per-request instead, so the HTML has to point at
 * `/messages/{uid}/cid/{content_id}`. Those URLs need the session's Bearer token, which the
 * WebView cannot attach on its own — see `InlineImageWebViewClient`.
 *
 * Pure function (no Android types) so it can be unit-tested directly.
 */
object InlineImages {

    private val CID_ATTR = Regex("""(?i)\b(src|background)\s*=\s*(["'])\s*cid:([^"']+?)\s*\2""")

    fun rewrite(html: String?, baseUrl: String, uid: String, folder: String): String? {
        if (html.isNullOrEmpty()) return html
        val root = baseUrl.trimEnd('/')
        val folderParam = encode(folder)
        return CID_ATTR.replace(html) { match ->
            val attribute = match.groupValues[1]
            val quote = match.groupValues[2]
            // Content-IDs are often written with the angle brackets of the raw header.
            val contentId = match.groupValues[3].trim().trim('<', '>')
            val url = "$root/api/emails/messages/${encode(uid)}/cid/${encode(contentId)}" +
                "?folder=$folderParam"
            "$attribute=$quote$url$quote"
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
