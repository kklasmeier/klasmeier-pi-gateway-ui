package com.klasmeier.internetgatewaypath.data

enum class InternetPath {
    OBSCURA,
    HOME,
    PHONE,
    UNKNOWN,
    CHECK_FAILED,
}

data class PathCheckResult(
    val path: InternetPath,
    val publicIp: String?,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val connectionDetail: List<String>,
    val expectedPath: String?,
    val policyMismatch: Boolean,
    val checkedAtEpochMs: Long,
    val errorMessage: String? = null,
)

data class SetupPayload(
    val version: Int,
    val gatewayUrl: String,
    val token: String,
    val homeSsid: String,
    val gatewayIp: String,
    val ipinfoToken: String?,
    val deviceLabel: String?,
)

data class ReferenceIps(
    val homeIp: String?,
    val obscuraIp: String?,
)
