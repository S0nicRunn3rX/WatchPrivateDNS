package com.daniil.watchprivatedns

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import java.net.IDN
import java.util.Locale

internal enum class DnsMode(val systemValue: String) {
    OFF("off"),
    AUTO("opportunistic"),
    MANUAL("hostname")
}

internal data class DnsState(
    val mode: DnsMode = DnsMode.AUTO,
    val hostname: String = "",
    val readable: Boolean = true
)

internal enum class ApplyResult { OK, BAD_HOSTNAME, DENIED }

internal const val KEY_PRIVATE_DNS_MODE = "private_dns_mode"
internal const val KEY_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

internal fun readDnsState(context: Context): DnsState = try {
    val rawMode = Settings.Global.getString(context.contentResolver, KEY_PRIVATE_DNS_MODE)
    val hostname = Settings.Global.getString(context.contentResolver, KEY_PRIVATE_DNS_SPECIFIER).orEmpty()
    val mode = when (rawMode) {
        DnsMode.OFF.systemValue -> DnsMode.OFF
        DnsMode.MANUAL.systemValue -> DnsMode.MANUAL
        else -> DnsMode.AUTO
    }
    DnsState(mode = mode, hostname = hostname, readable = true)
} catch (_: Exception) {
    DnsState(readable = false)
}

internal fun writeDnsState(context: Context, mode: DnsMode, rawHostname: String = ""): ApplyResult {
    if (!hasWriteSecureSettings(context)) return ApplyResult.DENIED

    return try {
        val resolver = context.contentResolver
        val ok = when (mode) {
            DnsMode.OFF -> Settings.Global.putString(
                resolver,
                KEY_PRIVATE_DNS_MODE,
                DnsMode.OFF.systemValue
            )

            DnsMode.AUTO -> Settings.Global.putString(
                resolver,
                KEY_PRIVATE_DNS_MODE,
                DnsMode.AUTO.systemValue
            )

            DnsMode.MANUAL -> {
                val hostname = normalizeHostname(rawHostname) ?: return ApplyResult.BAD_HOSTNAME
                val hostOk = Settings.Global.putString(resolver, KEY_PRIVATE_DNS_SPECIFIER, hostname)
                val modeOk = Settings.Global.putString(
                    resolver,
                    KEY_PRIVATE_DNS_MODE,
                    DnsMode.MANUAL.systemValue
                )
                hostOk && modeOk
            }
        }
        if (ok) ApplyResult.OK else ApplyResult.DENIED
    } catch (_: SecurityException) {
        ApplyResult.DENIED
    } catch (_: Exception) {
        ApplyResult.DENIED
    }
}

internal fun normalizeHostname(raw: String): String? {
    var host = raw.trim().lowercase(Locale.ROOT)
    if (host.endsWith('.')) host = host.dropLast(1)

    if (
        host.isBlank() || host.contains("://") || host.contains('/') ||
        host.contains(':') || host.any { it.isWhitespace() }
    ) return null

    return try {
        val ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
        if (ascii.length > 253 || !ascii.contains('.')) return null
        if (ascii.split('.').any {
                it.isBlank() || it.length > 63 || it.startsWith('-') || it.endsWith('-')
            }
        ) return null
        ascii
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun hasWriteSecureSettings(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
