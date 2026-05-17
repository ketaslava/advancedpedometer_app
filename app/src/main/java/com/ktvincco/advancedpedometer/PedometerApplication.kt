package com.ktvincco.advancedpedometer

import android.app.Application
import androidx.preference.PreferenceManager
import com.ktvincco.advancedpedometer.data.AppDatabase
import org.osmdroid.config.Configuration
import java.io.File

class PedometerApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        
        val osmConfig = Configuration.getInstance()
        
        // 1. Set a unique and specific User-Agent FIRST. 
        // Using a simple, clean string to avoid parsing issues with some tile servers.
        osmConfig.userAgentValue = "KTVINCCO.AdvancedPedometer"
        
        // 2. Set explicit internal storage paths for the map cache.
        // This is mandatory for Android 10+ (API 29+) to avoid permission issues.
        val basePath = File(filesDir, "osmdroid")
        if (!basePath.exists()) basePath.mkdirs()
        osmConfig.osmdroidBasePath = basePath
        
        val tileCache = File(basePath, "tiles")
        if (!tileCache.exists()) tileCache.mkdirs()
        osmConfig.osmdroidTileCache = tileCache

        // 3. Load preferences. Note: This can overwrite the paths above if they were previously saved.
        osmConfig.load(this, PreferenceManager.getDefaultSharedPreferences(this))
        
        // 4. Force our critical settings to take priority AFTER load()
        osmConfig.userAgentValue = "KTVINCCO.AdvancedPedometer"
        osmConfig.osmdroidBasePath = basePath
        osmConfig.osmdroidTileCache = tileCache
    }
}
