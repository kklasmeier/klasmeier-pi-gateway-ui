package com.klasmeier.internetgatewaypath.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.klasmeier.internetgatewaypath.R
import com.klasmeier.internetgatewaypath.util.QuietHours

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onClearedSetup: () -> Unit,
) {
    val prefs by viewModel.notificationPrefs.collectAsState()
    val state by viewModel.uiState.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearSetup(onClearedSetup)
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_notifications_section),
                style = MaterialTheme.typography.titleMedium,
            )

            ToggleRow(
                label = stringResource(R.string.settings_notify_changes),
                checked = prefs.notificationsEnabled,
                onCheckedChange = viewModel::setNotificationsEnabled,
            )

            ToggleRow(
                label = stringResource(R.string.settings_quiet_hours),
                checked = prefs.quietHoursEnabled,
                onCheckedChange = viewModel::setQuietHoursEnabled,
            )

            if (prefs.quietHoursEnabled) {
                QuietTimePicker(
                    label = stringResource(R.string.settings_quiet_start),
                    minutes = prefs.quietStartMinutes,
                    onMinutesChange = viewModel::setQuietStart,
                )
                QuietTimePicker(
                    label = stringResource(R.string.settings_quiet_end),
                    minutes = prefs.quietEndMinutes,
                    onMinutesChange = viewModel::setQuietEnd,
                )
                Text(
                    text = stringResource(R.string.settings_quiet_hours_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_calibration_section),
                style = MaterialTheme.typography.titleMedium,
            )

            state.referenceIps?.let { reference ->
                reference.homeIp?.let {
                    Text(stringResource(R.string.settings_home_ip, it), style = MaterialTheme.typography.bodyMedium)
                }
                reference.obscuraIp?.let {
                    Text(stringResource(R.string.settings_obscura_ip, it), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                onClick = viewModel::recalibrate,
                enabled = !state.recalibrating && !state.clearing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_recalibrate))
            }

            Text(
                text = stringResource(R.string.settings_recalibrate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_setup_section),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedButton(
                onClick = { showClearConfirm = true },
                enabled = !state.recalibrating && !state.clearing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_clear_setup))
            }

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietTimePicker(
    label: String,
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
) {
    var hourExpanded by remember { mutableStateOf(false) }
    var minuteExpanded by remember { mutableStateOf(false) }
    val hour24 = (minutes / 60) % 24
    val minute = minutes % 60
    val nearestMinute = QuietHours.minuteStepOptions().minByOrNull { kotlin.math.abs(it - minute) } ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded = hourExpanded,
                onExpandedChange = { hourExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = hour24.toString().padStart(2, '0'),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_hour)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hourExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = hourExpanded,
                    onDismissRequest = { hourExpanded = false },
                ) {
                    QuietHours.hourOptions().forEach { hour ->
                        DropdownMenuItem(
                            text = { Text(hour.toString().padStart(2, '0')) },
                            onClick = {
                                onMinutesChange(hour * 60 + nearestMinute)
                                hourExpanded = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = minuteExpanded,
                onExpandedChange = { minuteExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = nearestMinute.toString().padStart(2, '0'),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_minute)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minuteExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = minuteExpanded,
                    onDismissRequest = { minuteExpanded = false },
                ) {
                    QuietHours.minuteStepOptions().forEach { step ->
                        DropdownMenuItem(
                            text = { Text(step.toString().padStart(2, '0')) },
                            onClick = {
                                onMinutesChange(hour24 * 60 + step)
                                minuteExpanded = false
                            },
                        )
                    }
                }
            }
        }
        Text(
            text = QuietHours.formatMinutes(minutes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
