package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.network.tls.certificates.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.time.Duration.Companion.seconds
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.client.j2se.MatrixToImageWriter

fun main() {
    val directory = File(System.getenv("FOODRUN_DATA") ?: "${System.getProperty("user.home")}/.foodrun-hub")
    directory.mkdirs(); RoomDatabase.restrict(directory, true)
    val port = System.getenv("FOODRUN_PORT")?.toIntOrNull() ?: 8443
    val addresses = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
    val host = System.getenv("FOODRUN_HOST") ?: addresses.firstOrNull()?.hostAddress ?: "127.0.0.1"
    val passwordFile = File(directory, "tls.password")
    if (!passwordFile.exists()) { passwordFile.writeText(Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))); RoomDatabase.restrict(passwordFile) }
    val password = passwordFile.readText()
    val keyFile = File(directory, "hub.p12")
    val keyStore = if (keyFile.exists()) KeyStore.getInstance("PKCS12").apply { keyFile.inputStream().use { load(it, password.toCharArray()) } }
        else buildKeyStore { certificate("foodrun") { this.password = password; domains = (listOf("localhost", "127.0.0.1", host) + addresses.map { it.hostAddress }).distinct() } }.also { ks -> keyFile.outputStream().use { ks.store(it, password.toCharArray()) }; RoomDatabase.restrict(keyFile) }
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(keyStore.getCertificate("foodrun").encoded).joinToString("") { "%02x".format(it) }
    val pairing = "foodrun://pair?host=$host&port=$port&fingerprint=$fingerprint"
    val qrFile = File(directory, "pairing.png")
    MatrixToImageWriter.writeToPath(QRCodeWriter().encode(pairing, BarcodeFormat.QR_CODE, 480, 480), "PNG", qrFile.toPath())
    println("Food Run local hub: https://$host:$port\nPairing link: $pairing\nCertificate SHA-256: $fingerprint\nPairing QR: ${qrFile.absolutePath}\nKeep this computer awake. Data: ${directory.absolutePath}")
    File(directory, "pairing.txt").writeText(pairing)
    val discoveries = addresses.mapNotNull { address -> runCatching { JmDNS.create(address).apply { registerService(ServiceInfo.create("_foodrun._tcp.local.", "Food Run", port, "version=1")) } }.getOrElse { System.err.println("Discovery unavailable on ${address.hostAddress}; use pairing link."); null } }
    val db = RoomDatabase(directory)
    val service = RoomService(db)
    Runtime.getRuntime().addShutdownHook(Thread { discoveries.forEach { it.close() }; db.close() })
    embeddedServer(Netty, configure = {
        sslConnector(keyStore, "foodrun", { password.toCharArray() }, { password.toCharArray() }) { this.port = port; this.host = "0.0.0.0" }
    }) { hubRoutes(service) }.start(wait = true)
}

fun Application.hubRoutes(service: RoomService) {
    install(WebSockets) { pingPeriod = 15.seconds; timeout = 20.seconds; maxFrameSize = 2 * 1024 * 1024 }
    val attempts = ConcurrentHashMap<String, Pair<Long, Int>>()
    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (attempts.size > 10000) attempts.entries.removeIf { now - it.value.first > 60000 }
        val value = attempts.compute(key) { _, old -> if (old == null || now - old.first > 60000) now to 1 else old.first to old.second + 1 }!!
        return value.second <= 240
    }
    launch(Dispatchers.IO) {
        while (isActive) {
            try { service.tick() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { log.error("Room maintenance failed; retrying. ${failure.javaClass.simpleName}") }
            delay(250)
        }
    }
    routing {
        get("/health") { call.respondText("Food Run hub · protocol 1") }
        post("/command") {
            if (!allow(call.request.local.remoteHost)) { call.respond(HttpStatusCode.TooManyRequests); return@post }
            val reply = try {
                val bytes = call.receiveChannel().readRemaining(2 * 1024 * 1024L + 1).readByteArray()
                require(bytes.size <= 2 * 1024 * 1024) { "Request too large." }
                val body = bytes.toString(Charsets.UTF_8)
                JsonInputValidation.validate(body)
                val command = orderJson.decodeFromString<RoomCommand>(body)
                withContext(Dispatchers.IO) { service.execute(command) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (invalid: IllegalArgumentException) { RoomReply(ok = false, error = "Invalid request. Check the supplied fields and menu format.", code = "VALIDATION") }
            catch (failure: Exception) {
                log.error("Room request failed: ${failure.javaClass.simpleName}")
                RoomReply(ok = false, error = "The hub could not save this request. Reconnect and retry the same action.", code = "HUB_UNAVAILABLE")
            }
            call.respondText(orderJson.encodeToString(reply), ContentType.Application.Json)
        }
        webSocket("/events") {
            // Credentials are sent inside the encrypted socket, never in URLs or access logs.
            if (!allow(call.request.local.remoteHost)) return@webSocket
            val first = withTimeoutOrNull(10000) { incoming.receive() } as? Frame.Text ?: return@webSocket
            val request = runCatching { val body = first.readText(); JsonInputValidation.validate(body); orderJson.decodeFromString<RoomCommand>(body) }.getOrNull() ?: return@webSocket
            if (request.kind != CommandKind.SNAPSHOT || request.protocolVersion != 1) return@webSocket
            while (isActive) {
                val snapshot = try { withContext(Dispatchers.IO) { service.execute(request) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    log.error("Room subscription failed: ${failure.javaClass.simpleName}")
                    RoomReply(ok = false, error = "The hub is temporarily unavailable. Reconnect to resume.", code = "HUB_UNAVAILABLE")
                }
                send(Frame.Text(orderJson.encodeToString(snapshot)))
                if (!snapshot.ok) break
                delay(1000)
            }
        }
    }
}
