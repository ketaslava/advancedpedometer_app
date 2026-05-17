package com.ktvincco.advancedpedometer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.ktvincco.advancedpedometer.MainActivity
import com.ktvincco.advancedpedometer.PedometerApplication
import com.ktvincco.advancedpedometer.data.MeasurementCycle
import com.ktvincco.advancedpedometer.data.PathPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PedometerService : Service(), SensorEventListener {

    private val binder = LocalBinder()
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentCycleId: Long = -1
    private var initialSteps: Int = -1
    private var lastLocation: Location? = null
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    inner class LocalBinder : Binder() {
        fun getService(): PedometerService = this@PedometerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { handleNewLocation(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Service was restarted by the system
            startTracking()
        } else {
            when (intent.action) {
                ACTION_START -> startTracking()
                ACTION_STOP -> stopTracking()
            }
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (_isTracking.value) return // Already tracking

        _isTracking.value = true
        val notification = createNotification("Tracking steps and location...")
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            val db = (application as PedometerApplication).database
            val cycle = MeasurementCycle(startTime = System.currentTimeMillis())
            currentCycleId = db.pedometerDao().insertCycle(cycle)
            
            withContext(Dispatchers.Main) {
                stepSensor?.let {
                    sensorManager.registerListener(this@PedometerService, it, SensorManager.SENSOR_DELAY_UI)
                }
                requestLocationUpdates()
            }
        }
    }

    private fun stopTracking() {
        _isTracking.value = false
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        serviceScope.launch {
            val db = (application as PedometerApplication).database
            val cycle = db.pedometerDao().getCycleById(currentCycleId)
            cycle?.let {
                it.endTime = System.currentTimeMillis()
                if (it.totalSteps > 0 || it.totalDistance > 0) {
                    db.pedometerDao().updateCycle(it)
                } else {
                    db.pedometerDao().deleteCycle(it)
                }
            }
            currentCycleId = -1
            initialSteps = -1
            lastLocation = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()
        
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (unlikely: SecurityException) {
        }
    }

    private fun handleNewLocation(location: Location) {
        serviceScope.launch {
            val db = (application as PedometerApplication).database
            val point = PathPoint(
                cycleId = currentCycleId,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )
            db.pedometerDao().insertPoint(point)

            val cycle = db.pedometerDao().getCycleById(currentCycleId)
            cycle?.let {
                if (lastLocation != null) {
                    val distance = lastLocation!!.distanceTo(location).toDouble()
                    it.totalDistance += distance
                }
                db.pedometerDao().updateCycle(it)
            }
            lastLocation = location
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()
            if (initialSteps == -1) {
                initialSteps = totalStepsSinceBoot
            }
            val stepsInCycle = totalStepsSinceBoot - initialSteps
            
            serviceScope.launch {
                val db = (application as PedometerApplication).database
                val cycle = db.pedometerDao().getCycleById(currentCycleId)
                cycle?.let {
                    it.totalSteps = stepsInCycle
                    db.pedometerDao().updateCycle(it)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pedometer Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Advanced Pedometer")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "pedometer_channel"

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    }
}
