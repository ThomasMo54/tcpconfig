package com.motompro.tcpconfig.app.dhcp

import com.motompro.tcpconfig.app.TCPConfigApp
import com.motompro.tcpconfig.app.dhcp.history.AddressAssignHistory
import com.motompro.tcpconfig.app.dhcp.history.DHCPHistoryItem
import com.motompro.tcpconfig.app.dhcp.history.ServerStartHistory
import com.motompro.tcpconfig.app.util.IPRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.dhcp4java.DHCPConstants
import org.dhcp4java.DHCPPacket
import org.dhcp4java.DHCPResponseFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.experimental.and

private const val DHCP_PACKET_SIZE = 1500
private const val DHCP_LEASE_TIME = 3600
private val BROADCAST_ADDRESS = InetAddress.getByName("255.255.255.255")

class DHCPServer {

    private val hostName = InetAddress.getLocalHost().hostName
    private val ipTable = ConcurrentHashMap<String, String>()
    private lateinit var ipIterator: Iterator<String>
    private var socket: DatagramSocket? = null
    private var connectionThread: Job? = null

    var isStarted = false
        private set
    var networkAdapter: String? = null
    var ipRange: IPRange? = null
    var listener: DHCPServerListener? = null
    val history: List<DHCPHistoryItem>
        get() = _history
    private val _history = mutableListOf<DHCPHistoryItem>()
    val reservations: Map<String, String>
        get() = _reservations
    private val _reservations = LinkedHashMap<String, String>()

    fun addReservation(macAddress: String, ipAddress: String) {
        val formattedIp = ipAddress.split(".").joinToString(".") { it.toInt().toString() }
        _reservations[normalizeMacAddress(macAddress)] = formattedIp
    }

    fun removeReservation(macAddress: String) {
        _reservations.remove(normalizeMacAddress(macAddress))
    }

    fun start(networkAdapter: String, ipRange: IPRange) {
        this.networkAdapter = networkAdapter
        this.ipRange = ipRange
        ipIterator = ipRange.ipList.filterNot { it in _reservations.values }.iterator()
        ipTable.clear()
        _history.clear()

        // Start socket
        val stringIp = TCPConfigApp.INSTANCE.netInterfaceManager.getConfig(networkAdapter).ip
        val serverAddress = InetAddress.getByName(stringIp)
        socket = DatagramSocket(DHCPConstants.BOOTP_REQUEST_PORT, serverAddress)

        isStarted = true
        val startHistoryItem = ServerStartHistory(ZonedDateTime.now(), stringIp)
        _history.add(startHistoryItem)
        listener?.onServerStart(startHistoryItem)
        connectionThread = CoroutineScope(Dispatchers.Default).launch {
            while (socket?.isClosed != true && isStarted) {
                // Wait packet reception
                val packet = DatagramPacket(ByteArray(DHCP_PACKET_SIZE), DHCP_PACKET_SIZE)
                try {
                    socket?.receive(packet)
                } catch (ex: SocketException) {
                    break
                }

                // Convert to DHCPPacket
                val dhcpPacket = DHCPPacket.getPacket(packet)

                // Get useful packet data
                val messageType = dhcpPacket.dhcpMessageType
                val clientHardwareAddress = dhcpPacket.hardwareAddress.hardwareAddressHex
                val clientHostName = dhcpPacket.optionsCollection
                    .filter { it.code.and(0xFF.toByte()) == 12.toByte() }
                    .map { DHCPPacket.bytesToString(it.value) }
                    .firstOrNull()

                // Cancel treatment if the client is the server
                if (clientHostName == hostName) continue
                val reservedIp = _reservations[normalizeMacAddress(clientHardwareAddress)]
                // Cancel treatment if no ip address remaining
                if (!ipTable.containsKey(clientHardwareAddress) && reservedIp == null && !ipIterator.hasNext()) continue

                // DHCPDISCOVER
                if (messageType == DHCPConstants.DHCPDISCOVER) {
                    val responsePacket = DatagramPacket(ByteArray(DHCP_PACKET_SIZE), DHCP_PACKET_SIZE)
                    val ipAddress = if (ipTable.containsKey(clientHardwareAddress)) {
                        ipTable[clientHardwareAddress]!!
                    } else {
                        val ip = reservedIp ?: ipIterator.next()
                        ipTable[clientHardwareAddress] = ip
                        ip
                    }
                    val dhcpOfferPacket = DHCPResponseFactory.makeDHCPOffer(
                        dhcpPacket,
                        InetAddress.getByName(ipAddress),
                        DHCP_LEASE_TIME,
                        serverAddress,
                        null,
                        null,
                    )
                    responsePacket.address = BROADCAST_ADDRESS
                    responsePacket.port = DHCPConstants.BOOTP_REPLY_PORT
                    responsePacket.data = dhcpOfferPacket.serialize()
                    socket?.send(responsePacket)
                    continue
                }

                // DHCPREQUEST
                if (messageType == DHCPConstants.DHCPREQUEST) {
                    val responsePacket = DatagramPacket(ByteArray(DHCP_PACKET_SIZE), DHCP_PACKET_SIZE)
                    val ipAddress = ipTable[clientHardwareAddress] ?: continue
                    val dhcpAckPacket = DHCPResponseFactory.makeDHCPAck(
                        dhcpPacket,
                        InetAddress.getByName(ipAddress),
                        DHCP_LEASE_TIME,
                        serverAddress,
                        null,
                        null,
                    )
                    responsePacket.address = BROADCAST_ADDRESS
                    responsePacket.port = DHCPConstants.BOOTP_REPLY_PORT
                    responsePacket.data = dhcpAckPacket.serialize()
                    socket?.send(responsePacket)
                    val historyItem = AddressAssignHistory(ZonedDateTime.now(), clientHostName ?: "inconnu", ipAddress)
                    _history.add(historyItem)
                    listener?.onAddressAssign(historyItem)
                    continue
                }
            }
        }
    }

    fun stop() {
        isStarted = false
        socket?.close()
        connectionThread?.cancel()
    }

    companion object {
        fun normalizeMacAddress(macAddress: String): String = macAddress.replace(Regex("[:-]"), "").uppercase()
    }
}
