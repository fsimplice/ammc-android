package com.skoky.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.annotation.RequiresApi
import android.util.Log
import com.skoky.MyApp
import com.skoky.NetworkBroadcastHandler
import eu.plib.Parser
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import org.json.JSONObject
import java.net.*
import java.util.*
import kotlin.concurrent.schedule

data class Decoder(val uuid: UUID, var decoderId: String? = null, var ipAddress: String? = null, var decoderType: String? = null,
                   var connection: Socket? = null, var lastSeen: Long) {
    override fun equals(other: Any?): Boolean {
        return uuid == (other as? Decoder)?.uuid
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }

    companion object {
        fun newDecoder(ipAddress: String? = null, decoderId: String? = null): Decoder {
            return Decoder(UUID.randomUUID(), ipAddress = ipAddress, decoderId = decoderId, lastSeen = System.currentTimeMillis())
        }
    }
}

fun MutableList<Decoder>.addOrUpdate(decoder: Decoder) {

    var found = false

    this.forEach { d ->
        if (d.uuid == d.uuid) {
            decoder.decoderId?.let { d.decoderId = it }
            decoder.ipAddress?.let { d.ipAddress = it }
            decoder.decoderType?.let { d.decoderType = it }
            decoder.connection?.let { d.connection = it }
            if (decoder.lastSeen > d.lastSeen) d.lastSeen = decoder.lastSeen
            found = true
        }
    }
    if (!found)
        this.add(decoder)
}

class DecoderService : Service() {

    // Decoder(id = "1111", decoderType = "TranX", ipAddress = "10.0.0.10"))
    private var decoders = mutableListOf<Decoder>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Created")

        decoders.sortedWith(compareBy({ it.uuid }, { it.uuid }))

        Timer().schedule(1000, 1000) {
            // removes inactive decoders
            decoders.removeIf { d ->
                Log.i(TAG, "Decoder $d diff: ${System.currentTimeMillis() - d.lastSeen}")
                val toRemove = (System.currentTimeMillis() - d.lastSeen) > INACTIVE_DECODER_TIMEOUT
                if (toRemove) {
                    Log.i(TAG, "Removing decoder $d, current decoders $decoders")
                    d.connection?.close()
                    sendBroadcastDisconnected(d)
                    true

                } else {
                    false
                }
            }
        }

