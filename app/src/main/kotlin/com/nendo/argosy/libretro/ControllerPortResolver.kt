package com.nendo.argosy.libretro

import android.view.InputDevice
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.swordfish.libretrodroid.PortResolver

/**
 * Seats pads on the port they are assigned in settings, and otherwise on the first input they
 * send. Android numbers controllers per device rather than per session and leaves 0 on pads it
 * cannot number, so seating by those numbers can make the pad in the player's hands player two:
 * on a handheld docked to a TV the built-in controls take the number and the pad being played on
 * takes what is left. Whoever presses something first is player one instead.
 */
class ControllerPortResolver : PortResolver {
    private var controllerOrder: Map<String, Int> = emptyMap()
    private val claimedPorts = mutableMapOf<String, Int>()

    var onPortClaimed: ((controllerId: String, port: Int) -> Unit)? = null

    fun setControllerOrder(orders: List<ControllerOrderEntity>) {
        controllerOrder = orders.associate { it.controllerId to it.port }
        claimedPorts.keys.removeAll(controllerOrder.keys)
    }

    fun clearControllerOrder() {
        controllerOrder = emptyMap()
        claimedPorts.clear()
    }

    /**
     * Gives up the seats of pads that are no longer connected, so the next pad to send something
     * can take player one rather than inheriting a disconnected pad's leftovers. Assignments made
     * in settings are kept: they describe a pad that is expected back.
     */
    fun releaseDisconnected(connectedControllerIds: Set<String>) {
        claimedPorts.keys.retainAll(connectedControllerIds)
    }

    fun claimedPortFor0(): String? = claimedPorts.entries.firstOrNull { it.value == 0 }?.key

    override fun getPort(device: InputDevice): Int {
        val controllerId = getControllerId(device)
        controllerOrder[controllerId]?.let { return it }
        claimedPorts[controllerId]?.let { return it }
        return claimPort(controllerId)
    }

    /**
     * The port this pad holds right now, without seating it. For describing pads rather than
     * routing their input: asking is not playing, and it must not take player one.
     */
    fun peekPort(device: InputDevice): Int? {
        val controllerId = getControllerId(device)
        return controllerOrder[controllerId] ?: claimedPorts[controllerId]
    }

    fun getPort(controllerId: String, fallbackControllerNumber: Int): Int {
        return controllerOrder[controllerId]
            ?: claimedPorts[controllerId]
            ?: (fallbackControllerNumber - 1).coerceAtLeast(0)
    }

    fun hasCustomOrder(): Boolean = controllerOrder.isNotEmpty()

    private fun claimPort(controllerId: String): Int {
        val taken = controllerOrder.values.toSet() + claimedPorts.values.toSet()
        var port = 0
        while (port in taken) port++
        claimedPorts[controllerId] = port
        onPortClaimed?.invoke(controllerId, port)
        return port
    }

    private fun getControllerId(device: InputDevice): String {
        return "${device.vendorId}:${device.productId}:${device.descriptor}"
    }

    companion object {
        fun getControllerId(device: InputDevice): String {
            return "${device.vendorId}:${device.productId}:${device.descriptor}"
        }
    }
}
