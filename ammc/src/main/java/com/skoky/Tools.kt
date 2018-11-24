package com.skoky


import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.FULL_WAKE_LOCK
import android.util.Log
import android.widget.Toast
import java.net.NetworkInterface
import java.net.SocketException

object Tools {

    private val TAG = "P3Tools"

    var P3_DEF_PORT = 5403

    val localIpAddress: String?
        get() {
            try {
                val en = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val intf = en.nextElement()
                    val enumIpAddr = intf.inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress = enumIpAddr.nextElement()
                        if (!inetAddress.isLoopbackAddress) {
                            return inetAddress.hostAddress.toString()
                        }
                    }
                }
            } catch (ex: SocketException) {
                Log.e(TAG, ex.toString())
            }

            return null
        }

    fun getAddress(text: CharSequence): String {
        var add = ""
        val a = text.toString()
        val spl = a.split(":".toRegex(), 2).toTypedArray()
        if (spl.size > 0) add = spl[0]
        Log.d(TAG, "Returning address $add")
        return add
    }

    fun getPort(text: CharSequence): Int {
        var port = P3_DEF_PORT
        val a = text.toString()
        val spl = a.split(":".toRegex(), 2).toTypedArray()
        if (spl.size > 1) {
            val n = spl[1]
            try {
                port = Integer.parseInt(n)
            } catch (e: NumberFormatException) {  // keep def port
            }

        }

        Log.d(TAG, "Returning port $port")
        return port
    }

    fun checkInt(text: CharSequence): Boolean {
        try {
            Integer.parseInt(text.toString())
            return true

        } catch (e: Exception) {
            Log.d(TAG, "Not int $text")
            return false
        }

    }


    fun millisToTime(duration: Long): String {

        val seconds = (duration / 1000).toInt() % 60
        val minutes = (duration / (1000 * 60) % 60).toInt()

        return String.format("%d:%02d", minutes, seconds)
    }

    fun millisToTimeWithMillis(milliseconds: Long): String {

        val mils = milliseconds.toInt() % 1000
        val seconds = (milliseconds / 1000).toInt() % 60
        val minutes = (milliseconds / (1000 * 60) % 60).toInt()

        return String.format("%d:%02d.%03d", minutes, seconds, mils)

    }

    fun wakeLock(context: Context, b: Boolean) {
        if (b) {
            // FIXME use smaller wake lock on new androids
            MyApp.wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(FULL_WAKE_LOCK , "AMMC:Race running").apply {
                    acquire()
                }

            }
        } else {
            MyApp.wakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        }
    }

    fun reportEvent(app: Application, key: String, value: String) {
        val params = Bundle()
        params.putString(key, value)
        (app as MyApp).firebaseAnalytics.logEvent("AmmcAndroid", params)

    }
}
