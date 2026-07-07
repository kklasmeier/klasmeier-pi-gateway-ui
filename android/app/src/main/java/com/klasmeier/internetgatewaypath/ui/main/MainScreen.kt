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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.klasmeier.internetgatewaypath.R
import com.klasmeier.internetgatewaypath.data.InternetPath
import com.klasmeier.internetgatewaypath.data.PathCheckResult
import com.klasmeier.internetgatewaypath.data.db.TransitionEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

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
