package com.skoky

import com.google.gson.GsonBuilder


const val DELIM = "\t"
const val VOSTOK_ID = "101"
const val VOSTOK_NAME = "Vostok"
const val VOSTOK_NAME_LONG = "Vostok lap timing system"

data class Status(val recordType: String, val decoderType: String, val decoderId: String, val num: Int, val noise: Int, val crcOk: Boolean)
data class Passing(val recordType: String, val decoderType: String, val decoderId: String, val num: Int, val transponderCode: String,
                   val timeSinceStart: Float, val hitCounts: Int, val signalStrength: Int,
                   val passingStatus: Int, val crcOk: Boolean,
                   val RTC_Time: String)

data class Error(val recordType: String, val msg: String)

val gson = GsonBuilder().setPrettyPrinting().create()

object P98Parser {
    fun parse(msg: ByteArray, id: String): String {
        return try {
            when (msg[0].toChar()) {
                '#' -> parserStatus(msg, id)
                '@' -> parsePassing(msg, id)
                else -> makeError("unknown 98 record type")
            }
        } catch (e: Exception) {
            makeError("${e.message}")
        }
    }

    private fun parserStatus(msg: ByteArray, id: String): String {
        val fields = String(msg).split(DELIM)
        if (fields.size != 5) return makeError("Status does not have 5 fields - ${fields.size} / ${String(msg)}")
        val isCrcOk = checkCrc(msg, fields[4])
        val type = if (fields[1] == VOSTOK_ID) VOSTOK_NAME else ""
        val status = Status(
                recordType = "Status",
                decoderType = type,
                decoderId = id,
                num = fields[2].toInt(),
                noise = fields[3].toInt(),
                crcOk = isCrcOk
        )
        return gson.toJson(status)
    }

    private fun parsePassing(msg: ByteArray, id: String): String {

        val fields = String(msg).split(DELIM)
        if (fields.size != 9) return makeError("Passing does not have 9 fields")

        val isCrcOk = checkCrc(msg, fields[8])
        val type = if (fields[1] == VOSTOK_ID) VOSTOK_NAME else ""
        val tss = fields[4].toFloat()
        val passing = Passing(
                recordType = "Passing",
                decoderType = type,
                decoderId = id,
                num = fields[2].toInt(),
                transponderCode = fields[3],
                timeSinceStart = tss,
                hitCounts = fields[5].toInt(),
                signalStrength = fields[6].toInt(),
                passingStatus = fields[7].toInt(),
                crcOk = isCrcOk,
                RTC_Time = (tss * 1000).toInt().toString()
        )
        return gson.toJson(passing)
    }

    private fun makeError(msg: String): String {
        return gson.toJson(Error("Error", msg))
    }


    private fun checkCrc(msg: ByteArray, crc: String): Boolean {
        return true
    }

    // does not work
//    //        receivedCrc = Integer.parseInt(ps.getCrc().replaceAll("x", ""), 16).toShort()
//    //        if (crcCalc === receivedCrc) ps.setCrcOk(true)
//
//    fun calcCrc(data: ByteArray): Short {
//        val dataToCrc = ByteArray(data.size - 6)
//        System.arraycopy(data, 1, dataToCrc, 0, dataToCrc.size)
//        return CRC16.cmpCRC(dataToCrc)
//    }


}