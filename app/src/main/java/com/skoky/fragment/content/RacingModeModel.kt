package com.skoky.fragment.content

import org.jetbrains.anko.collections.forEachWithIndex

data class Racer(var pos: Int, val transponder: String, var laps: Int, var lastTimeMs: Long, var lastLapTimeMs: Int)

class RacingModeModel {

    fun newPassing(values: List<Racer>, transponder: Int, time: Long): List<Racer> {

        val found = values.find { it.transponder == transponder.toString() }
        val m = values.toMutableList()
        if (found != null) {
            val i = m.indexOf(found)
            found.laps = found.laps + 1
            // TODO add red or green based on better or worse time
            found.lastLapTimeMs = (time - found.lastTimeMs).toInt()
            found.lastTimeMs = time
            m[i] = found

        } else {
            m.add(Racer(values.size + 1, transponder.toString(), 0, time, 0))
        }

        // TODO calculate diff from first

        val sorted = m.sortedWith(compareByDescending<Racer> { it.laps }.thenBy { it.lastLapTimeMs })

        sorted.forEachWithIndex { i, r ->
            r.pos = i + 1
        }

        return sorted
    }


    companion object {
        private const val TAG = "RacingModeModel"
    }
}