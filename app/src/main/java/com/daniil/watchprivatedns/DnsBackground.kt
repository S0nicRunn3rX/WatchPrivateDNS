package com.daniil.watchprivatedns

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class WatchPrivateDnsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DnsPreferences.ensureInitialized(this)
        DnsJobScheduler.schedulePeriodic(this)
        DnsConnectivityMonitor.start(this)
    }
}

internal object DnsConnectivityMonitor {
    @Volatile
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                DnsPolicy.evaluateConnectivityPolicy(appContext)
            }

            override fun onLost(network: Network) {
                DnsPolicy.evaluateConnectivityPolicy(appContext)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                DnsPolicy.evaluateConnectivityPolicy(appContext)
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
            started = true
            DnsPolicy.evaluateConnectivityPolicy(appContext)
        } catch (_: Exception) {
            // Periodic JobScheduler checks remain as a fallback.
        }
    }
}

internal object DnsJobScheduler {
    private const val PERIODIC_JOB_ID = 42001
    private const val VALIDATION_JOB_ID = 42002
    private const val PERIOD_MS = 15 * 60 * 1000L

    fun schedulePeriodic(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val info = JobInfo.Builder(
            PERIODIC_JOB_ID,
            ComponentName(context, DnsPolicyJobService::class.java)
        )
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(info)
    }

    fun scheduleValidation(context: Context, delayMs: Long) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val info = JobInfo.Builder(
            VALIDATION_JOB_ID,
            ComponentName(context, DnsValidationJobService::class.java)
        )
            .setMinimumLatency(delayMs.coerceAtLeast(1_000L))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build()
        scheduler.schedule(info)
    }
}

class DnsPolicyJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        DnsPolicy.evaluateConnectivityPolicy(this)
        DnsPolicy.validatePendingManualDns(this)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}

class DnsValidationJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        DnsPolicy.evaluateConnectivityPolicy(this)
        DnsPolicy.validatePendingManualDns(this)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            DnsJobScheduler.schedulePeriodic(context)
            DnsPolicy.evaluateConnectivityPolicy(context)
        }
    }
}
