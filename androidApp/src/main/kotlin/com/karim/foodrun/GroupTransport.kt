package com.karim.foodrun

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.karim.foodrun.orders.HubPairing
import com.karim.foodrun.shared.orders.GroupReplyCallback
import com.karim.foodrun.shared.orders.GroupSubscription
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

internal class GroupTransport(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val clients = mutableMapOf<HubPairing, OkHttpClient>()
    private val subscriptions = mutableSetOf<GroupSubscription>()
    private var closed = false

    fun request(hub: HubPairing, body: String, callback: GroupReplyCallback, path: String = "command") {
        check(!closed)
        val request = Request.Builder().url(endpoint(hub, path))
            .post(body.toRequestBody("application/json".toMediaType())).build()
        client(hub).newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = deliver(callback, "", context.getString(R.string.group_network_unavailable))

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) {
                            deliver(callback, "", context.getString(R.string.group_http_failed, it.code))
                            return
                        }
                        val content = it.body?.byteStream()?.use { stream -> stream.readBounded(MAX_REPLY_BYTES) }
                            ?: throw IOException("Empty response")
                        deliver(callback, content.toString(Charsets.UTF_8), "")
                    }
                } catch (_: Exception) {
                    deliver(callback, "", context.getString(R.string.group_response_failed))
                }
            }
        })
    }

    fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback): GroupSubscription {
        check(!closed)
        val subscription = Watch(hub, body, callback)
        subscriptions += subscription
        subscription.start()
        return subscription
    }

    private inner class Watch(
        private val hub: HubPairing,
        private val body: String,
        private val callback: GroupReplyCallback,
    ) : GroupSubscription {
        private var cancelled = false
        private var socket: WebSocket? = null
        private var retry = 1_000L
        private var epoch = 0L
        private val connect = Runnable { connect() }

        fun start() { main.post(connect) }

        private fun connect() {
            if (cancelled || closed) return
            val generation = ++epoch
            socket = client(hub).newWebSocket(
                Request.Builder().url(endpoint(hub, "events")).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        main.post {
                            if (cancelled || generation != epoch || closed) webSocket.cancel()
                            else if (!webSocket.send(body)) reconnect(generation)
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        main.post {
                            if (cancelled || generation != epoch || closed) return@post
                            if (text.length > MAX_REPLY_BYTES) {
                                callback.complete("", context.getString(R.string.group_response_failed))
                                cancel()
                            } else {
                                retry = 1_000L
                                callback.complete(text, "")
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, null)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        response?.close()
                        main.post { reconnect(generation) }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        main.post { reconnect(generation) }
                    }
                },
            )
        }

        private fun reconnect(generation: Long) {
            if (cancelled || closed || generation != epoch) return
            epoch++
            socket?.cancel()
            socket = null
            callback.complete("", context.getString(R.string.group_reconnecting))
            main.removeCallbacks(connect)
            main.postDelayed(connect, retry + Random.nextLong(250))
            retry = (retry * 2).coerceAtMost(30_000)
        }

        override fun cancel() {
            if (Looper.myLooper() != Looper.getMainLooper()) { main.post { cancel() }; return }
            cancelled = true
            epoch++
            main.removeCallbacks(connect)
            socket?.cancel()
            socket = null
            subscriptions.remove(this)
        }
    }

    private fun deliver(callback: GroupReplyCallback, body: String, error: String) {
        main.post { if (!closed) callback.complete(body, error) }
    }

    private fun endpoint(hub: HubPairing, path: String) = hub.url.toHttpUrl().also {
        require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.encodedPath == "/" && it.query == null && it.fragment == null)
    }.newBuilder().addPathSegment(path).build()

    private fun client(hub: HubPairing): OkHttpClient = clients.getOrPut(hub) {
        val builder = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
        if (hub.fingerprint.isNotEmpty()) {
            val trust = HubCertificateTrust(hub.fingerprint)
            val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
            builder.sslSocketFactory(sslContext.socketFactory, trust)
                .hostnameVerifier { host, session ->
                    host == hub.url.toHttpUrl().host && (session.peerCertificates.firstOrNull() as? X509Certificate)?.let(trust::matches) == true
                }
        }
        builder.build()
    }

    fun close() {
        if (closed) return
        closed = true
        subscriptions.toList().forEach { it.cancel() }
        val connections = clients.values.toList()
        clients.clear()
        main.removeCallbacksAndMessages(null)
        // TLS close_notify can write to the socket, including when evicting an idle pooled connection.
        val cleanup = java.util.concurrent.Executors.newSingleThreadExecutor()
        cleanup.execute {
            connections.forEach {
                try {
                    it.dispatcher.cancelAll()
                    it.connectionPool.evictAll()
                } finally { it.dispatcher.executorService.shutdown() }
            }
        }
        cleanup.shutdown()
    }

    companion object { const val MAX_REPLY_BYTES = 4 * 1024 * 1024 }
}

/** A paired self-signed hub is trusted only by its exact SHA-256 certificate fingerprint. */
@android.annotation.SuppressLint("CustomX509TrustManager") // Exact paired certificate + validity checks; no system-CA or trust-all fallback.
internal class HubCertificateTrust(private val fingerprint: String) : X509TrustManager {
    init { require(fingerprint.matches(Regex("[a-f0-9]{64}"))) }
    fun matches(certificate: X509Certificate): Boolean {
        val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        val expected = fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(actual, expected)
    }
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client authentication unsupported")
    }
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificate = chain?.firstOrNull() ?: throw CertificateException("Missing hub certificate")
        certificate.checkValidity()
        if (!matches(certificate)) throw CertificateException("Hub certificate differs from the paired identity")
    }
}
