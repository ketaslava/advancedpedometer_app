package com.ktvincco.advancedpedometer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ktvincco.advancedpedometer.data.MeasurementCycle
import com.ktvincco.advancedpedometer.data.PathPoint
import com.ktvincco.advancedpedometer.ui.PedometerMap
import com.ktvincco.advancedpedometer.ui.PedometerViewModel
import com.ktvincco.advancedpedometer.ui.theme.AdvancedPedometerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdvancedPedometerTheme {
                MainNavigation()
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val viewModel: PedometerViewModel = viewModel()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onDayClick = { dateMillis -> navController.navigate("day_detail/$dateMillis") },
                onSettingsClick = { navController.navigate("settings") },
                onEditorOpen = { navController.navigate("editor") }
            )
        }
        composable(
            "day_detail/{dateMillis}",
            arguments = listOf(navArgument("dateMillis") { type = NavType.LongType })
        ) { backStackEntry ->
            val dateMillis = backStackEntry.arguments?.getLong("dateMillis") ?: 0L
            DayDetailScreen(viewModel, dateMillis, onBack = { navController.popBackStack() })
        }
        composable("settings") { SettingsScreen(viewModel, onBack = { navController.popBackStack() }) }
        composable("editor") { EditorScreen(viewModel, onBack = { navController.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: PedometerViewModel,
    onDayClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onEditorOpen: () -> Unit
) {
    val context = LocalContext.current
    val allCycles by viewModel.allCycles.collectAsState()
    val totalStepsOverall by viewModel.totalSteps.collectAsState()
    val isImperial by viewModel.isImperial.collectAsState()
    val isTracking by viewModel.isServiceTracking.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // Permissions granted
        } else {
            Toast.makeText(context, "Permissions required for tracking.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        
        if (!permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Advanced Pedometer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isTracking) Color(0xFF81C784) else MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = {
                            if (isTracking) {
                                viewModel.stopTracking(context)
                            } else {
                                val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                                    viewModel.startTracking(context)
                                } else {
                                    permissionLauncher.launch(permissions.toTypedArray())
                                }
                            }
                        }
                    ) { Text(if (isTracking) "Stop" else "Start") }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = { onSettingsClick() },
                                    onLongClick = { onEditorOpen() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Settings", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Quick Stats
                val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                val yesterday = today - 86400000L
                
                val todayCycles = allCycles.filter { it.startTime >= today }
                val yesterdayCycles = allCycles.filter { it.startTime in yesterday until today }
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Today Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDayClick(today) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Today", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                            Text("${todayCycles.sumOf { it.totalSteps }} steps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(formatDistance(todayCycles.sumOf { it.totalDistance }, isImperial), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    
                    // Yesterday Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDayClick(yesterday) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Yesterday", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text("${yesterdayCycles.sumOf { it.totalSteps }} steps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(formatDistance(yesterdayCycles.sumOf { it.totalDistance }, isImperial), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Overall Stats Block
                    val totalDist = allCycles.sumOf { it.totalDistance }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Overall Statistics", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("${totalStepsOverall ?: 0} steps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(formatDistance(totalDist, isImperial), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Grouping logic: Year -> Month -> Days
            val groupedByYear = allCycles.groupBy { 
                Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.YEAR)
            }

            groupedByYear.forEach { (year, yearCycles) ->
                item { 
                    val yearSteps = yearCycles.sumOf { it.totalSteps }
                    val yearDist = yearCycles.sumOf { it.totalDistance }
                    Spacer(modifier = Modifier.height(16.dp))
                    HeaderInsert(text = "$year", subtext = "$yearSteps steps | ${formatDistance(yearDist, isImperial)}") 
                }

                val groupedByMonth = yearCycles.groupBy {
                    Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.MONTH)
                }

                groupedByMonth.forEach { (month, monthCycles) ->
                    item { 
                        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().apply { set(Calendar.MONTH, month) }.time)
                        val monthSteps = monthCycles.sumOf { it.totalSteps }
                        val monthDist = monthCycles.sumOf { it.totalDistance }
                        Spacer(modifier = Modifier.height(8.dp))
                        MonthInsert(text = "$monthName $year", subtext = "$monthSteps steps | ${formatDistance(monthDist, isImperial)}") 
                    }

                    // Group by day
                    val groupedByDay = monthCycles.groupBy {
                        val cal = Calendar.getInstance().apply { timeInMillis = it.startTime }
                        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        cal.timeInMillis
                    }
                    
                    groupedByDay.forEach { (dateMillis, dayCycles) ->
                        item {
                            DayBlockButton(
                                date = Date(dateMillis),
                                steps = dayCycles.sumOf { it.totalSteps },
                                distance = dayCycles.sumOf { it.totalDistance },
                                isImperial = isImperial,
                                onClick = { onDayClick(dateMillis) }
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun HeaderInsert(text: String, subtext: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(subtext, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MonthInsert(text: String, subtext: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtext,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun DayBlockButton(date: Date, steps: Int, distance: Double, isImperial: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SimpleDateFormat("dd MMMM", Locale.getDefault()).format(date),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$steps steps",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = formatDistance(distance, isImperial),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(viewModel: PedometerViewModel, dateMillis: Long, onBack: () -> Unit) {
    val allCycles by viewModel.allCycles.collectAsState()
    val isImperial by viewModel.isImperial.collectAsState()
    val dayCycles = allCycles.filter {
        val cal = Calendar.getInstance().apply { timeInMillis = it.startTime }
        val dayCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        cal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)
    }

    val totalSteps = dayCycles.sumOf { it.totalSteps }
    val totalDist = dayCycles.sumOf { it.totalDistance }
    
    val aggregatedPoints = remember { mutableStateListOf<PathPoint>() }
    LaunchedEffect(dayCycles) {
        aggregatedPoints.clear()
        dayCycles.forEach { cycle ->
            val points = viewModel.getPointsForCycle(cycle.id).first()
            aggregatedPoints.addAll(points)
            if (points.isNotEmpty()) {
                aggregatedPoints.add(PathPoint(cycleId = cycle.id, latitude = 0.0, longitude = 0.0, timestamp = 0, isBreak = true))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Day Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "") } }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize()) {
            Text(SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date(dateMillis)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBox("Steps", "$totalSteps")
                StatBox("Distance", formatDistance(totalDist, isImperial))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                PedometerMap(modifier = Modifier.fillMaxSize(), points = aggregatedPoints)
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: PedometerViewModel, onBack: () -> Unit) {
    val allCycles by viewModel.allCycles.collectAsState()
    val isImperial by viewModel.isImperial.collectAsState()
    var editingCycle by remember { mutableStateOf<MeasurementCycle?>(null) }
    var cycleToDelete by remember { mutableStateOf<MeasurementCycle?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newRecordDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())) }
    
    val points by if (editingCycle != null) viewModel.getPointsForCycle(editingCycle!!.id).collectAsState(initial = emptyList()) else remember { mutableStateOf(emptyList()) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Record") },
            text = {
                Column {
                    Text("Enter date and time for the new record:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newRecordDate,
                        onValueChange = { newRecordDate = it },
                        label = { Text("Date (dd/MM/yyyy HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).parse(newRecordDate)
                        if (date != null) {
                            viewModel.addCycle(MeasurementCycle(startTime = date.time))
                            showAddDialog = false
                        }
                    } catch (e: Exception) {
                        // Invalid date format
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (cycleToDelete != null) {
        AlertDialog(
            onDismissRequest = { cycleToDelete = null },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete this record? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCycle(cycleToDelete!!)
                    cycleToDelete = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { cycleToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Data Editor") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "") } }) }) { innerPadding ->
        if (editingCycle == null) {
            LazyColumn(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                item { 
                    Button(onClick = { 
                        newRecordDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                        showAddDialog = true
                    }, modifier = Modifier.fillMaxWidth()) { 
                        Text("Add New Record") 
                    } 
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val groupedByYear = allCycles.groupBy { 
                    Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.YEAR)
                }

                groupedByYear.forEach { (year, yearCycles) ->
                    item { 
                        Spacer(modifier = Modifier.height(16.dp))
                        HeaderInsert(text = "$year", subtext = "${yearCycles.size} records") 
                    }

                    val groupedByMonth = yearCycles.groupBy {
                        Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.MONTH)
                    }

                    groupedByMonth.forEach { (month, monthCycles) ->
                        item { 
                            val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().apply { set(Calendar.MONTH, month) }.time)
                            Spacer(modifier = Modifier.height(8.dp))
                            MonthInsert(text = "$monthName $year", subtext = "${monthCycles.size} records") 
                        }

                        items(monthCycles) { cycle ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(cycle.startTime)), fontWeight = FontWeight.Bold)
                                    Text("${cycle.totalSteps} steps | ${formatDistance(cycle.totalDistance, isImperial)}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { editingCycle = cycle }) { Icon(Icons.Default.Edit, "") }
                                IconButton(onClick = { cycleToDelete = cycle }) { Icon(Icons.Default.Delete, "", tint = Color.Red) }
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        } else {
            Column(modifier = Modifier.padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Edit Record", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                var dateString by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(editingCycle!!.startTime))) }
                
                OutlinedTextField(
                    value = dateString,
                    onValueChange = { 
                        dateString = it
                        try {
                            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).parse(it)
                            if (date != null) editingCycle = editingCycle!!.copy(startTime = date.time)
                        } catch (e: Exception) {}
                    },
                    label = { Text("Date (dd/MM/yyyy HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = editingCycle!!.totalSteps.toString(), 
                    onValueChange = { editingCycle = editingCycle!!.copy(totalSteps = it.toIntOrNull() ?: 0) }, 
                    label = { Text("Total Steps") }, 
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = editingCycle!!.totalDistance.toString(), 
                    onValueChange = { editingCycle = editingCycle!!.copy(totalDistance = it.toDoubleOrNull() ?: 0.0) }, 
                    label = { Text("Total Distance (meters)") }, 
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Map Drawing (Tap to add dots)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    PedometerMap(
                        modifier = Modifier.fillMaxSize(),
                        points = points, 
                        onMapClick = { geoPoint ->
                            viewModel.addPoint(PathPoint(
                                cycleId = editingCycle!!.id,
                                latitude = geoPoint.latitude,
                                longitude = geoPoint.longitude,
                                timestamp = System.currentTimeMillis()
                            ))
                        }, 
                        showMyLocation = true,
                        isEditor = true
                    ) 
                }

                val suggestedStats = remember(points) {
                    var totalDist = 0.0
                    var lastP: PathPoint? = null
                    points.forEach { p ->
                        if (!p.isBreak && (p.latitude != 0.0 || p.longitude != 0.0)) {
                            if (lastP != null && !lastP!!.isBreak && (lastP!!.latitude != 0.0 || lastP!!.longitude != 0.0)) {
                                val results = FloatArray(1)
                                android.location.Location.distanceBetween(
                                    lastP!!.latitude, lastP!!.longitude,
                                    p.latitude, p.longitude,
                                    results
                                )
                                totalDist += results[0]
                            }
                        }
                        lastP = p
                    }
                    val steps = (totalDist * 1.31).toInt()
                    Pair(steps, totalDist)
                }

                Text(
                    text = "Suggested values based on the path: ${suggestedStats.first} steps, ${String.format("%.1f", suggestedStats.second)} meters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.deleteLastPointForCycle(editingCycle!!.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Remove The Last Waypoint")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { viewModel.addPoint(PathPoint(cycleId = editingCycle!!.id, latitude = 0.0, longitude = 0.0, timestamp = System.currentTimeMillis(), isBreak = true)) }, modifier = Modifier.weight(1f)) { Text("Add Gap") }
                    Button(onClick = { viewModel.deletePointsForCycle(editingCycle!!.id) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), modifier = Modifier.weight(1f)) { Text("Clear Path") }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { editingCycle = null }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { 
                        viewModel.updateCycle(editingCycle!!)
                        editingCycle = null 
                    }, modifier = Modifier.weight(1f)) { Text("Save") }
                }
                // Extra spacer to ensure content isn't cut off by bottom of screen
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: PedometerViewModel, onBack: () -> Unit) {
    val isImperial by viewModel.isImperial.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Use US Imperial System", style = MaterialTheme.typography.bodyLarge)
                    Text("Display distance in miles instead of kilometers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = isImperial,
                    onCheckedChange = { viewModel.setImperial(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("App Version: 1.0", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tracking is active in the background when 'Start' is pressed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

fun formatDistance(meters: Double, isImperial: Boolean): String {
    return if (isImperial) {
        val miles = meters * 0.000621371
        String.format("%.2f mi", miles)
    } else {
        String.format("%.2f km", meters / 1000.0)
    }
}
