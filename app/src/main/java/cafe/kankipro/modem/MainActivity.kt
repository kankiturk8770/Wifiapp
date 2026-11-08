package cafe.kankipro.modem

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val st: AppState by viewModels()
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always black, so the clock and icons on top must always be light (also when the phone is in light mode).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        SmsWatch.channels(this)
        // New SMS notifications need this permission on Android 13 and later.
        if (Build.VERSION.SDK_INT >= 33 && !SmsWatch.canNotify(this)) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        SmsWatch.start(this)
        openFrom(intent)
        setContent { Theme { App(st) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** A tap on an SMS notification opens the SMS tab. */
    private fun openFrom(i: Intent?) {
        if (i?.getStringExtra("tab") == "sms") { st.pages.clear(); st.tab = Tab.SMS }
    }
}
