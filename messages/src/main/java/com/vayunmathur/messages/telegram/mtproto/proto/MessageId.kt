package com.vayunmathur.messages.telegram.mtproto.proto

import java.util.concurrent.atomic.AtomicLong

object MessageId {
    private val lastNano = AtomicLong(0)
    private val seen = LinkedHashSet<Long>()
    private const val MAX_SEEN = 1000
    private const val MIN_RESOLUTION_NANOS = 10L

    fun generate(): Long {
        val nowNano = System.currentTimeMillis() * 1_000_000L
        val nano = lastNano.updateAndGet { prev ->
            if (nowNano > prev) nowNano else prev + MIN_RESOLUTION_NANOS
        }
        val seconds = nano / 1_000_000_000L
        val fracPart = (nano % 1_000_000_000L) and -4L
        return (seconds shl 32) or fracPart
    }

    fun reset() {
        lastNano.set(0)
    }

    fun isReplay(msgId: Long): Boolean {
        synchronized(seen) {
            if (msgId in seen) return true
            seen.add(msgId)
            while (seen.size > MAX_SEEN) {
                val iter = seen.iterator()
                iter.next()
                iter.remove()
            }
            return false
        }
    }
}
