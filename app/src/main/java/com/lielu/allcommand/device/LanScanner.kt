package com.lielu.allcommand.device

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** Cherche sur le réseau local les appareils qui écoutent sur le port d'un type donné. */
interface DeviceScanner {
    /** Adresses IP trouvées, triées ; vide si le téléphone n'est pas sur un réseau local (pas de Wi-Fi). */
    suspend fun scan(port: Int): List<String>
}

/**
 * Teste le port sur les 254 adresses du réseau /24 du téléphone — le cas de toute box domestique. Aucune
 * permission requise : une simple connexion TCP, abandonnée aussitôt.
 */
class LanScanner(
    private val localAddress: () -> String? = ::findLocalIpv4,
    private val timeoutMillis: Int = CONNECT_TIMEOUT_MILLIS,
) : DeviceScanner {
    override suspend fun scan(port: Int): List<String> {
        val local = localAddress() ?: return emptyList()
        val gate = Semaphore(PARALLEL_PROBES)
        return coroutineScope {
            subnetHosts(local)
                .map { host -> async(Dispatchers.IO) { gate.withPermit { host.takeIf { isOpen(it, port) } } } }
                .awaitAll()
                .filterNotNull()
        }
    }

    private suspend fun isOpen(
        host: String,
        port: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMillis) }
                true
            } catch (_: java.io.IOException) {
                false
            }
        }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 400
        private const val PARALLEL_PROBES = 64

        /** Les 253 autres adresses du /24 de [local] (« 192.168.1.42 » → 192.168.1.1…254 sauf .42). */
        fun subnetHosts(local: String): List<String> {
            val parts = local.split('.')
            if (parts.size != 4) return emptyList()
            val prefix = parts.take(3).joinToString(".")
            return (1..254).map { "$prefix.$it" }.filter { it != local }
        }

        /** Première adresse IPv4 privée du téléphone (Wi-Fi ou partage de connexion), sinon null. */
        fun findLocalIpv4(): String? =
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
    }
}
