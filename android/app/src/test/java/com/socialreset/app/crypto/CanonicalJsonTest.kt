package com.socialreset.app.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CanonicalJsonTest {
    @Test
    fun encodeSortsObjectKeysRecursively() {
        val encoded = CanonicalJson.encode(
            mapOf(
                "z" to 3,
                "a" to mapOf("b" to true, "a" to "first"),
                "m" to listOf("x", 2L, null),
            )
        )

        assertEquals("""{"a":{"a":"first","b":true},"m":["x",2,null],"z":3}""", encoded)
    }

    @Test
    fun encodeRejectsFloatsForSignedMaterial() {
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalJson.encode(mapOf("bad" to 1.5))
        }
    }
}
