package com.mediaflow.universal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMappingTest {

    private fun kindOf(message: String) = RuntimeException(message).toAppError().kind

    @Test
    fun passesExistingAppExceptionThrough() {
        val original = AppException(ErrorKind.PRIVATE, "detail")
        assertSame(original, original.toAppError())
    }

    @Test
    fun mapsNetworkFailures() {
        assertEquals(ErrorKind.NETWORK, UnknownHostException("host").toAppError().kind)
        assertEquals(ErrorKind.NETWORK, SocketTimeoutException().toAppError().kind)
        assertEquals(ErrorKind.NETWORK, IOException("broken pipe").toAppError().kind)
    }

    @Test
    fun mapsPrivateAndRestrictedContent() {
        assertEquals(ErrorKind.PRIVATE, kindOf("This is a private video"))
        assertEquals(ErrorKind.PRIVATE, kindOf("Sign in to confirm your age"))
        assertEquals(ErrorKind.PRIVATE, kindOf("HTTP 401 unauthorized"))
        assertEquals(ErrorKind.RESTRICTED, kindOf("Video blocked in your country"))
        assertEquals(ErrorKind.RESTRICTED, kindOf("HTTP 403 forbidden"))
    }

    @Test
    fun mapsDeletedExpiredAndFormatFailures() {
        assertEquals(ErrorKind.DELETED, kindOf("Video 404 not available"))
        assertEquals(ErrorKind.EXPIRED, kindOf("no formats found"))
        assertEquals(ErrorKind.EXPIRED, kindOf("signature cipher failed"))
        assertEquals(ErrorKind.FORMAT, kindOf("unsupported codec"))
    }

    @Test
    fun fallsBackToExtractForUnknownMessages() {
        assertEquals(ErrorKind.EXTRACT, kindOf("something unexpected happened"))
    }
}
