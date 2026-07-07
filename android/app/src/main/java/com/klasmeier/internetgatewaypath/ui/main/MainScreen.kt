package com.klasmeier.internetgatewaypath.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.klasmeier.internetgatewaypath.BuildConfig
import com.klasmeier.internetgatewaypath.R
import com.klasmeier.internetgatewaypath.data.InternetPath
import com.klasmeier.internetgatewaypath.data.PathCheckResult
import com.klasmeier.internetgatewaypath.data.db.TransitionEntity
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.app_version_label,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loading && state.current == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            val display = state.current ?: state.previous
            if (display != null) {
                PathCard(result = display, dimmed = state.current?.path == InternetPath.CHECK_FAILED)
            }

            Button(
                onClick = viewModel::refresh,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text(stringResource(R.string.refresh), modifier = Modifier.padding(start = 8.dp))
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (state.transitions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.recent_changes),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.transitions.forEach { transition ->
                    TransitionRow(transition)
                }
            }
        }
    }
}

@Composable
private fun PathCard(result: PathCheckResult, dimmed: Boolean) {
    val alpha = if (dimmed) 0.55f else 1f
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = pathIcon(result.path),
            contentDescription = null,
            modifier = Modifier.padding(top = 8.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        )
        Text(
            text = pathLabel(result.path),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
        )
        result.publicIp?.let {
            Text("Exit IP  $it", color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha))
        }
        result.location?.let {
            Text("Location  $it", color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha))
        }
        Text(
            text = "${stringResource(R.string.last_checked)}  ${formatTime(result.checkedAtEpochMs)}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (result.connectionDetail.isNotEmpty()) {
            Text(
                text = stringResource(R.string.connection_detail),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start),
            )
            result.connectionDetail.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodyMedium)
            }
        }
        result.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun TransitionRow(transition: TransitionEntity) {
    Text(
        text = "${formatTime(transition.occurredAtEpochMs)}  " +
            "${pathLabel(transition.fromPath)} → ${pathLabel(transition.toPath)}",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun pathLabel(path: InternetPath): String = when (path) {
    InternetPath.OBSCURA -> stringResource(R.string.path_obscura)
    InternetPath.HOME -> stringResource(R.string.path_home)
    InternetPath.PHONE -> stringResource(R.string.path_phone)
    InternetPath.UNKNOWN, InternetPath.CHECK_FAILED -> stringResource(R.string.path_unknown)
}

private fun pathLabel(name: String): String = when (name) {
    InternetPath.OBSCURA.name -> "Obscura Internet"
    InternetPath.HOME.name -> "Home Internet"
    InternetPath.PHONE.name -> "Phone Internet"
    else -> "Unknown"
}

private fun pathIcon(path: InternetPath): ImageVector = when (path) {
    InternetPath.OBSCURA -> Icons.Default.Cloud
    InternetPath.HOME -> Icons.Default.Home
    InternetPath.PHONE -> Icons.Default.PhoneAndroid
    InternetPath.UNKNOWN, InternetPath.CHECK_FAILED -> Icons.Default.Warning
}

private fun formatTime(epochMs: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(epochMs))
}
