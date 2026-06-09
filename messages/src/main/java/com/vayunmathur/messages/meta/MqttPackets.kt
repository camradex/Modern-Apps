package com.vayunmathur.messages.meta

object MqttPackets {
    const val CONNECT = 1
    const val CONNACK = 2
    const val PUBLISH = 3
    const val PUBACK = 4
    const val SUBSCRIBE = 8
    const val SUBACK = 9
    const val UNSUBSCRIBE = 10
    const val UNSUBACK = 11
    const val PINGREQ = 12
    const val PINGRESP = 13
    const val DISCONNECT = 14

    const val DUP = 0x08
    const val RETAIN = 0x01

    const val QOS_LEVEL_0: Byte = 0x00
    const val QOS_LEVEL_1: Byte = 0x01
    const val QOS_LEVEL_2: Byte = 0x02

    fun compressConnect(): Byte = (CONNECT shl 4).toByte()

    fun compressPublish(qos: Byte = QOS_LEVEL_0, dup: Boolean = false, retain: Boolean = false): Byte {
        var result = PUBLISH shl 4
        if (dup) result = result or DUP
        result = result or ((qos.toInt() and 0x03) shl 1)
        if (retain) result = result or RETAIN
        return result.toByte()
    }

    fun compressSubscribe(): Byte = ((SUBSCRIBE shl 4) or 0x02).toByte()

    data class ConnectFlags(
        val username: Boolean = false,
        val password: Boolean = false,
        val retain: Boolean = false,
        val qos: Int = 0,
        val cleanSession: Boolean = false,
    ) {
        fun toByte(): Byte {
            var flags = 0
            if (username) flags = flags or 0x80
            if (password) flags = flags or 0x40
            if (retain) flags = flags or 0x20
            flags = flags or ((qos shl 3) and 0x18)
            if (cleanSession) flags = flags or 0x02
            return flags.toByte()
        }
    }
}
