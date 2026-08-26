package com.light.medication

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.light.medication.data.Reminder
import com.light.medication.util.TimeUtils
import com.light.medication.util.AppUpdater
import com.light.medication.util.GitHubRelease
import com.light.medication.viewmodel.ReminderViewModel
import com.light.medication.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationReminderScreen(viewModel: ReminderViewModel = viewModel()) {
    val context = LocalContext.current
    var showAboutScreen by remember { mutableStateOf(false) }
    var showLogScreen by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<Reminder?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, context.getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val reminders by viewModel.allReminders.collectAsState()
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600 || configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isBoxyScreen = !isWideScreen && (configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat() > 0.75f)

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportBackup(it,
                onSuccess = { Toast.makeText(context, context.getString(R.string.backup_success), Toast.LENGTH_SHORT).show() },
                onError = { e -> Toast.makeText(context, context.getString(R.string.backup_failed, e.message), Toast.LENGTH_LONG).show() }
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.restoreBackup(it,
                onSuccess = { Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_SHORT).show() },
                onError = { e -> Toast.makeText(context, context.getString(R.string.restore_failed, e.message), Toast.LENGTH_LONG).show() }
            )
        }
    }

    if (showLogScreen) {
        MedicationLogScreen(
            onBack = { showLogScreen = false },
            viewModel = viewModel
        )
    } else if (showAboutScreen) {
        AboutScreen(
            onBack = { showAboutScreen = false },
            onBackup = { backupLauncher.launch("medilight_backup.json") },
            onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
            compact = isBoxyScreen
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.screen_title)) },
                    actions = {
                        IconButton(onClick = { showLogScreen = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.log_title))
                        }
                        IconButton(onClick = { showAboutScreen = true }) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about_button))
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, FloatingActionButtonDefaults.shape),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = FloatingActionButtonDefaults.shape
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_reminder_title))
                }
            }
        ) { padding ->
            if (isWideScreen) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        BatteryOptimizationBanner(compact = isBoxyScreen)
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        ExactAlarmPermissionBanner(compact = isBoxyScreen)
                    }
                    if (reminders.isEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_reminders), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else {
                        items(reminders) { reminder ->
                            ReminderItem(
                                reminder = reminder,
                                onDelete = { viewModel.deleteReminder(it) },
                                onToggle = { viewModel.toggleReminder(it) },
                                onEdit = { reminderToEdit = it },
                                onTake = { viewModel.markAsTaken(it) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = if (isBoxyScreen) 8.dp else 16.dp,
                        top = if (isBoxyScreen) 8.dp else 16.dp,
                        end = if (isBoxyScreen) 8.dp else 16.dp,
                        bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isBoxyScreen) 4.dp else 8.dp)
                ) {
                    item { BatteryOptimizationBanner(compact = isBoxyScreen) }
                    item { ExactAlarmPermissionBanner(compact = isBoxyScreen) }
                    if (reminders.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_reminders), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else {
                        items(reminders) { reminder ->
                            ReminderItem(
                                reminder = reminder,
                                onDelete = { viewModel.deleteReminder(it) },
                                onToggle = { viewModel.toggleReminder(it) },
                                onEdit = { reminderToEdit = it },
                                onTake = { viewModel.markAsTaken(it) },
                                compact = isBoxyScreen
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            ReminderDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, count, h, m, freq ->
                    viewModel.addReminder(name, count, h, m, freq)
                    showAddDialog = false
                }
            )
        }

        if (reminderToEdit != null) {
            ReminderDialog(
                reminder = reminderToEdit,
                onDismiss = { reminderToEdit = null },
                onConfirm = { name, count, h, m, freq ->
                    viewModel.updateReminder(reminderToEdit!!, name, count, h, m, freq)
                    reminderToEdit = null
                }
            )
        }
    }
}

