package com.motompro.tcpconfig.app.util

import com.motompro.tcpconfig.app.TCPConfigApp
import java.net.Inet4Address

class IPRange(
    start: String,
    end: String,
) {

    val startIP: String
    val endIP: String

    /**
     * Check if the two IP addresses passed in constructor have a valid IPv4 format
     */
    val isValid = start.matches(TCPConfigApp.IP_ADDRESS_REGEX) && end.matches(TCPConfigApp.IP_ADDRESS_REGEX)

    /**
     * Get a list of every IP address situated between the two passed in constructor (both included)
     */
    val ipList = mutableListOf<String>()

    init {
        if (isValid) {
            // Convert to int and then to string to make sure addresses are well formatted (e.g. 192.168.1.054 -> 192.168.1.54)
            val formattedStartIP = start.split(".").map { it.toInt() }.joinToString(".")
            val formattedEndIP = end.split(".").map { it.toInt() }.joinToString(".")

            if (isIPSmallerThanOther(formattedStartIP, formattedEndIP)) {
                startIP = formattedStartIP
                endIP = formattedEndIP
            } else {
                startIP = formattedEndIP
                endIP = formattedStartIP
            }

            // Initialize list
            var currentIP = startIP
            val maxIP = getNextIP(endIP)
            while (currentIP != maxIP) {
                if (!currentIP.endsWith(".0") && !currentIP.endsWith(".255")) {
                    ipList.add(currentIP)
                }
                currentIP = getNextIP(currentIP)
            }
        } else {
            startIP = start
            endIP = end
        }
    }

    fun isOverlapping(range: IPRange): Boolean {
        return startIP == range.startIP || startIP == range.endIP || endIP == range.startIP || endIP == range.endIP ||
                (isIPSmallerThanOther(startIP, range.endIP) && isIPSmallerThanOther(range.startIP, startIP)) ||
                (isIPSmallerThanOther(endIP, range.endIP) && isIPSmallerThanOther(range.startIP, endIP)) ||
                (isIPSmallerThanOther(startIP, range.startIP) && isIPSmallerThanOther(range.endIP, endIP))
    }

    /**
     * Get the following IP address in ascending order (e.g. 192.168.0.1 -> 192.168.0.2)
     * @param ip the IP address
     * @return the IP address that follows the one passed in the function
     */
    private fun getNextIP(ip: String): String {
        val numbers = ip.split(".").map { it.toInt() }.toMutableList()
        if (numbers[3] == 255) {
            numbers[3] = 0
            if (numbers[2] == 255) {
                numbers[2] = 0
                if (numbers[1] == 255) {
                    numbers[1] = 0
                    if (numbers[0] == 255) {
                        numbers[0] = 0
                    } else {
                        numbers[0]++
                    }
                } else {
                    numbers[1]++
                }
            } else {
                numbers[2]++
            }
        } else {
            numbers[3]++
        }
        return numbers.joinToString(".")
    }

    companion object {
        /**
         * Check if the first IP address is before the second one in numeric order (e.g 192.168.0.1 is just before
         * 192.168.0.2)
         * @param ip1 the IP address to compare
         * @param ip2 the IP address we compare the first to
         * @return *true* if the address is before, *false* otherwise
         */
        fun isIPSmallerThanOther(ip1: String, ip2: String): Boolean {
            val address1 = ip1.split(".").map { it.toInt() }
            val address2 = ip2.split(".").map { it.toInt() }
            for (i in address1.indices) {
                if (address1[i] < address2[i]) return true
                if (address1[i] > address2[i]) return false
            }
            return false
        }
    }
}
