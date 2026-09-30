package app.offgrow

import android.app.Application
import app.offgrow.work.RefreshWorker

class OffgrowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }
}
