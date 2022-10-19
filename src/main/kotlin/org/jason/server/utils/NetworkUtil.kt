package org.jason.server.utils

import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtil {
    
    fun getLocalIPAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val element = interfaces.nextElement()
                val addresses = element.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress && inetAddress.isIPV4()) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private fun InetAddress.isIPV4(): Boolean {
        return "\\d+\\.\\d+\\.\\d+\\.\\d+".toRegex().find(this.hostAddress.orEmpty())?.groupValues?.isNotEmpty() == true
    }
}