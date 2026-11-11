package com.sslms.console

import android.app.Application
import com.sslms.console.data.Session
import org.osmdroid.config.Configuration
import java.io.File

class SslmsApp : Application() {
    lateinit var session: Session
        private set

    override fun onCreate() {
        super.onCreate()
        session = Session(this)
        // OpenStreetMap tile policy asks for an identifying user agent; keep the tile cache app-private.
        Configuration.getInstance().apply {
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
