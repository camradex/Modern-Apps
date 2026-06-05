package com.vayunmathur.passwords.util

object Cbor {
    private const val TYPE_UNSIGNED_INT = 0x00
    private const val TYPE_NEGATIVE_INT = 0x01
    private const val TYPE_BYTE_STRING = 0x02
    private const val TYPE_TEXT_STRING = 0x03
    private const val TYPE_ARRAY = 0x04
    private const val TYPE_MAP = 0x05

    fun encode(data: Any): ByteArray {
        if (data is Number) {
            val value = data.toLong()
            return if (value >= 0) createArg(TYPE_UNSIGNED_INT, value)
            else createArg(TYPE_NEGATIVE_INT, -1 - value)
        }
        if (data is ByteArray) {
            return createArg(TYPE_BYTE_STRING, data.size.toLong()) + data
        }
        if (data is String) {
            return createArg(TYPE_TEXT_STRING, data.length.toLong()) + data.encodeToByteArray()
        }
        if (data is List<*>) {
            var ret = createArg(TYPE_ARRAY, data.size.toLong())
            for (i in data) ret += encode(i!!)
            return ret
        }
        if (data is Map<*, *>) {
            // CTAP2 canonical CBOR: shorter keys first, then lexicographic
            var ret = createArg(TYPE_MAP, data.size.toLong())
            val byteMap = linkedMapOf<ByteArray, ByteArray>()
            for (entry in data) {
                byteMap[encode(entry.key!!)] = encode(entry.value!!)
            }
            val sortedKeys = byteMap.keys.sortedWith(Comparator { a, b ->
                if (a.size != b.size) return@Comparator a.size - b.size
                for (i in a.indices) {
                    val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
                    if (diff != 0) return@Comparator diff
                }
                0
            })
            for (key in sortedKeys) {
                ret += key
                ret += byteMap[key]!!
            }
            return ret
        }
        throw IllegalArgumentException("Unsupported CBOR type: ${data::class}")
    }

    private fun createArg(type: Int, arg: Long): ByteArray {
        val t = type shl 5
        val a = arg.toInt()
        if (arg < 24) return byteArrayOf(((t or a) and 0xFF).toByte())
        if (arg <= 0xFF) return byteArrayOf(((t or 24) and 0xFF).toByte(), (a and 0xFF).toByte())
        if (arg <= 0xFFFF) return byteArrayOf(
            ((t or 25) and 0xFF).toByte(),
            ((a shr 8) and 0xFF).toByte(),
            (a and 0xFF).toByte()
        )
        return byteArrayOf(
            ((t or 26) and 0xFF).toByte(),
            ((a shr 24) and 0xFF).toByte(),
            ((a shr 16) and 0xFF).toByte(),
            ((a shr 8) and 0xFF).toByte(),
            (a and 0xFF).toByte()
        )
    }
}