@Composable
fun ExactAlarmPermissionBanner(compact: Boolean = false) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var hasPermission by remember { mutableStateOf(alarmManager.canScheduleExactAlarms()) }

        if (!hasPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(if (compact) 8.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.exact_alarm_permission_toast),
                            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = if (compact) PaddingValues(horizontal = 8.dp, vertical = 4.dp) else ButtonDefaults.ContentPadding
                    ) {
                        Text(stringResource(R.string.edit_button), style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationBanner(compact: Boolean = false) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isOptimized by remember {
        mutableStateOf(!powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    if (isOptimized) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 8.dp else 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(if (compact) 8.dp else 12.dp)) {
                Text(
                    text = stringResource(R.string.battery_optimization_title),
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (!compact) {
                    Text(
                        text = stringResource(R.string.battery_optimization_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = if (compact) PaddingValues(horizontal = 8.dp, vertical = 4.dp) else ButtonDefaults.ContentPadding
                ) {
                    Text(stringResource(R.string.disable_optimization_button), style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}


@Composable
fun ReminderItem(
    reminder: Reminder,
    onDelete: (Reminder) -> Unit,
    onToggle: (Reminder) -> Unit,
    onEdit: (Reminder) -> Unit,
    onTake: (Reminder) -> Unit,
    compact: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(if (compact) 10.dp else 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { context ->
                            LightToggle(context).apply {
                                setOnCheckedChangeListener { onToggle(reminder) }
                            }
                        },
                        update = { view ->
                            view.isChecked = reminder.isEnabled
                            val takenToday = TimeUtils.isTakenToday(reminder.lastTakenTimestamp)
                            view.setText(if (takenToday) "✓ ${reminder.medicationName}" else reminder.medicationName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.pill_info, reminder.pillCount, TimeUtils.formatTime(reminder.hour, reminder.minute)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row {
                        IconButton(onClick = { onTake(reminder) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.mark_as_taken_button),
                                tint = if (TimeUtils.isTakenToday(reminder.lastTakenTimestamp)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { onEdit(reminder) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_button), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onDelete(reminder) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_button), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pill_info, reminder.pillCount, TimeUtils.formatTime(reminder.hour, reminder.minute)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.frequency_value_label, when(reminder.frequency) {
                                "Daily" -> stringResource(R.string.frequency_daily)
                                "Weekly" -> stringResource(R.string.frequency_weekly)
                                "Monthly" -> stringResource(R.string.frequency_monthly)
                                else -> reminder.frequency
                            }),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    Row {
                        IconButton(onClick = { onTake(reminder) }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.mark_as_taken_button),
                                tint = if (TimeUtils.isTakenToday(reminder.lastTakenTimestamp)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = { onEdit(reminder) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_button))
                        }
                        IconButton(onClick = { onDelete(reminder) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_button))
                        }
                    }
                }
            }

            reminder.lastTakenTimestamp?.let { timestamp ->
                Text(
                    text = stringResource(R.string.last_taken_label, TimeUtils.formatFullDateTime(timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            reminder.lastSkippedTimestamp?.let { timestamp ->
                Text(
                    text = stringResource(R.string.last_skipped_label, TimeUtils.formatFullDateTime(timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    reminder: Reminder? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, String) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isBoxyScreen = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat() > 0.7f
    
    var name by remember { mutableStateOf(reminder?.medicationName ?: "") }
    var count by remember { mutableStateOf(reminder?.pillCount ?: "1") }
    var hour by remember { mutableStateOf(reminder?.hour ?: 8) }
    var minute by remember { mutableStateOf(reminder?.minute ?: 0) }
    var frequency by remember { mutableStateOf(reminder?.frequency ?: "Daily") }
    var showTimePicker by remember { mutableStateOf(false) }

    val frequencies = listOf("Daily", "Weekly", "Monthly")

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        hour = timePickerState.hour
                        minute = timePickerState.minute
                        showTimePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showTimePicker = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (isBoxyScreen) {
                        // TimeInput is more compact than TimePicker
                        TimeInput(state = timePickerState)
                    } else {
                        TimePicker(state = timePickerState)
                    }
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (reminder == null) R.string.add_reminder_title else R.string.edit_reminder_title), style = if (isBoxyScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (isBoxyScreen) 4.dp else 8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.medication_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = count,
                    onValueChange = { count = it },
                    label = { Text(stringResource(R.string.pill_count_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { showTimePicker = true }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = if (isBoxyScreen) PaddingValues(vertical = 4.dp) else ButtonDefaults.ContentPadding
                ) {
                    Text(stringResource(R.string.set_time_button, TimeUtils.formatTime(hour, minute)), style = if (isBoxyScreen) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
                }
                
                Text(stringResource(R.string.frequency_label), style = if (isBoxyScreen) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    frequencies.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = frequencies.size),
                            onClick = { frequency = label },
                            selected = frequency == label,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.onSurface,
                                activeContentColor = MaterialTheme.colorScheme.surface,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                text = when(label) {
                                    "Daily" -> stringResource(R.string.frequency_daily)
                                    "Weekly" -> stringResource(R.string.frequency_weekly)
                                    "Monthly" -> stringResource(R.string.frequency_monthly)
                                    else -> label
                                },
                                style = if (isBoxyScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && count.isNotBlank()) {
                        onConfirm(name, count, hour, minute, frequency)
                    } else {
                        Toast.makeText(context, context.getString(R.string.input_error_toast), Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(stringResource(if (reminder == null) R.string.schedule_button else R.string.update_button))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    compact: Boolean = false
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { AppUpdater(context) }
    
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        onRestore()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showRestoreConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
    
    if (updateRelease != null) {
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_available_message, updateRelease!!.tagName)) },
            confirmButton = {
                Button(
                    onClick = {
                        val release = updateRelease!!
                        updateRelease = null
                        isDownloading = true
                        scope.launch {
                            try {
                                updater.downloadAndInstall(release)
                            } catch (e: Exception) {
                                Toast.makeText(context, R.string.update_failed, Toast.LENGTH_LONG).show()
                            } finally {
                                isDownloading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                Button(
                    onClick = { updateRelease = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) 12.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (compact) Arrangement.Top else Arrangement.Center
    ) {
        if (isDownloading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            Text(stringResource(R.string.downloading_update), style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 40.dp else 64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 16.dp))
        
        Text(
            text = stringResource(R.string.about_title),
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
        
        Text(
            text = stringResource(R.string.about_description),
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 16.dp))
        
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(if (compact) 12.dp else 32.dp))

        Button(
            onClick = {
                if (!isCheckingUpdate) {
                    isCheckingUpdate = true
                    scope.launch {
                        val release = updater.checkForUpdate()
                        isCheckingUpdate = false
                        if (release != null) {
                            updateRelease = release
                        } else {
                            Toast.makeText(context, R.string.no_update_found, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isCheckingUpdate,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = if (compact) PaddingValues(vertical = 4.dp) else ButtonDefaults.ContentPadding
        ) {
            if (isCheckingUpdate) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSurface)
            } else {
                Text(stringResource(R.string.check_update_button))
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

        // Group Backup/Restore in a Row for boxy screens to save vertical space
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBackup,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.backup_button), style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { showRestoreConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.restore_button), style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Button(
                onClick = onBackup,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(stringResource(R.string.backup_button))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showRestoreConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(stringResource(R.string.restore_button))
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 8.dp else 16.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = if (compact) PaddingValues(vertical = 4.dp) else ButtonDefaults.ContentPadding
        ) {
            Text(stringResource(R.string.back_button))
        }
    }
}
