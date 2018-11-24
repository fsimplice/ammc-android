package com.skoky.fragment

import android.app.Fragment
import android.util.Log
import com.skoky.Tools.reportEvent
import org.jetbrains.anko.doAsync
import org.json.JSONObject


data class Time(val us: Long)

class FragmentCommon : Fragment() {


    fun getTimeFromPassingJson(json: JSONObject): Time {
        return when {
            json.has("RTC_Time") -> Time((json.get("RTC_Time") as String).toLong())
            json.has("UTC_Time") -> Time((json.get("UTC_Time") as String).toLong())
            json.has("msecs_since_start") ->
                Time((json.get("msecs_since_start") as Integer).toLong()*1000)
            else -> {
                Log.w(TrainingModeFragment.TAG, "No time in passing record $json")
                doAsync {
                    reportEvent(activity.application, "passing-no-time", json.toString())
                }
                return Time(0L)
            }
        }
    }

    fun getTransponderFromPassingJson(json: JSONObject): String {

        return when {
            json.has("transponder") -> (json.get("transponder") as Int).toString()
            json.has("transponderCode") -> json.get("transponderCode") as String
            json.has("driverId") -> json.get("driverId") as String
            else -> {
                Log.w(TrainingModeFragment.TAG, "No racer identification in Passing $json")
                doAsync {
                    reportEvent(activity.application, "passing_not_transponder", json.toString())
                }

                return "---"
            }
        }


    }
}