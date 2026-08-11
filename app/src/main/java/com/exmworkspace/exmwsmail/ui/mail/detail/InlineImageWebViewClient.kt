package com.exmworkspace.exmwsmail.ui.mail.detail

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.exmworkspace.exmwsmail.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Serves the message's inline images through the authenticated HTTP stack.
 *
 * A WebView cannot attach the session's Bearer token to the `<img>` requests it makes on its
 * own, and the API refuses `/cid/` without it. Intercepting requests aimed at the API host
 * and replaying them through the shared OkHttp client — which already carries the auth
 * interceptor — is what makes inline images render at all. Requests to any other host fall
 * through to the WebView untouched.
 */
class InlineImageWebViewClient(
    private val httpClient: OkHttpClient,
    private val onFinished: (WebView) -> Unit = {},
) : WebViewClient() {

    private val apiHost: String? = BuildConfig.API_BASE_URL.toHttpUrlOrNull()?.host

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val host = apiHost ?: return null
        if (!request.url.host.equals(host, ignoreCase = true)) return null
        if (!request.method.equals("GET", ignoreCase = true)) return null

        return runCatching {
            val response = httpClient
                .newCall(Request.Builder().url(request.url.toString()).get().build())
                .execute()
            val body = response.body ?: return@runCatching null
            val mediaType = body.contentType()
            WebResourceResponse(
                mediaType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
                mediaType?.charset()?.name(),
                response.code,
                response.message.ifBlank { "OK" },
                emptyMap(),
                body.byteStream(),
            )
        }.getOrNull()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onFinished(view)
    }
}
