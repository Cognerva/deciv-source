package com.unciv.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class Sha1Tests {
    @Test
    fun emptyInput() {
        assertEquals(
            "da39a3ee5e6b4b0d3255bfef95601890afd80709",
            sha1(ByteArray(0)).toHex()
        )
    }

    @Test
    fun abc() {
        assertEquals(
            "a9993e364706816aba3e25717850c26c9cd0d89d",
            sha1("abc".toByteArray()).toHex()
        )
    }

    @Test
    fun longInput() {
        assertEquals(
            "34aa973cd4c4daa4f61eeb2bdbad27316534016f",
            sha1("a".repeat(1_000_000).toByteArray()).toHex()
        )
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
