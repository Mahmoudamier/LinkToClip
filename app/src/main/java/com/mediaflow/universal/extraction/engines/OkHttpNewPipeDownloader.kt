package com.mediaflow.universal.extraction.engines

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

/** NewPipeExtractor performs network requests through this adapter. */
class OkHttpNewPipeDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val headers = Headers.Builder()
        request.headers().forEach { (name, values) ->
            values.forEach { value -> headers.add(name, value) }
        }
        if (headers["User-Agent"] == null) headers.add("User-Agent", USER_AGENT)

        val body = request.dataToSend()?.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val call = okhttp3.Request.Builder()
            .url(request.url())
            .headers(headers.build())
            .method(request.httpMethod(), body)
            .build()

        client.newCall(call).execute().use { response ->
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                response.request.url.toString()
            )
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36"
    }
}
