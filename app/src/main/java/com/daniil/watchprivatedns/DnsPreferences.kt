package com.daniil.watchprivatedns

import android.content.Context

internal enum class FallbackReason {
    NONE,
    BLUETOOTH_PROXY,
    DNS_VALIDATION_FAILED
}

internal data class DesiredDnsState(
    val mode: DnsMode,
    val hostname: String
)

internal object DnsPreferences {
    private const val PREFS = "private_dns_preferences"
    private const val KEY_INITIALIZED = "initialized"
    private const val KEY_DESIRED_MODE = "desired_mode"
    private const val KEY_DESIRED_HOST = "desired_host"
    private const val KEY_HISTORY = "manual_history"
    private const val KEY_SUSPENDED_BT = "suspended_by_bluetooth"
    private const val KEY_FALLBACK_REASON = "fallback_reason"
    private const val KEY_FAILURE_HOST = "failure_host"
    private const val KEY_VALIDATION_HOST = "validation_host"
    private const val KEY_VALIDATION_STARTED = "validation_started"
    private const val HISTORY_LIMIT = 8

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureInitialized(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_INITIALIZED, false)) return
        val current = readDnsState(context)
        p.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putString(KEY_DESIRED_MODE, current.mode.name)
            .putString(KEY_DESIRED_HOST, current.hostname)
            .apply()
    }

    fun desired(context: Context): DesiredDnsState {
        ensureInitialized(context)
        val p = prefs(context)
        val mode = runCatching {
            DnsMode.valueOf(p.getString(KEY_DESIRED_MODE, DnsMode.AUTO.name) ?: DnsMode.AUTO.name)
        }.getOrDefault(DnsMode.AUTO)
        return DesiredDnsState(mode, p.getString(KEY_DESIRED_HOST, "").orEmpty())
    }

    fun setDesired(context: Context, mode: DnsMode, hostname: String = "") {
        val editor = prefs(context).edit().putString(KEY_DESIRED_MODE, mode.name)
        if (mode == DnsMode.MANUAL) editor.putString(KEY_DESIRED_HOST, hostname)
        editor.apply()
    }

    fun history(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_HISTORY, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct().take(HISTORY_LIMIT).toList()
    }

    fun addHistory(context: Context, hostname: String) {
        val normalized = normalizeHostname(hostname) ?: return
        val values = buildList {
            add(normalized)
            addAll(history(context).filterNot { it.equals(normalized, ignoreCase = true) })
        }.take(HISTORY_LIMIT)
        prefs(context).edit().putString(KEY_HISTORY, values.joinToString("\n")).apply()
    }

    fun removeHistory(context: Context, hostname: String) {
        val values = history(context).filterNot { it.equals(hostname, ignoreCase = true) }
        prefs(context).edit().putString(KEY_HISTORY, values.joinToString("\n")).apply()
    }

    fun isSuspendedByBluetooth(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUSPENDED_BT, false)

    fun setSuspendedByBluetooth(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUSPENDED_BT, value).apply()
    }

    fun setFallback(context: Context, reason: FallbackReason, hostname: String = "") {
        prefs(context).edit()
            .putString(KEY_FALLBACK_REASON, reason.name)
            .putString(KEY_FAILURE_HOST, hostname)
            .apply()
    }

    fun fallbackReason(context: Context): FallbackReason = runCatching {
        FallbackReason.valueOf(
            prefs(context).getString(KEY_FALLBACK_REASON, FallbackReason.NONE.name)
                ?: FallbackReason.NONE.name
        )
    }.getOrDefault(FallbackReason.NONE)

    fun failureHost(context: Context): String =
        prefs(context).getString(KEY_FAILURE_HOST, "").orEmpty()

    fun clearFallback(context: Context) {
        prefs(context).edit()
            .putString(KEY_FALLBACK_REASON, FallbackReason.NONE.name)
            .putString(KEY_FAILURE_HOST, "")
            .apply()
    }

    fun beginValidation(context: Context, hostname: String) {
        prefs(context).edit()
            .putString(KEY_VALIDATION_HOST, hostname)
            .putLong(KEY_VALIDATION_STARTED, System.currentTimeMillis())
            .apply()
    }

    fun validationHost(context: Context): String =
        prefs(context).getString(KEY_VALIDATION_HOST, "").orEmpty()

    fun validationStarted(context: Context): Long =
        prefs(context).getLong(KEY_VALIDATION_STARTED, 0L)

    fun clearValidation(context: Context) {
        prefs(context).edit()
            .remove(KEY_VALIDATION_HOST)
            .remove(KEY_VALIDATION_STARTED)
            .apply()
    }
}
