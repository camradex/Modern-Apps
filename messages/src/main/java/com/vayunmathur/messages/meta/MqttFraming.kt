package com.vayunmathur.messages.meta

import android.util.Log

object MqttFraming {

    private const val TAG = "MqttFraming"

    private const val PROTOCOL_NAME = "MQIsdp"
    private const val PROTOCOL_CLIENT_ID = "mqttwsclient"
    private const val PROTOCOL_LEVEL = 3
    private const val KEEPALIVE_TIMEOUT = 15

    fun buildConnectPacket(connectJson: String): ByteArray {
        val connectFlags = MqttPackets.ConnectFlags(
            cleanSession = true,
            username = true,
        ).toByte()

        val payloadWriter = MetaByter.Writer()
        // Protocol Name (length-prefixed)
        payloadWriter.writeStringWithUint16Length(PROTOCOL_NAME)
        // Protocol Level
        payloadWriter.writeByte(PROTOCOL_LEVEL)
        // Connect Flags
        payloadWriter.writeByte(connectFlags.toInt() and 0xFF)
        // Keep Alive
        payloadWriter.writeUint16BE(KEEPALIVE_TIMEOUT)
        // Client ID (length-prefixed)
        payloadWriter.writeStringWithUint16Length(PROTOCOL_CLIENT_ID)
        // Username/JSON data (length-prefixed)
        payloadWriter.writeStringWithUint16Length(connectJson)

        val payload = payloadWriter.toByteArray()

        // Header: packet byte + VLQ remaining length
        val headerWriter = MetaByter.Writer()
        headerWriter.writeByte(MqttPackets.compressConnect().toInt() and 0xFF)
        headerWriter.writeVLQ(payload.size)

        val header = headerWriter.toByteArray()
        return header + payload
    }

    fun buildPublishPacket(
        topic: String,
        jsonData: String,
        qos: Byte = MqttPackets.QOS_LEVEL_1,
        packetId: Int,
    ): ByteArray {
        val payloadWriter = MetaByter.Writer()
        // Topic (length-prefixed)
        payloadWriter.writeStringWithUint16Length(topic)
        // Packet ID (for QoS > 0)
        if (qos > 0) {
            payloadWriter.writeUint16BE(packetId)
        }
        // JSON payload (raw bytes, no length prefix)
        payloadWriter.writeRawBytes(jsonData.toByteArray(Charsets.UTF_8))

        val payload = payloadWriter.toByteArray()

        val packetByte = MqttPackets.compressPublish(qos = qos)
        val headerWriter = MetaByter.Writer()
        headerWriter.writeByte(packetByte.toInt() and 0xFF)
        headerWriter.writeVLQ(payload.size)

        return headerWriter.toByteArray() + payload
    }

    fun buildSubscribePacket(topic: String, qos: Byte, packetId: Int): ByteArray {
        val payloadWriter = MetaByter.Writer()
        // Packet ID
        payloadWriter.writeUint16BE(packetId)
        // Topic (length-prefixed)
        payloadWriter.writeStringWithUint16Length(topic)
        // QoS
        payloadWriter.writeByte(qos.toInt() and 0xFF)

        val payload = payloadWriter.toByteArray()

        val headerWriter = MetaByter.Writer()
        headerWriter.writeByte(MqttPackets.compressSubscribe().toInt() and 0xFF)
        headerWriter.writeVLQ(payload.size)

        return headerWriter.toByteArray() + payload
    }

    fun buildPingReqPacket(): ByteArray {
        return byteArrayOf((MqttPackets.PINGREQ shl 4).toByte(), 0x00)
    }

    fun buildPubAckPacket(messageId: Int): ByteArray {
        return byteArrayOf(
            (MqttPackets.PUBACK shl 4).toByte(),
            0x02,
            ((messageId shr 8) and 0xFF).toByte(),
            (messageId and 0xFF).toByte(),
        )
    }

    data class ParsedPacket(
        val packetType: Int,
        val qos: Int,
        val remainingLength: Int,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ParsedPacket) return false
            return packetType == other.packetType && qos == other.qos &&
                remainingLength == other.remainingLength && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = packetType
            result = 31 * result + qos
            result = 31 * result + remainingLength
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    fun parsePacket(data: ByteArray): ParsedPacket? {
        if (data.isEmpty()) return null
        val reader = MetaByter.Reader(data)
        val packetByte = reader.readByte()
        val packetType = packetByte shr 4
        val qos = (packetByte shr 1) and 0x03
        val remainingLength = reader.readVLQ()
        val payload = if (reader.remaining >= remainingLength) {
            reader.readBytes(remainingLength)
        } else {
            reader.readRemainingBytes()
        }
        return ParsedPacket(packetType, qos, remainingLength, payload)
    }

    sealed class MqttResponse {
        data class ConnAck(val connectionCode: Int) : MqttResponse()
        data class PubAck(val packetId: Int) : MqttResponse()
        data class SubAck(val packetId: Int, val qosLevel: Int) : MqttResponse()
        data class PublishMessage(
            val topic: String,
            val packetId: Int,
            val qos: Int,
            val payload: ByteArray,
        ) : MqttResponse() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is PublishMessage) return false
                return topic == other.topic && packetId == other.packetId &&
                    qos == other.qos && payload.contentEquals(other.payload)
            }
            override fun hashCode(): Int {
                var result = topic.hashCode()
                result = 31 * result + packetId
                result = 31 * result + qos
                result = 31 * result + payload.contentHashCode()
                return result
            }
        }
        data object PingResp : MqttResponse()
    }

    fun parseResponse(data: ByteArray): MqttResponse? {
        val parsed = parsePacket(data) ?: return null
        val reader = MetaByter.Reader(parsed.payload)

        return when (parsed.packetType) {
            MqttPackets.CONNACK -> {
                if (parsed.payload.size >= 2) {
                    // byte 0 = session present flag, byte 1 = return code
                    MqttResponse.ConnAck(parsed.payload[1].toInt() and 0xFF)
                } else null
            }
            MqttPackets.PUBACK -> {
                if (parsed.payload.size >= 2) {
                    val packetId = reader.readUint16BE()
                    MqttResponse.PubAck(packetId)
                } else null
            }
            MqttPackets.SUBACK -> {
                if (parsed.payload.size >= 3) {
                    val packetId = reader.readUint16BE()
                    val qos = reader.readByte()
                    MqttResponse.SubAck(packetId, qos)
                } else null
            }
            MqttPackets.PUBLISH -> {
                val topic = reader.readStringWithUint16Length()
                val packetId = if (parsed.qos > 0 && reader.remaining >= 2) {
                    reader.readUint16BE()
                } else 0
                val payload = reader.readRemainingBytes()
                MqttResponse.PublishMessage(topic, packetId, parsed.qos, payload)
            }
            MqttPackets.PINGRESP -> MqttResponse.PingResp
            else -> {
                Log.w(TAG, "Unknown packet type: ${parsed.packetType}")
                null
            }
        }
    }
}
