package com.vayunmathur.messages.meta

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MetaByter {

    class Writer {
        private val buffer = ByteArrayOutputStream()

        fun writeByte(value: Int): Writer {
            buffer.write(value and 0xFF)
            return this
        }

        fun writeUint16BE(value: Int): Writer {
            buffer.write((value shr 8) and 0xFF)
            buffer.write(value and 0xFF)
            return this
        }

        fun writeUint16LE(value: Int): Writer {
            buffer.write(value and 0xFF)
            buffer.write((value shr 8) and 0xFF)
            return this
        }

        fun writeStringWithUint16Length(s: String): Writer {
            val bytes = s.toByteArray(Charsets.UTF_8)
            writeUint16BE(bytes.size)
            buffer.write(bytes)
            return this
        }

        fun writeRawBytes(bytes: ByteArray): Writer {
            buffer.write(bytes)
            return this
        }

        fun writeVLQ(value: Int): Writer {
            var v = value
            do {
                var encodedByte = (v and 0x7F)
                v = v shr 7
                if (v > 0) {
                    encodedByte = encodedByte or 0x80
                }
                buffer.write(encodedByte)
            } while (v > 0)
            return this
        }

        fun toByteArray(): ByteArray = buffer.toByteArray()
        fun size(): Int = buffer.size()
    }

    class Reader(private val data: ByteArray) {
        var position: Int = 0
            private set

        val remaining: Int get() = data.size - position

        fun readByte(): Int {
            check(remaining >= 1) { "Not enough bytes to read" }
            return data[position++].toInt() and 0xFF
        }

        fun readUint16BE(): Int {
            check(remaining >= 2) { "Not enough bytes for uint16" }
            val hi = data[position++].toInt() and 0xFF
            val lo = data[position++].toInt() and 0xFF
            return (hi shl 8) or lo
        }

        fun readUint16LE(): Int {
            check(remaining >= 2) { "Not enough bytes for uint16" }
            val lo = data[position++].toInt() and 0xFF
            val hi = data[position++].toInt() and 0xFF
            return (hi shl 8) or lo
        }

        fun readStringWithUint16Length(): String {
            val length = readUint16BE()
            check(remaining >= length) { "Not enough bytes for string of length $length" }
            val str = String(data, position, length, Charsets.UTF_8)
            position += length
            return str
        }

        fun readBytes(count: Int): ByteArray {
            check(remaining >= count) { "Not enough bytes: need $count, have $remaining" }
            val result = data.copyOfRange(position, position + count)
            position += count
            return result
        }

        fun readVLQ(): Int {
            var multiplier = 1
            var value = 0
            do {
                check(remaining >= 1) { "Not enough bytes for VLQ" }
                val encodedByte = data[position++].toInt() and 0xFF
                value += (encodedByte and 0x7F) * multiplier
                if (multiplier > 2097152) error("Malformed VLQ encoded data")
                multiplier *= 128
                if ((encodedByte and 0x80) == 0) break
            } while (true)
            return value
        }

        fun readRemainingBytes(): ByteArray {
            val result = data.copyOfRange(position, data.size)
            position = data.size
            return result
        }
    }
}
