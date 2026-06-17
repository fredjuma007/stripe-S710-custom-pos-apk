package community.rafiki.pay

import android.app.Application
import com.stripe.stripeterminal.TerminalApplicationDelegate

class RafikiPayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TerminalApplicationDelegate.onCreate(this)
    }
}
