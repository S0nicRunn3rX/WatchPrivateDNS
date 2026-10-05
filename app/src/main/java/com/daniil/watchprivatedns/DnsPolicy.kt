package com.daniil.watchprivatedns

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal enum class UserApplyResult {
    APPLIED,
    DEFERRED_BY_BLUETOOTH,
    BAD_HOSTNAME,
    DENIED
}

internal object DnsPolicy {
    private const val VALIDATION_GRACE_MS = 12_000L

    @Synchronized
    fun applyUserChoice(
        context: Context,
        mode: DnsMode,
        rawHostname: String = ""
    ): UserApplyResult {
        DnsPreferences.ensureInitialized(context)
        if (!hasWriteSecureSettings(context)) return UserApplyResult.DENIED

        val hostname = if (mode == DnsMode.MANUAL) {
            normalizeHostname(rawHostname) ?: return UserApplyResult.BAD_HOSTNAME
        } else ""

        if (mode == DnsMode.MANUAL) DnsPreferences.addHistory(context, hostname)
        DnsPreferences.setDesired(context, mode, hostname)
        DnsPreferences.clearFallback(context)
        DnsPreferences.clearValidation(context)

        if (isBluetoothProxyAvailable(context)) {
            val result = writeDnsState(context, DnsMode.OFF)
            if (result != ApplyResult.OK) return UserApplyResult.DENIED
            DnsPreferences.setSuspendedByBluetooth(context, mode != DnsMode.OFF)
            if (mode != DnsMode.OFF) {
                DnsPreferences.setFallback(context, FallbackReason.BLUETOOTH_PROXY)
                return UserApplyResult.DEFERRED_BY_BLUETOOTH
            }
            return UserApplyResult.APPLIED
        }

        DnsPreferences.setSuspendedByBluetooth(context, false)
        val result = writeDnsState(context, mode, hostname)
        return when (result) {
            ApplyResult.OK -> {
                if (mode == DnsMode.MANUAL) beginManualValidation(context, hostname)
                UserApplyResult.APPLIED
            }
            ApplyResult.BAD_HOSTNAME -> UserApplyResult.BAD_HOSTNAME
            ApplyResult.DENIED -> UserApplyResult.DENIED
        }
    }

    @Synchronized
    fun evaluateConnectivityPolicy(context: Context) {
        DnsPreferences.ensureInitialized(context)
        if (!hasWriteSecureSettings(context)) return

        val desired = DnsPreferences.desired(context)
        val btProxy = isBluetoothProxyAvailable(context)

        if (btProxy) {
            if (readDnsState(context).mode != DnsMode.OFF) {
                writeDnsState(context, DnsMode.OFF)
            }
            DnsPreferences.clearValidation(context)
            if (desired.mode != DnsMode.OFF) {
                DnsPreferences.setSuspendedByBluetooth(context, true)
                DnsPreferences.setFallback(context, FallbackReason.BLUETOOTH_PROXY)
            } else {
                DnsPreferences.setSuspendedByBluetooth(context, false)
            }
            return
        }

        if (!DnsPreferences.isSuspendedByBluetooth(context)) return

        DnsPreferences.setSuspendedByBluetooth(context, false)
        DnsPreferences.clearFallback(context)

        when (desired.mode) {
            DnsMode.AUTO -> writeDnsState(context, DnsMode.AUTO)
            DnsMode.OFF -> writeDnsState(context, DnsMode.OFF)
            DnsMode.MANUAL -> {
                val host = normalizeHostname(desired.hostname)
                if (host == null) {
                    fallbackToAutomatic(context, desired.hostname)
                    return
                }
                if (writeDnsState(context, DnsMode.MANUAL, host) == ApplyResult.OK) {
                    beginManualValidation(context, host)
                } else {
                    fallbackToAutomatic(context, host)
                }
            }
        }
    }

    @Synchronized
    fun validatePendingManualDns(context: Context) {
        if (!hasWriteSecureSettings(context)) return
        val expected = normalizeHostname(DnsPreferences.validationHost(context)) ?: return

        if (isBluetoothProxyAvailable(context)) {
            evaluateConnectivityPolicy(context)
            return
        }

        val desired = DnsPreferences.desired(context)
        if (desired.mode != DnsMode.MANUAL || !desired.hostname.equals(expected, ignoreCase = true)) {
            DnsPreferences.clearValidation(context)
            return
        }

        val current = readDnsState(context)
        if (current.mode != DnsMode.MANUAL || !current.hostname.equals(expected, ignoreCase = true)) {
            DnsPreferences.clearValidation(context)
            return
        }

        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val network = cm.activeNetwork ?: return
        val linkProperties = cm.getLinkProperties(network) ?: return

        val strictDnsIsActive = linkProperties.isPrivateDnsActive &&
            linkProperties.privateDnsServerName?.equals(expected, ignoreCase = true) == true

        if (strictDnsIsActive) {
            DnsPreferences.clearValidation(context)
            DnsPreferences.clearFallback(context)
            return
        }

        val elapsed = System.currentTimeMillis() - DnsPreferences.validationStarted(context)
        if (elapsed < VALIDATION_GRACE_MS) {
            DnsJobScheduler.scheduleValidation(context, VALIDATION_GRACE_MS - elapsed)
            return
        }

        fallbackToAutomatic(context, expected)
    }

    fun isBluetoothProxyAvailable(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return try {
            cm.allNetworks.any { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@any false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        } catch (_: SecurityException) {
            false
        }
    }

    private fun beginManualValidation(context: Context, hostname: String) {
        DnsPreferences.beginValidation(context, hostname)
        DnsJobScheduler.scheduleValidation(context, VALIDATION_GRACE_MS)
    }

    private fun fallbackToAutomatic(context: Context, failedHostname: String) {
        writeDnsState(context, DnsMode.AUTO)
        DnsPreferences.setDesired(context, DnsMode.AUTO)
        DnsPreferences.setSuspendedByBluetooth(context, false)
        DnsPreferences.clearValidation(context)
        DnsPreferences.setFallback(
            context,
            FallbackReason.DNS_VALIDATION_FAILED,
            failedHostname
        )
    }
}