        doAsync {
            NetworkBroadcastHandler.receiveBroadcastData { processUdpMsg(it) }
        }

    }

    fun getDecoders(): List<Decoder> {
        return decoders.toList()
    }

    fun isDecoderConnected(): Boolean {
        return decoders.any { it.connection != null }
    }

    fun disconnectDecoderByIpUUID(decoderUUID: String) {
        val uuid = UUID.fromString(decoderUUID)
        val found = decoders.find { it.uuid == uuid }
        found?.let { disconnectDecoder2(it) }
    }

    fun connectDecoderByUUID(decoderUUIDString: String) {
        val uuid = UUID.fromString(decoderUUIDString)
        val found = decoders.find { it.uuid == uuid }
        found?.let { connectDecoder2(it) }
    }

    fun connectDecoder2(decoder: Decoder) {

        if (decoder.connection == null) {
            doAsync {
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(decoder.ipAddress, 5403), 5000)
                    decoders.addOrUpdate(decoder.copy(connection = socket, lastSeen = System.currentTimeMillis()))

                    Log.i(TAG, "Decoder $decoder connected")
                    sendBroadcastConnect(decoder)

                    doAsync {
                        listenOnSocketConnection(socket, decoder)
                    }

                    val versionRequest = Parser.encode("{\"recordType\":\"Version\",\"emptyFields\":[\"decoderType\"],\"VERSION\":\"2\"}")
                    socket.getOutputStream().write(versionRequest)
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting decoder", e)
                    socket.close()
                    uiThread {
                        toast("Connection not possible to ${decoder.ipAddress}:5403")
                    }
                }
            }
        } else {
            Log.e(TAG, "Decoder already connected $decoder")
        }
    }

    fun exploreDecoder(uuid: UUID) {
        val socket = decoders.find { it.uuid == uuid }?.connection

        doAsync {
            socket?.let { s ->
                if (s.isBound) {
                    val versionRequest = Parser.encode("{\"recordType\":\"Version\",\"emptyFields\":[\"decoderType\"],\"VERSION\":\"2\"}")
                    s.getOutputStream().write(versionRequest)
                    val statusRequest = Parser.encode("{\"recordType\":\"Status\",\"emptyFields\":[" +
                            "\"loopTriggers\",\"noise\",\"gps\", \"temperature-text\",\"inputVoltage-text\"],\"VERSION\":\"2\"}")
                    s.getOutputStream().write(statusRequest)

                    // TODO send all possible messages
                    // FIXME make sure Console Fragment caches all fields

                }
            }
        }

    }

    private fun disconnectDecoder2(decoder: Decoder) {

        try {
            decoder.connection?.let {
                it.close()
            }
            // cleanup
            decoder.connection = null
            sendBroadcastDisconnected(decoder)

        } catch (e: Exception) {
            Log.w(TAG, "Unable to disconnect decoder $decoder", e)
        }
    }

    private fun listenOnSocketConnection(socket: Socket, orgDecoder: Decoder) {
        val buffer = ByteArray(1024)
        var decoder = orgDecoder
        try {
            var read = 0
            while (socket.isBound && read != -1) {
                socket.getInputStream()?.let {
                    read = it.read(buffer)
                    Log.i(TAG, "Received $read bytes")
                    if (read > 0) {
                        val json = JSONObject(Parser.decode(buffer.copyOf(read)))
                        if (json.get("recordType").toString().isNotEmpty()) sendBroadcastData(decoder, json.toString())
                        when {
                            json.get("recordType").toString() == "Passing" -> sendBroadcastPassing(json.toString())
                            json.get("recordType").toString() == "Version" -> {
                                val decoderType = json.get("decoderType-text") as? String
                                decoders.addOrUpdate(decoder.copy(decoderType = decoderType))
                                sendBroadcastData(decoder, json.toString())
                            }
                            else -> Log.w(TAG, "received unknown data $json")
                        }
                        json.get("decoderId")?.let { id -> decoders.addOrUpdate(decoder.copy(decoderId = id as String)) }
                        decoders.addOrUpdate(decoder.copy(lastSeen = System.currentTimeMillis()))
                    }
                }
                decoders.find { it.uuid == decoder.uuid }?.let { decoder = it }
            }
            Log.i(TAG, "Bound ${socket.isBound}, read $read")
        } catch (e: Exception) {
            Log.w(TAG, "Decoder connection error $decoder", e)
        } catch (t: Throwable) {
            Log.e(TAG, "Decoder connection throwable $decoder", t)
        } finally {
            decoder.connection?.close()
            decoder.connection = null
            Log.i(TAG, "Decoder disconnected")
            sendBroadcastDisconnected(decoder)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun processUdpMsg(msgB: ByteArray) {
        Log.w(TAG, "Data received: ${msgB.size}")
        val msg = Parser.decode(msgB)
        val json = JSONObject(msg)


        var decoderId: String?
        if (json.has("decoderId")) {
            decoderId = json.get("decoderId") as String
        } else {
            Log.w(TAG, "Received P3 message without decoderId. Wired! $json")
            return
        }

        var decoder = decoders.find { it.decoderId == decoderId }
        if (decoder == null) decoder = Decoder.newDecoder(decoderId = decoderId)

        decoder?.let { d ->

            if (json.has("recordType")) when (json.get("recordType")) {
                "Status" -> {
                    sendUdpNetworkRequest()
                    sendUdpVersionRequest()
                    sendBroadcastData(d, json.toString())
                    decoders.addOrUpdate(decoder)
                }
                "NetworkSettings" ->
                    if (json.has("activeIPAddress")) {
                        val ipAddress = json.get("activeIPAddress") as? String
                        decoders.addOrUpdate(decoder.copy(ipAddress = ipAddress))
                        sendBroadcastData(d, json.toString())
                    }
                "Version" ->
                    if (json.has("decoderType")) {
                        val decoderType = json.get("decoderType-text") as? String
                        decoders.addOrUpdate(decoder.copy(decoderType = decoderType))
                        sendBroadcastData(d, json.toString())
                    }
            }
            Log.i(TAG, "Decoders: $decoders")
        }
    }

    private fun sendUdpVersionRequest() {
        sendUdpBroadcastMessage("{\"recordType\":\"Version\",\"emptyFields\":[\"decoderType\"],\"VERSION\":\"2\"}")
    }

    private fun sendUdpNetworkRequest() {
        sendUdpBroadcastMessage("{\"recordType\":\"NetworkSettings\",\"emptyFields\":[\"activeIPAddress\"],\"VERSION\":\"2\"}")
    }

    private fun sendUdpBroadcastMessage(msg: String) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(5403)
            socket.broadcast = true
            socket.connect(InetAddress.getByName("255.255.255.255"), 5403)
            val bytes = Parser.encode(msg)
            Log.w(TAG, "Bytes size ${bytes.size}")
            socket.send(DatagramPacket(bytes, bytes.size))
        } catch (e: Exception) {
            Log.w(TAG, "Error $e", e)
        } finally {
            socket?.close()
        }
    }

    private fun sendBroadcastConnect(decoder: Decoder) {
        val intent = Intent()
        intent.action = DECODER_CONNECT
        intent.putExtra("uuid", decoder.uuid.toString())
        applicationContext.sendBroadcast(intent)
        Log.w(TAG, "Broadcast sent $intent")
    }

    private fun sendBroadcastDisconnected(decoder: Decoder) {
        val intent = Intent()
        intent.action = DECODER_DISCONNECTED
        intent.putExtra("uuid", decoder.uuid.toString())
        // TBD more data as it is not in decoders anymore
        applicationContext.sendBroadcast(intent)
        Log.w(TAG, "Broadcast sent $intent")
    }

    private fun sendBroadcastPassing(jsonData: String) {
        val intent = Intent()
        intent.action = DECODER_PASSING
        intent.putExtra("Passing", jsonData)
        applicationContext.sendBroadcast(intent)
        Log.w(TAG, "Broadcast passing sent $intent")
    }

    private fun sendBroadcastData(decoder: Decoder?, jsonData: String) {
        val intent = Intent()
        intent.action = DECODER_DATA
        intent.putExtra("Data", jsonData)
        decoder?.let { intent.putExtra("uuid", it.toString()) }
        applicationContext.sendBroadcast(intent)
        Log.w(TAG, "Broadcast data sent $intent")
    }

    private val myBinder = MyLocalBinder()
    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Destroyed")
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.w(TAG, "rebind")
    }

    override fun onBind(intent: Intent): IBinder? {
        return myBinder
    }

    fun connectDecoder(address: String): Boolean {
        Log.d(TAG, "Connecting to $address")

        val foundByIp = decoders.find { it.ipAddress == address }
        if (foundByIp != null)
            connectDecoder2(foundByIp)
        else {  // create new decoder
            connectDecoder2(Decoder.newDecoder(ipAddress = address))
        }
        return true
    }

    inner class MyLocalBinder : Binder() {
        fun getService(): DecoderService {
            return this@DecoderService
        }
    }

    companion object {
        private const val TAG = "DecoderService"
        const val DECODER_CONNECT = "com.skoky.decoder.broadcast.connect"
        const val DECODER_DATA = "com.skoky.decoder.broadcast.data"
        const val DECODER_PASSING = "com.skoky.decoder.broadcast.passing"
        const val DECODER_DISCONNECTED = "com.skoky.decoder.broadcast.disconnected"
        private const val INACTIVE_DECODER_TIMEOUT: Long = 10000  // 10secs
    }
}
