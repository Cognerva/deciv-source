package com.unciv.utils

/**
 * Calculates a SHA-1 digest without depending on a platform security provider.
 *
 * Some supported runtimes, notably RoboVM's Java compatibility layer, do not
 * expose the standard [java.security.MessageDigest] SHA-1 provider.
 */
fun sha1(data: ByteArray): ByteArray {
    val messageLength = ((data.size + 9 + 63) / 64) * 64
    val message = ByteArray(messageLength)
    data.copyInto(message)
    message[data.size] = 0x80.toByte()

    val bitLength = data.size.toLong() * 8L
    for (index in 0 until 8) {
        message[messageLength - 1 - index] = (bitLength ushr (index * 8)).toByte()
    }

    var h0 = 0x67452301
    var h1 = 0xEFCDAB89.toInt()
    var h2 = 0x98BADCFE.toInt()
    var h3 = 0x10325476
    var h4 = 0xC3D2E1F0.toInt()
    val schedule = IntArray(80)

    for (chunkStart in message.indices step 64) {
        for (index in 0 until 16) {
            val byteIndex = chunkStart + index * 4
            schedule[index] =
                ((message[byteIndex].toInt() and 0xFF) shl 24) or
                ((message[byteIndex + 1].toInt() and 0xFF) shl 16) or
                ((message[byteIndex + 2].toInt() and 0xFF) shl 8) or
                (message[byteIndex + 3].toInt() and 0xFF)
        }
        for (index in 16 until 80) {
            val value = schedule[index - 3] xor schedule[index - 8] xor
                schedule[index - 14] xor schedule[index - 16]
            schedule[index] = (value shl 1) or (value ushr 31)
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4

        for (index in 0 until 80) {
            val function: Int
            val constant: Int
            when (index) {
                in 0..19 -> {
                    function = (b and c) or (b.inv() and d)
                    constant = 0x5A827999
                }
                in 20..39 -> {
                    function = b xor c xor d
                    constant = 0x6ED9EBA1
                }
                in 40..59 -> {
                    function = (b and c) or (b and d) or (c and d)
                    constant = 0x8F1BBCDC.toInt()
                }
                else -> {
                    function = b xor c xor d
                    constant = 0xCA62C1D6.toInt()
                }
            }

            val rotatedA = (a shl 5) or (a ushr 27)
            val temporary = rotatedA + function + e + constant + schedule[index]
            e = d
            d = c
            c = (b shl 30) or (b ushr 2)
            b = a
            a = temporary
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    val digest = ByteArray(20)
    val words = intArrayOf(h0, h1, h2, h3, h4)
    for (wordIndex in words.indices) {
        val word = words[wordIndex]
        val byteIndex = wordIndex * 4
        digest[byteIndex] = (word ushr 24).toByte()
        digest[byteIndex + 1] = (word ushr 16).toByte()
        digest[byteIndex + 2] = (word ushr 8).toByte()
        digest[byteIndex + 3] = word.toByte()
    }
    return digest
}
