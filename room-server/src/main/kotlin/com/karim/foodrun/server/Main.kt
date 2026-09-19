package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.http.content.*
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
    val proxyMode = System.getenv("FOODRUN_TLS_MODE")?.equals("proxy", ignoreCase = true) == true
    val port = System.getenv("FOODRUN_PORT")?.toIntOrNull()
        ?: System.getenv("PORT")?.toIntOrNull()
        ?: if (proxyMode) 8080 else 8443
    require(port in 1..65535) { "FOODRUN_PORT/PORT must be a valid TCP port." }
    val addresses = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
    val host = System.getenv("FOODRUN_HOST") ?: addresses.firstOrNull()?.hostAddress ?: "127.0.0.1"
    val passwordFile = File(directory, "tls.password")
    if (!passwordFile.exists()) { passwordFile.writeText(Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))); RoomDatabase.restrict(passwordFile) }
    val password = passwordFile.readText()
    val keyFile = File(directory, "hub.p12")
    val keyStore = if (keyFile.exists()) KeyStore.getInstance("PKCS12").apply { keyFile.inputStream().use { load(it, password.toCharArray()) } }
        else buildKeyStore { certificate("foodrun") { this.password = password; domains = (listOf("localhost", "127.0.0.1", host) + addresses.map { it.hostAddress }).distinct() } }.also { ks -> keyFile.outputStream().use { ks.store(it, password.toCharArray()) }; RoomDatabase.restrict(keyFile) }
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(keyStore.getCertificate("foodrun").encoded).joinToString("") { "%02x".format(it) }
    val publicUrl = sequenceOf(
        System.getenv("FOODRUN_PUBLIC_URL"),
        System.getenv("RENDER_EXTERNAL_URL"),
        System.getenv("RENDER_EXTERNAL_HOSTNAME")?.let { "https://${it.trim()}" },
    ).mapNotNull { it?.trim()?.trimEnd('/')?.takeIf(String::isNotEmpty) }.firstOrNull()
    if (proxyMode) require(publicUrl?.matches(Regex("https://[a-zA-Z0-9.-]+(:[0-9]{1,5})?")) == true) {
        "FOODRUN_PUBLIC_URL must be the public HTTPS API origin when FOODRUN_TLS_MODE=proxy."
    }
    val external = java.net.URI(publicUrl ?: "https://$host:$port")
    val pairing = "foodrun://pair?host=${external.host}&port=${if (external.port > 0) external.port else 443}&fingerprint=${if (proxyMode) "" else fingerprint}"
    val qrFile = File(directory, "pairing.png")
    MatrixToImageWriter.writeToPath(QRCodeWriter().encode(pairing, BarcodeFormat.QR_CODE, 480, 480), "PNG", qrFile.toPath())
    println("Food Run API: ${publicUrl ?: "https://$host:$port"}\nPairing link: $pairing\nCertificate SHA-256: ${if (proxyMode) "managed by public HTTPS" else fingerprint}\nPairing QR: ${qrFile.absolutePath}\nData: ${directory.absolutePath}")
    File(directory, "pairing.txt").writeText(pairing)
    val discoveries = if (proxyMode) emptyList() else addresses.mapNotNull { address -> runCatching { JmDNS.create(address).apply { registerService(ServiceInfo.create("_foodrun._tcp.local.", "Food Run", port, "version=1")) } }.getOrElse { System.err.println("Discovery unavailable on ${address.hostAddress}; use pairing link."); null } }
    val db = RoomDatabase(directory)
    val service = RoomService(db, identityProvider = FirebaseIdentity.configured())
    Runtime.getRuntime().addShutdownHook(Thread { discoveries.forEach { it.close() }; db.close() })
    embeddedServer(Netty, configure = {
        if (proxyMode) connector { this.port = port; this.host = "0.0.0.0" }
        else sslConnector(keyStore, "foodrun", { password.toCharArray() }, { password.toCharArray() }) { this.port = port; this.host = "0.0.0.0" }
    }) { hubRoutes(service) }.start(wait = true)
}

fun Application.hubRoutes(service: RoomService) {
    val origins = (System.getenv("FOODRUN_WEB_ORIGINS") ?: "http://localhost:5173,http://127.0.0.1:5173").split(',').filter { it.isNotBlank() }
    install(CORS) {
        origins.forEach { value -> val uri = java.net.URI(value.trim()); allowHost(uri.authority, schemes = listOf(uri.scheme)) }
        allowMethod(HttpMethod.Post); allowHeader(HttpHeaders.ContentType)
    }
    install(WebSockets) { pingPeriod = 15.seconds; timeout = 20.seconds; maxFrameSize = 2 * 1024 * 1024 }
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.headers["Access-Control-Request-Private-Network"] == "true") {
            call.response.headers.append("Access-Control-Allow-Private-Network", "true")
        }
    }
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
    launch(Dispatchers.IO) {
        while (isActive) {
            try { service.syncCloud() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { log.warn("Cloud sync will retry; changes remain saved on the hub.") }
            delay(5000)
        }
    }
    routing {
        staticResources("/", "web")
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
            if (request.kind !in listOf(CommandKind.SNAPSHOT, CommandKind.HOME) || request.protocolVersion != 1) return@webSocket
            var lastVersion = Long.MIN_VALUE
            var lastSnapshotAt = 0L
            var lastPresenceAt = 0L
            var memberId = ""
            while (isActive) {
                val now = System.currentTimeMillis()
                if (memberId.isNotEmpty() && now - lastPresenceAt >= 5_000) {
                    service.touch(memberId)
                    lastPresenceAt = now
                }
                val version = service.eventVersion(request.kind, request.roomId)
                if (lastVersion == Long.MIN_VALUE || version != lastVersion || now - lastSnapshotAt >= 30_000) {
                    val snapshot = try { withContext(Dispatchers.IO) { service.execute(request) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        log.error("Room subscription failed: ${failure.javaClass.simpleName}")
                        RoomReply(ok = false, error = "The hub is temporarily unavailable. Reconnect to resume.", code = "HUB_UNAVAILABLE")
                    }
                    send(Frame.Text(orderJson.encodeToString(snapshot)))
                    if (!snapshot.ok) break
                    memberId = snapshot.memberId
                    lastVersion = service.eventVersion(request.kind, request.roomId)
                    lastSnapshotAt = now
                    lastPresenceAt = now
                }
                delay(250)
            }
        }
    }
}
