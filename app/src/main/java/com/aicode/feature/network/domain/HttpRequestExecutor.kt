package com.aicode.feature.network.domain

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpRequestExecutor @Inject constructor(
    private val httpClient: OkHttpClient
) {
    fun execute(method: HttpMethod, url: String): NetworkSendState {
        val requestId = UUID.randomUUID().toString().take(8)
        val timings = CallTimings()
        val client = httpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .eventListener(timings)
            .build()

        val hasBody = method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH
        val requestBody = if (hasBody) {
            ByteArray(0).toRequestBody("application/json".toMediaTypeOrNull())
        } else {
            null
        }
        val request = Request.Builder()
            .url(url)
            .method(method.name, requestBody)
            .build()

        val startedAt = System.nanoTime()
        return try {
            client.newCall(request).execute().use { response ->
                val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                val bodyText = bodyBytes.toString(Charsets.UTF_8)
                val headers = response.headers.map { NetworkHeader(it.first, it.second) }
                val exchange = NetworkExchange(
                    headers = headers,
                    body = bodyText,
                    metrics = timings.toMetrics(
                        requestId = requestId,
                        method = method.name,
                        url = url,
                        statusCode = response.code,
                        statusText = response.message,
                        protocol = response.protocol.toString(),
                        durationMs = durationMs,
                        requestBytes = request.body?.contentLength()?.coerceAtLeast(0L) ?: 0L,
                        responseBytes = bodyBytes.size.toLong(),
                        contentType = response.header("Content-Type").orEmpty()
                    )
                )
                NetworkSendState.Success(exchange)
            }
        } catch (e: IOException) {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            NetworkSendState.Failure(
                message = e.message ?: e.javaClass.simpleName,
                exchange = NetworkExchange(
                    metrics = timings.toMetrics(
                        requestId = requestId,
                        method = method.name,
                        url = url,
                        durationMs = durationMs
                    )
                )
            )
        } catch (e: IllegalArgumentException) {
            NetworkSendState.Failure(e.message ?: e.javaClass.simpleName)
        }
    }
}

private class CallTimings : EventListener() {
    private var dnsStartNanos = 0L
    private var connectStartNanos = 0L
    private var tlsStartNanos = 0L
    private var dnsMs = -1L
    private var connectMs = -1L
    private var tlsMs = -1L
    private var remoteAddress = ""

    override fun dnsStart(call: Call, domainName: String) {
        dnsStartNanos = System.nanoTime()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) {
        if (dnsStartNanos > 0L) {
            dnsMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - dnsStartNanos)
        }
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStartNanos = System.nanoTime()
        remoteAddress = inetSocketAddress.toString().removePrefix("/")
    }

    override fun secureConnectStart(call: Call) {
        tlsStartNanos = System.nanoTime()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        if (tlsStartNanos > 0L) {
            tlsMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tlsStartNanos)
        }
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        if (connectStartNanos > 0L) {
            connectMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStartNanos)
        }
        remoteAddress = inetSocketAddress.toString().removePrefix("/")
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        val address = connection.route().socketAddress
        remoteAddress = address.toString().removePrefix("/")
    }

    fun toMetrics(
        requestId: String,
        method: String,
        url: String,
        statusCode: Int = 0,
        statusText: String = "",
        protocol: String = "",
        durationMs: Long,
        requestBytes: Long = 0,
        responseBytes: Long = 0,
        contentType: String = ""
    ): NetworkMetrics = NetworkMetrics(
        requestId = requestId,
        method = method,
        url = url,
        statusCode = statusCode,
        statusText = statusText,
        protocol = protocol,
        remoteAddress = remoteAddress,
        durationMs = durationMs,
        dnsMs = dnsMs,
        connectMs = connectMs,
        tlsMs = tlsMs,
        requestBytes = requestBytes,
        responseBytes = responseBytes,
        contentType = contentType
    )
}
