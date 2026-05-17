package com.ktvincco.advancedpedometer.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.ktvincco.advancedpedometer.PedometerApplication
import com.ktvincco.advancedpedometer.data.MeasurementCycle
import com.ktvincco.advancedpedometer.data.PathPoint
import com.ktvincco.advancedpedometer.service.PedometerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PedometerViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as PedometerApplication).database.pedometerDao()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    private val _isImperial = MutableStateFlow(prefs.getBoolean("is_imperial", false))
    val isImperial = _isImperial.asStateFlow()

    val isServiceTracking = PedometerService.isTracking

    fun setImperial(enabled: Boolean) {
        _isImperial.value = enabled
        prefs.edit().putBoolean("is_imperial", enabled).apply()
    }

    val allCycles = dao.getAllCycles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totalSteps = dao.getTotalSteps().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun getPointsForCycle(cycleId: Long): Flow<List<PathPoint>> {
        return dao.getPointsForCycle(cycleId)
    }

    fun startTracking(context: Context) {
        val intent = Intent(context, PedometerService::class.java).apply {
            action = PedometerService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stopTracking(context: Context) {
        val intent = Intent(context, PedometerService::class.java).apply {
            action = PedometerService.ACTION_STOP
        }
        context.startService(intent)
    }
    
    fun deleteCycle(cycle: MeasurementCycle) {
        viewModelScope.launch {
            dao.deleteCycle(cycle)
        }
    }
    
    fun updateCycle(cycle: MeasurementCycle) {
        viewModelScope.launch {
            dao.updateCycle(cycle)
        }
    }

    fun addCycle(cycle: MeasurementCycle) {
        viewModelScope.launch {
            dao.insertCycle(cycle)
        }
    }

    fun addPoint(point: PathPoint) {
        viewModelScope.launch {
            dao.insertPoint(point)
        }
    }
    
    fun deletePointsForCycle(cycleId: Long) {
        viewModelScope.launch {
            dao.deletePointsForCycle(cycleId)
        }
    }

    fun deleteLastPointForCycle(cycleId: Long) {
        viewModelScope.launch {
            dao.deleteLastPoint(cycleId)
        }
    }
}
