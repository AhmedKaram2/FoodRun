package com.karim.foodrun

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.karim.foodrun.shared.orders.GroupReplyCallback

@Suppress("DEPRECATION") // API 26-compatible resolve path; newer APIs require Android 13.
internal class GroupDiscovery(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var active: Search? = null

    fun discover(callback: GroupReplyCallback) {
        active?.finish("", context.getString(R.string.group_discovery_cancelled))
        Search(callback).also { active = it }.start()
    }

    fun close() { active?.stop(); active = null }

    private inner class Search(private val callback: GroupReplyCallback) {
        private var done = false
        private var started = false
        private var resolving = false
        private val queued = ArrayDeque<NsdServiceInfo>()
        private val timeout = Runnable { finish("", context.getString(R.string.group_hub_not_found)) }
        private val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) {
                main.post { finish("", context.getString(R.string.group_discovery_failed)) }
            }
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    if (!done && info.serviceType.trimEnd('.') == "_foodrun._tcp") {
                        queued.addLast(info)
                        resolveNext()
                    }
                }
            }
        }

        fun start() {
            try {
                nsd.discoverServices("_foodrun._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
                started = true
                main.postDelayed(timeout, 8_000)
            } catch (_: Exception) { finish("", context.getString(R.string.group_discovery_failed)) }
        }

        private fun resolveNext() {
            if (done || resolving || queued.isEmpty()) return
            resolving = true
            try {
                nsd.resolveService(queued.removeFirst(), object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                        main.post { resolving = false; resolveNext() }
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        main.post {
                            resolving = false
                            val host = info.host?.hostAddress?.takeIf { ':' !in it }
                            if (host != null && info.port in 1..65535) finish("https://$host:${info.port}", "")
                            else resolveNext()
                        }
                    }
                })
            } catch (_: Exception) { resolving = false; resolveNext() }
        }

        fun finish(text: String, error: String) {
            if (done) return
            stop()
            callback.complete(text, error)
        }

        fun stop() {
            if (done) return
            done = true
            main.removeCallbacks(timeout)
            if (started) runCatching { nsd.stopServiceDiscovery(listener) }
            queued.clear()
            if (active === this) active = null
        }
    }
}
