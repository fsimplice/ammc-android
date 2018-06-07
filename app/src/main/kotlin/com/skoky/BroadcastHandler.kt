package com.skoky

import android.content.Context
import android.content.Intent
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket


object BroadcastHandler {

    private const val TAG = "BroadcastHandler"
    private const val INCOMING_PORT = 5303

    fun receiveBroadcastData(c: Context) {

        Log.w(TAG, "Starting broadcast listener")
        val incomingData = ByteArray(1024)
        var socket: DatagramSocket? = null
        while (true) {
            try {
                socket = DatagramSocket(INCOMING_PORT)
            } catch (e: Exception) {
                Log.i(TAG, "Unable to listen in port $INCOMING_PORT, error $e")
            }
            socket?.let { s ->

                try {
                    while (!s.isClosed) {
                        val incomingPacket = DatagramPacket(incomingData, incomingData.size)
                        s.receive(incomingPacket)
                        val data = incomingPacket.data
                        Log.w(TAG, "Received data length ${incomingPacket.length}")

                        val intent = Intent()
                        intent.action = "com.skoky.decoder.broadcast"
                        intent.putExtra("data", data.copyOf(incomingPacket.length))
                        c.sendBroadcast(intent)
                        Log.w(TAG,"Broadcast sent $intent")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Broadcast socket closed $e")
                } finally {
                    s.let { s.close() }
                    Thread.sleep(1000)
                    Log.i(TAG, "Reconnecting broadcast port $INCOMING_PORT")
                }
            }
        }
    }

}