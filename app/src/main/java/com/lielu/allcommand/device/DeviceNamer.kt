package com.lielu.allcommand.device

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume

/** Retrouve le nom que l'utilisateur reconnaît (« Salon », « Chromecast de la chambre »…) pour des adresses IP. */
interface DeviceNamer {
    /** Nom par adresse IP, pour celles dont un nom a été trouvé ; les autres sont absentes. */
    suspend fun names(hosts: Collection<String>): Map<String, String>
}

/**
 * Noms annoncés par les appareils eux-mêmes (mDNS : Android TV Remote et Google Cast), à défaut le nom réseau
 * que la box attribue (DNS inverse). Silencieux en cas d'échec : un nom manquant ne bloque jamais l'ajout.
 */
class MdnsDeviceNamer(
    context: Context,
) : DeviceNamer {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    override suspend fun names(hosts: Collection<String>): Map<String, String> {
        if (hosts.isEmpty()) return emptyMap()
        val wanted = hosts.toSet()
        val result = mutableMapOf<String, String>()
        runCatching { announced(wanted) }.getOrNull()?.let(result::putAll)
        val missing = wanted - result.keys
        if (missing.isNotEmpty()) result.putAll(reverseDns(missing))
        return result
    }

    private suspend fun announced(wanted: Set<String>): Map<String, String> {
        val found = CopyOnWriteArrayList<NsdServiceInfo>()
        val listeners =
            SERVICE_TYPES.map { type ->
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        found += serviceInfo
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                    override fun onDiscoveryStopped(serviceType: String) = Unit

                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) = Unit

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) = Unit
                } to type
            }
        listeners.forEach { (listener, type) ->
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        }
        delay(DISCOVERY_MILLIS)
        listeners.forEach { (listener, _) -> runCatching { nsd.stopServiceDiscovery(listener) } }

        val names = mutableMapOf<String, String>()
        // Une seule résolution à la fois : Android refuse les résolutions simultanées.
        for (service in found.toList()) {
            val resolved = withTimeoutOrNull(RESOLVE_MILLIS) { resolve(service) } ?: continue
            val address = resolved.host?.hostAddress ?: continue
            if (address !in wanted) continue
            val friendly =
                resolved.attributes["fn"]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
                    ?: resolved.serviceName
            names.putIfAbsent(address, friendly)
        }
        return names
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            val listener =
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(
                        serviceInfo: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (continuation.isActive) continuation.resume(serviceInfo)
                    }
                }
            try {
                nsd.resolveService(service, listener)
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private suspend fun reverseDns(hosts: Set<String>): Map<String, String> =
        coroutineScope {
            hosts
                .map { host ->
                    async(Dispatchers.IO) {
                        val name =
                            withTimeoutOrNull(REVERSE_DNS_MILLIS) {
                                withContext(Dispatchers.IO) { InetAddress.getByName(host).canonicalHostName }
                            }
                        // Sans nom, Android renvoie l'adresse elle-même.
                        name?.takeIf { it != host }?.substringBefore('.')?.let { host to it }
                    }
                }.awaitAll()
                .filterNotNull()
                .toMap()
        }

    private companion object {
        val SERVICE_TYPES = listOf("_androidtvremote2._tcp.", "_googlecast._tcp.")
        const val DISCOVERY_MILLIS = 2_500L
        const val RESOLVE_MILLIS = 1_500L
        const val REVERSE_DNS_MILLIS = 1_500L
    }
}
