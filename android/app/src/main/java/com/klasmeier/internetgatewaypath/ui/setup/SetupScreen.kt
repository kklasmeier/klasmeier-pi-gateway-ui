package com.klasmeier.internetgatewaypath.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.klasmeier.internetgatewaypath.R
import com.klasmeier.internetgatewaypath.data.ReferenceIps
import com.klasmeier.internetgatewaypath.data.SettingsRepository
import com.klasmeier.internetgatewaypath.data.api.GatewayClient
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }
    val gatewayClient = remember { GatewayClient() }

    var gatewayUrl by remember { mutableStateOf("http://192.168.1.100:8080") }
    var token by remember { mutableStateOf("") }
    var homeSsid by remember { mutableStateOf("") }
    var gatewayIp by remember { mutableStateOf("192.168.1.100") }
    var ipinfoToken by remember { mutableStateOf("") }
    var setupJson by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun saveFromPayload(raw: String) {
        scope.launch {
            saving = true
            error = null
            try {
                val payload = SettingsRepository.parseSetupJson(raw)
                settingsRepository.saveSetup(payload)
                gatewayClient.fetchEgress(payload.gatewayUrl, payload.token, refresh = true)?.let { egress ->
                    settingsRepository.saveReferenceIps(
                        ReferenceIps(
                            homeIp = egress.homeIp,
                            obscuraIp = egress.obscuraIp,
                        ),
                    )
                }
                onComplete()
            } catch (exc: Exception) {
                error = exc.message ?: "Setup failed"
            } finally {
                saving = false
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { saveFromPayload(it) }
            ?: run { error = "QR scan cancelled" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.setup_title))
        Text("On the gateway admin site go to Mobile app, then scan the setup QR here (home WiFi).")

        Button(
            onClick = {
                scanLauncher.launch(
                    ScanOptions().apply {
                        setPrompt("Scan setup QR from gateway admin website")
                        setBeepEnabled(false)
                    },
                )
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_scan_qr))
        }

        OutlinedButton(
            onClick = { showManual = !showManual },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (showManual) "Hide manual setup" else stringResource(R.string.setup_manual))
        }

        if (showManual) {
            HorizontalDivider()
            Text("Paste setup JSON or enter fields manually.")

            OutlinedTextField(
                value = setupJson,
                onValueChange = { setupJson = it },
                label = { Text("Setup JSON") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Button(
                onClick = { saveFromPayload(setupJson) },
                enabled = setupJson.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import JSON")
            }

            OutlinedTextField(
                value = gatewayUrl,
                onValueChange = { gatewayUrl = it },
                label = { Text("Gateway URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("API token") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = homeSsid,
                onValueChange = { homeSsid = it },
                label = { Text("Home WiFi SSID") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = gatewayIp,
                onValueChange = { gatewayIp = it },
                label = { Text("Gateway IP") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ipinfoToken,
                onValueChange = { ipinfoToken = it },
                label = { Text("ipinfo token (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val payload = """{"v":1,"gateway_url":"$gatewayUrl","token":"$token","home_ssid":"$homeSsid","gateway_ip":"$gatewayIp","ipinfo_token":"$ipinfoToken"}"""
                    saveFromPayload(payload)
                },
                enabled = token.isNotBlank() && homeSsid.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save manual setup")
            }
        }

        error?.let { Text(it) }
    }
}
