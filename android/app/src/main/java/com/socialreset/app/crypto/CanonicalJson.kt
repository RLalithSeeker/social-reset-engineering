package com.socialreset.app.crypto

/**
 * Deterministic serializer for signed material.
 *
 * A signature over "whatever JSON the serializer happened to emit" is not verifiable
 * across two implementations, so every signed structure is first flattened into a
 * canonical form: keys sorted, no whitespace, no floating point, strings escaped
 * identically on both sides.
 *
 * Accepted value types: String, Long, Int, Boolean, null, Map<String, *>, List<*>, Enum.
 * Anything else is a programming error and throws, rather than silently signing a
 * `toString()` the peer cannot reproduce.
 */
object CanonicalJson {

    fun encode(value: Any?): String = StringBuilder().also { writeValue(it, value) }.toString()

    fun encodeToBytes(value: Any?): ByteArray = encode(value).toByteArray(Charsets.UTF_8)

    private fun writeValue(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Int -> out.append(value.toString())
            is Long -> out.append(value.toString())
            is Map<*, *> -> writeObject(out, value)
            is List<*> -> writeArray(out, value)
            is Enum<*> -> writeString(out, value.name)
            else -> throw IllegalArgumentException(
                "Not canonicalizable: ${value.javaClass.name}. Convert before signing."
            )
        }
    }

    private fun writeObject(out: StringBuilder, map: Map<*, *>) {
        val keys = map.keys.map {
            it as? String ?: throw IllegalArgumentException("Canonical object keys must be String")
        }.sorted()
        out.append('{')
        keys.forEachIndexed { index, key ->
            if (index > 0) out.append(',')
            writeString(out, key)
            out.append(':')
            writeValue(out, map[key])
        }
        out.append('}')
    }

    private fun writeArray(out: StringBuilder, list: List<*>) {
        out.append('[')
        list.forEachIndexed { index, item ->
            if (index > 0) out.append(',')
            writeValue(out, item)
        }
        out.append(']')
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\u0008' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (ch < ' ') {
                    out.append("\\u").append(String.format("%04x", ch.code))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
    }
}
