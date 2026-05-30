package com.vayunmathur.messages.gmessages

import android.util.Base64
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.MessageOrBuilder
import com.google.protobuf.Message
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import com.google.protobuf.ByteString

/**
 * Encoder/decoder for Google's "PB-Lite" wire format. Direct port of the
 * reflection-driven implementation in
 * https://github.com/mautrix/go-util/tree/main/pblite.
 *
 * PB-Lite encodes a protobuf message as a positional JSON array where
 * index `i` holds field number `i + 1`. Holes are JSON null. Nested
 * messages become nested arrays. Repeated fields become flat arrays of
 * encoded values. Bytes-typed fields become base64 strings.
 *
 * Messages-for-Web uses this format for the SendMessage RPC body
 * (`Content-Type: application/json+protobuf`). Inbound responses use
 * regular binary protobuf — see [decodeProtobuf] in [RpcClient].
 *
 * Implementation reads/writes via the standard protobuf descriptor
 * reflection API (requires the full `protobuf-java` runtime, not the
 * `-lite` variant, since lite drops reflection).
 */
object PbLite {

    fun encode(message: MessageOrBuilder): String {
        val arr = serializeMessage(message)
        return arr.toString()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Message> decode(json: String, builderTemplate: Message.Builder): T {
        val parsed = JSONTokener(json).nextValue()
        require(parsed is JSONArray) {
            "expected top-level JSON array for pblite, got ${parsed?.javaClass?.simpleName}"
        }
        deserializeMessage(parsed, builderTemplate)
        return builderTemplate.build() as T
    }

    private fun serializeMessage(message: MessageOrBuilder): JSONArray {
        val desc = message.descriptorForType
        val fields = desc.fields
        // Find the highest field number to size the array.
        val maxFieldNumber = fields.maxOfOrNull { it.number } ?: 0
        val arr = JSONArray()
        // JSONArray doesn't have a fixed-size constructor; pad with nulls.
        repeat(maxFieldNumber) { arr.put(JSONObject.NULL) }
        for (field in fields) {
            if (!hasField(message, field)) continue
            val idx = field.number - 1
            val value = message.getField(field)
            arr.put(idx, encodeFieldValue(field, value))
        }
        return arr
    }

    /** Encode a single field value (may be a List for repeated fields). */
    private fun encodeFieldValue(field: FieldDescriptor, value: Any): Any {
        if (field.isRepeated) {
            val list = value as List<*>
            val arr = JSONArray()
            for (v in list) {
                arr.put(encodeScalar(field, v!!))
            }
            return arr
        }
        return encodeScalar(field, value)
    }

    private fun encodeScalar(field: FieldDescriptor, value: Any): Any = when (field.javaType) {
        FieldDescriptor.JavaType.INT -> (value as Number).toLong()
        FieldDescriptor.JavaType.LONG -> {
            // Match how Google's web client serializes int64 — values
            // outside the JS-safe range (|v| > 2^53) become quoted strings.
            // Server is more lenient when receiving but we mirror what
            // the real web client sends for forward-compatibility.
            val v = (value as Number).toLong()
            if (v > 9_007_199_254_740_992L || v < -9_007_199_254_740_992L) v.toString() else v
        }
        FieldDescriptor.JavaType.FLOAT, FieldDescriptor.JavaType.DOUBLE -> (value as Number).toDouble()
        FieldDescriptor.JavaType.BOOLEAN -> value as Boolean
        FieldDescriptor.JavaType.STRING -> value as String
        FieldDescriptor.JavaType.BYTE_STRING -> {
            val bs = value as ByteString
            Base64.encodeToString(bs.toByteArray(), Base64.NO_WRAP)
        }
        FieldDescriptor.JavaType.ENUM -> {
            val ev = value as com.google.protobuf.Descriptors.EnumValueDescriptor
            ev.number.toLong()
        }
        FieldDescriptor.JavaType.MESSAGE -> serializeMessage(value as MessageOrBuilder)
        else -> error("unsupported pblite java type ${field.javaType}")
    }

    /** Honour the protobuf3 "presence" rules: scalar fields are present
     *  iff they have a non-default value (the descriptor API exposes
     *  hasField only for messages + optional fields). */
    private fun hasField(message: MessageOrBuilder, field: FieldDescriptor): Boolean {
        if (field.isRepeated) return (message.getField(field) as List<*>).isNotEmpty()
        if (field.hasPresence()) return message.hasField(field)
        val v = message.getField(field)
        return when (field.javaType) {
            FieldDescriptor.JavaType.INT, FieldDescriptor.JavaType.LONG -> (v as Number).toLong() != 0L
            FieldDescriptor.JavaType.FLOAT, FieldDescriptor.JavaType.DOUBLE -> (v as Number).toDouble() != 0.0
            FieldDescriptor.JavaType.BOOLEAN -> v as Boolean
            FieldDescriptor.JavaType.STRING -> (v as String).isNotEmpty()
            FieldDescriptor.JavaType.BYTE_STRING -> !(v as ByteString).isEmpty
            FieldDescriptor.JavaType.ENUM -> (v as com.google.protobuf.Descriptors.EnumValueDescriptor).number != 0
            FieldDescriptor.JavaType.MESSAGE -> message.hasField(field)
            else -> false
        }
    }

    private fun deserializeMessage(arr: JSONArray, builder: Message.Builder) {
        val desc = builder.descriptorForType
        for (field in desc.fields) {
            val idx = field.number - 1
            if (idx >= arr.length()) continue
            if (arr.isNull(idx)) continue
            val raw = arr.get(idx)
            decodeFieldValue(field, raw, builder)
        }
    }

    private fun decodeFieldValue(field: FieldDescriptor, raw: Any, builder: Message.Builder) {
        if (field.isRepeated) {
            val arr = raw as? JSONArray ?: error("expected array for repeated ${field.fullName}")
            for (i in 0 until arr.length()) {
                if (arr.isNull(i)) continue
                builder.addRepeatedField(field, decodeScalar(field, arr.get(i), builder))
            }
            return
        }
        builder.setField(field, decodeScalar(field, raw, builder))
    }

    private fun decodeScalar(field: FieldDescriptor, raw: Any, parent: Message.Builder): Any = when (field.javaType) {
        FieldDescriptor.JavaType.INT -> coerceLong(raw).toInt()
        FieldDescriptor.JavaType.LONG -> coerceLong(raw)
        FieldDescriptor.JavaType.FLOAT -> coerceDouble(raw).toFloat()
        FieldDescriptor.JavaType.DOUBLE -> coerceDouble(raw)
        FieldDescriptor.JavaType.BOOLEAN -> when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.toBoolean()
            else -> error("can't coerce ${raw::class.simpleName} to Boolean")
        }
        FieldDescriptor.JavaType.STRING -> raw.toString()
        FieldDescriptor.JavaType.BYTE_STRING -> {
            val s = raw as String
            ByteString.copyFrom(Base64.decode(s, Base64.NO_WRAP))
        }
        FieldDescriptor.JavaType.ENUM -> {
            val n = coerceLong(raw).toInt()
            field.enumType.findValueByNumber(n) ?: field.enumType.values.first()
        }
        FieldDescriptor.JavaType.MESSAGE -> {
            val arr = raw as? JSONArray ?: error("expected array for message ${field.fullName}")
            val sub = parent.newBuilderForField(field)
            deserializeMessage(arr, sub)
            sub.build()
        }
        else -> error("unsupported pblite java type ${field.javaType}")
    }

    /**
     * PB-Lite encodes large int64s as JSON strings (to dodge JavaScript's
     * 53-bit safe-integer limit). Also tolerates numbers wrapped as
     * various Number subtypes by JSON parsers.
     */
    private fun coerceLong(raw: Any): Long = when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLong()
        else -> error("can't coerce ${raw::class.simpleName} to Long")
    }

    private fun coerceDouble(raw: Any): Double = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.toDouble()
        else -> error("can't coerce ${raw::class.simpleName} to Double")
    }
}
