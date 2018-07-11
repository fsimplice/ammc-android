package com.skoky

//import eu.plib.P3tools.MsgProcessor
//import eu.plib.Ptools.Bytes
//import eu.plib.Ptools.ProtocolsEnum
import android.content.*
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.NavigationView
import android.support.v4.app.Fragment
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.skoky.config.ConfigTool
import com.skoky.fragment.StartupFragment
import com.skoky.fragment.TrainingModeFragment
import com.skoky.fragment.content.Lap
import com.skoky.services.DecoderService
import com.skoky.timing.data.DatabaseHelper
import kotlinx.android.synthetic.main.fragment_trainingmode_list.*
import org.jetbrains.anko.AlertBuilder


class MainActivity : AppCompatActivity(), TrainingModeFragment.OnListFragmentInteractionListener {

    override fun onListFragmentInteraction(item: Lap?) {
        Log.w(TAG, "Interaction $item")
    }

    private lateinit var app: MyApp
    private lateinit var mDrawerLayout: DrawerLayout

    //    private var timerThread: TimerThread? = null

    private val initialIPAddress: String
        get() {
            val address = ConfigTool.storedAddress

            Log.d(TAG, "Stored address is $address")
            return address
        }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as MyApp
        MyApp.setCachedApplicationContext(this)
        app.dbHelper = DatabaseHelper(this)
//        setContentView(R.layout.main)

        setContentView(R.layout.main)
        mDrawerLayout = findViewById(R.id.drawer_layout)

        val navigationView: NavigationView = this.findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            // set item as selected to persist highlight
            menuItem.isChecked = true
            // close drawer when item is tapped
            mDrawerLayout.closeDrawers()

            // Add code here to update the UI based on the item selected
            // For example, swap UI fragments here

            Toast.makeText(applicationContext, "Item: $menuItem", Toast.LENGTH_SHORT).show()
            true
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val actionbar: ActionBar? = supportActionBar
        actionbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_black_24dp)
        }

        openStartupFragment()

        Log.w(TAG, "Binding service")
        val intent = Intent(this, DecoderService::class.java)
        bindService(intent, decoderServiceConnection, Context.BIND_AUTO_CREATE)


//        screenHandler = screeHandler
//        screenHandler!!.updateScreenForMode(ConfigTool.mode)
//        tryReconnect()
    }

    private fun openStartupFragment() {
        val fr: Fragment = StartupFragment.newInstance(1)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.screen_container, fr)
        fragmentTransaction.commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    fun connectOrDisconnect(view: View) {
        app.decoderService!!.connectOrDisconnectFirstDecoder()
    }

    fun showMoreDecoders(view: View) {
        Log.i(TAG, "TBD")
    }

    fun openRacingMode(view: View) {
        Log.i(TAG, "TBD")
    }


    fun openTransponderDialog(view: View) {
        val trs = trainingFragment.transponders.toTypedArray()
        AlertDialog.Builder(this@MainActivity)
                .setTitle("Select transponder to watch")
                .setSingleChoiceItems(trs,0) { dialog, i ->
                    Log.w(TAG,"Selected $i")
                    trainingFragment.setSelectedTransponder(trs[i])
                    decoderIdSelector.text = trs[i]
                    dialog.cancel()
                }.create().show()
    }

    private lateinit var trainingFragment : TrainingModeFragment
    fun openTrainingMode(view: View) {

        app.decoderService?.let {
            if (it.isDecoderConnected()) {
                trainingFragment = TrainingModeFragment.newInstance(1)
                val fragmentTransaction = supportFragmentManager.beginTransaction()
                fragmentTransaction.replace(R.id.screen_container, trainingFragment)
                fragmentTransaction.commit()
            } else {
                AlertDialog.Builder(this).setMessage(getString(R.string.decoder_not_connected)).setCancelable(true).create().show()
            }
        }

    }

    private val decoderServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName,
                                        service: IBinder) {

            val binder = service as DecoderService.MyLocalBinder
            app.decoderService = binder.getService()

            app.decoderService?.let {
                Log.w(TAG, "Decoder service bound")
                Log.d(TAG, "Service -> " + it.connectDecoder("aDecoder"))
                it.listenOnDecodersBroadcasts()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            app.decoderService = null
            Log.w(TAG,"Service disconnected?")
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.i(TAG,"Option menu ${item.itemId}")
        return when (item.itemId) {
            com.skoky.R.id.miHome -> {
                openStartupFragment()
                true
            }
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show()
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show()
        }
        // Checks whether a hardware keyboard is available
        if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
            Toast.makeText(this, "keyboard visible", Toast.LENGTH_SHORT).show()
        } else if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
            Toast.makeText(this, "keyboard hidden", Toast.LENGTH_SHORT).show()
        }

        Log.d(TAG, "Layout:" + newConfig.screenLayout)
    }


    override fun onDestroy() {
        super.onDestroy()

    }

    companion object {
        private val TAG = "MainActivity"
    }


}