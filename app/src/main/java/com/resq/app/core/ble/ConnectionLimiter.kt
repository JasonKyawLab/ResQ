package com.resq.app.core.ble

/**
 * Caps how many helper phones can be connected to this sender at once, so a slow or
 * hostile helper cannot hold every slot. A phone already admitted is admitted again
 * (a repeated "connected" event is not a second connection).
 */
class ConnectionLimiter(private val max: Int) {
    private val admitted = LinkedHashSet<String>()

    @Synchronized
    fun tryAdmit(address: String): Boolean {
        if (address in admitted) return true
        if (admitted.size >= max) return false
        admitted.add(address)
        return true
    }

    /** Safe to call for an address that was never admitted (a rejected phone disconnecting). */
    @Synchronized
    fun release(address: String) {
        admitted.remove(address)
    }

    @Synchronized
    fun clear() = admitted.clear()

    @Synchronized
    fun count(): Int = admitted.size
}
