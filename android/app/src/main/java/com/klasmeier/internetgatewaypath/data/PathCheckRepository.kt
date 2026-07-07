package com.klasmeier.internetgatewaypath.data

import android.content.Context
import com.klasmeier.internetgatewaypath.data.api.GatewayClient
import com.klasmeier.internetgatewaypath.data.api.IpInfoClient
import com.klasmeier.internetgatewaypath.data.db.AppDatabase
import com.klasmeier.internetgatewaypath.data.db.TransitionEntity
import com.klasmeier.internetgatewaypath.data.detection.PathClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PathCheckRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository = SettingsRepository(context),
    private val ipInfoClient: IpInfoClient = IpInfoClient(),
    private val gatewayClient: GatewayClient = GatewayClient(),
    private val classifier: PathClassifier = PathClassifier(),
    db: AppDatabase? = null,
) {
    private val database = db ?: AppDatabase.get(context)
    private val transitionDao = database.transitionDao()

    suspend fun recentTransitions(limit: Int = 20): List<TransitionEntity> {
        return transitionDao.recent(limit)
    }

    suspend fun recalibrateReferenceIps(): Result<ReferenceIps> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.snapshot()
        if (!settings.configured) {
            return@withContext Result.failure(IllegalStateException("App not configured"))
        }
        val localNetwork = classifier.probeLocalNetwork(context, settings.homeSsid)
        if (!localNetwork.mightReachGateway) {
            return@withContext Result.failure(
                IllegalStateException("Connect to home WiFi or home access VPN first"),
            )
        }
        val egress = gatewayClient.fetchEgress(settings.gatewayUrl!!, settings.token!!, refresh = true)
            ?: return@withContext Result.failure(IllegalStateException("Gateway unreachable"))
        val reference = ReferenceIps(homeIp = egress.homeIp, obscuraIp = egress.obscuraIp)
        settingsRepository.saveReferenceIps(reference)
        Result.success(reference)
    }

    suspend fun runCheck(): PathCheckResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.snapshot()
        if (!settings.configured) {
            return@withContext classifier.failed("App not configured")
        }

        val gatewayUrl = settings.gatewayUrl!!
        val token = settings.token!!

        val localNetwork = classifier.probeLocalNetwork(context, settings.homeSsid)
        val reachable = if (localNetwork.mightReachGateway) {
            gatewayClient.isReachable(gatewayUrl, token)
        } else {
            false
        }

        var homeIp = settings.homeIp
        var obscuraIp = settings.obscuraIp
        if (reachable && (homeIp.isNullOrBlank() || obscuraIp.isNullOrBlank())) {
            gatewayClient.fetchEgress(gatewayUrl, token, refresh = true)?.let { egress ->
                homeIp = egress.homeIp ?: homeIp
                obscuraIp = egress.obscuraIp ?: obscuraIp
                settingsRepository.saveReferenceIps(ReferenceIps(homeIp, obscuraIp))
            }
        }

        val ipInfo = try {
            ipInfoClient.fetch(settings.ipinfoToken)
        } catch (exc: Exception) {
            return@withContext classifier.failed(exc.message ?: "ipinfo check failed")
        }

        val clientPath = if (reachable) {
            gatewayClient.fetchClientPath(gatewayUrl, token)
        } else {
            null
        }

        val network = localNetwork.copy(gatewayReachable = reachable)

        val result = classifier.classify(
            ipInfo = ipInfo,
            reference = ReferenceIps(homeIp, obscuraIp),
            network = network,
            clientPath = clientPath,
        )

        recordTransitionIfNeeded(result)
        pruneHistory()
        result
    }

    private suspend fun recordTransitionIfNeeded(result: PathCheckResult) {
        val path = result.path
        if (path == InternetPath.CHECK_FAILED || path == InternetPath.UNKNOWN) {
            return
        }
        val previousName = settingsRepository.getLastPath()
        val previous = previousName?.let { runCatching { InternetPath.valueOf(it) }.getOrNull() }
        settingsRepository.setLastPath(path.name)
        if (previous != null && previous != path) {
            transitionDao.insert(
                TransitionEntity(
                    fromPath = previous.name,
                    toPath = path.name,
                    publicIp = result.publicIp,
                    occurredAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun pruneHistory() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
        transitionDao.deleteOlderThan(cutoff)
        if (transitionDao.count() > 500) {
            transitionDao.trimTo(500)
        }
    }
}
