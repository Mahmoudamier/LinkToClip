package com.mediaflow.universal.core

import com.mediaflow.universal.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class ErrorKind(val messageRes: Int) {
    INVALID_LINK(R.string.error_invalid_link),
    UNSUPPORTED_LINK(R.string.error_unsupported_link),
    PRIVATE(R.string.error_private),
    DELETED(R.string.error_deleted),
    NETWORK(R.string.error_network),
    RESTRICTED(R.string.error_restricted),
    FORMAT(R.string.error_format),
    EXPIRED(R.string.error_expired),
    EXTRACT(R.string.error_extract),
    DOWNLOAD(R.string.error_download),
    STORAGE(R.string.error_storage),
    PROCESSING(R.string.error_processing),
    DUPLICATE(R.string.error_duplicate),
    NO_FORMATS(R.string.error_no_formats),
    WIFI_ONLY(R.string.error_wifi_only),
    UNKNOWN(R.string.error_extract);
}

class AppException(
    val kind: ErrorKind,
    detail: String? = null,
    cause: Throwable? = null
) : RuntimeException(detail ?: kind.name, cause)

/** Maps low level engine messages to one of the messages shown to the user. */
fun Throwable.toAppError(): AppException = when (this) {
    is AppException -> this
    is UnknownHostException -> AppException(ErrorKind.NETWORK, cause = this)
    is SocketTimeoutException -> AppException(ErrorKind.NETWORK, cause = this)
    is IOException -> AppException(ErrorKind.NETWORK, cause = this)
    else -> {
        val text = (message.orEmpty() + " " + (cause?.message.orEmpty())).lowercase()
        val kind = when {
            text.hasAny(
                "private video", "private", "sign in", "sign-in", "login", "log in",
                "cookies", "members only", "members-only", "join this channel",
                "authentication", "unauthorized", "401"
            ) -> ErrorKind.PRIVATE

            text.hasAny(
                "not available", "unavailable", "removed", "deleted", "gone", "404",
                "does not exist", "no longer", "invalid id"
            ) -> ErrorKind.DELETED

            text.hasAny(
                "geo", "region", "country", "restricted", "blocked", "copyright",
                "age", "captcha", "bot", "rate limit", "too many requests", "429", "403"
            ) -> ErrorKind.RESTRICTED

            text.hasAny(
                "expired", "n parameter", "cipher", "signature", "manifest url",
                "unsupported url", "no formats", "unsupported format", "format not"
            ) -> ErrorKind.EXPIRED

            text.hasAny("format", "codec", "resolution") -> ErrorKind.FORMAT
            else -> ErrorKind.EXTRACT
        }
        AppException(kind, detail = message, cause = this)
    }
}

private fun String.hasAny(vararg tokens: String): Boolean = tokens.any { contains(it) }
