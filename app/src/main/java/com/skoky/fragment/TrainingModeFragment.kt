package com.skoky.fragment

import android.app.AlertDialog
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.skoky.MainActivity
import com.skoky.R
import com.skoky.Tools
import com.skoky.fragment.content.Lap
import com.skoky.fragment.content.TrainingModeModel
import com.skoky.services.DecoderBroadcastReceiver
import com.skoky.services.DecoderService.Companion.DECODER_DISCONNECTED
import com.skoky.services.DecoderService.Companion.DECODER_PASSING
import com.skoky.services.PassingBroadcastReceiver
import kotlinx.android.synthetic.main.fragment_trainingmode_list.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONObject
import java.util.concurrent.Future


class TrainingModeFragment : Fragment() {

    private var columnCount = 1

    private var listener: OnListFragmentInteractionListener? = null
    private lateinit var receiver: PassingBroadcastReceiver

    private var startStopButtonM: Button? = null

    private var tmm: TrainingModeModel = TrainingModeModel()    // a dummy model with no transponder
    val transponders = mutableListOf<String>()

    private lateinit var timingContentView: RecyclerView

    private lateinit var clockViewX: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_trainingmode_list, container, false)

        clockViewX = view.findViewById<TextView>(R.id.clockView)
        timingContentView = view.findViewById<RecyclerView>(R.id.training_content)
        // Set the adapter
        if (timingContentView is RecyclerView) {
            with(timingContentView) {
                layoutManager = when {
                    columnCount <= 1 -> LinearLayoutManager(context)
                    else -> GridLayoutManager(context, columnCount)
                }
                adapter = TrainingModeRecyclerViewAdapter(mutableListOf(), listener)

                // view.findViewById<Spinner>(R.id.decoderIdSpinner)
                receiver = PassingBroadcastReceiver()
                receiver.setHandler { data ->
                    val json = JSONObject(data)
                    Log.i(TAG, "Received passing $data")
                    val transponder = json.get("transponder") as Int
                    val time = (json.get("RTC_Time") as String).toLong()

                    if (running) {
                        (adapter as TrainingModeRecyclerViewAdapter).addRecord(transponder, time)
                        adapter.notifyDataSetChanged()
                    }
                    tmm = (adapter as TrainingModeRecyclerViewAdapter).tmm

                    if (!transponders.contains(transponder.toString())) {
                        transponders.add(transponder.toString())
                    }
                }
                context!!.registerReceiver(receiver, IntentFilter(DECODER_PASSING))
            }
        }
        startStopButtonM = view.findViewById<Button>(R.id.startStopButton)
        startStopButtonM!!.setOnClickListener { doStartStopDialog() }

        val disconnectReceiver = DecoderBroadcastReceiver()
        disconnectReceiver.setHandler { _ ->
            context?.let {
                AlertDialog.Builder(it).setMessage(getString(R.string.decoder_not_connected)).setCancelable(true).create().show()
            }

        }
        context!!.registerReceiver(disconnectReceiver, IntentFilter(DECODER_DISCONNECTED))

        return view
    }

    fun openTransponderDialog(startRace: Boolean) {

        val trs = transponders.toTypedArray()

        val b = android.support.v7.app.AlertDialog.Builder(this!!.context!!)
                .setTitle(getString(R.string.select_label))
        if (trs.isEmpty()) {
            b.setMessage(getString(R.string.no_transponder))
        } else {
            b.setSingleChoiceItems(trs, 0) { dialog, i ->
                Log.w(TAG, "Selected $i")
                setSelectedTransponder(trs[i])
                decoderIdSelector.text = trs[i]
                if (startRace) doStartStop()
                dialog.cancel()
            }
        }
        b.create().show()
    }

    fun setSelectedTransponder(transponder: String) {
        tmm.setSelectedTransponder(transponder)
    }

    var running = false
    private fun doStartStopDialog() {

        if (tmm.getSelectedTransponder() == null) {
            openTransponderDialog(true)
            return
        }
        doStartStop()
    }
    private fun doStartStop() {

        if (running) {
            running = false
            clock.cancel(true)      // TODO calculate exact training timeUs
            startStopButtonM?.text = getText(R.string.start)
        } else {    // not running

            if (timingContentView.adapter.itemCount == 1) {     // just a label, nothing to clear
                doStart()
            } else {
                AlertDialog.Builder(context).setTitle("Clear results and start new training?")
                        .setPositiveButton("Yes") { dialog, x ->
                            dialog.cancel()
                            doStart()
                        }
                        .setNegativeButton("No") { dialog, which -> dialog.cancel() }
                        .create().show()
            }
        }

    }

    private lateinit var clock: Future<Unit>

    private var trainingStartTime: Long? = null

    private fun doStart() {
        (timingContentView.adapter as TrainingModeRecyclerViewAdapter).clearResults()
        running = true
        startStopButtonM?.text = getText(R.string.stop)
        trainingStartTime = System.currentTimeMillis()

        clock = doAsync {
            while (true) {
                val timeMs = System.currentTimeMillis() - trainingStartTime!!
                val str = Tools.millisToTimeWithMillis(timeMs)
                uiThread {
                    clockViewX.text = str
                }
                Thread.sleep(30)
            }
        }

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnListFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnListFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        context?.unregisterReceiver(receiver)
    }


    interface OnListFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onListFragmentInteraction(item: Lap?)
    }

    companion object {

        private const val ARG_COLUMN_COUNT = "column-count"
        const val TAG = "TrainingModeFragment"

        @JvmStatic
        fun newInstance(columnCount: Int) =
                TrainingModeFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_COLUMN_COUNT, columnCount)
                    }
                }
    }

}