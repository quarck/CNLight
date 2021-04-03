// Credits to https://rosettacode.org/wiki/MD5/Implementation#Kotlin

package com.github.quarck.calnotify.utils

import com.github.quarck.calnotify.calendar.EventAlertRecord

// version 1.1.3

data class md5state(var a: Int, var b: Int, var c: Int, var d: Int) {

    override fun toString() : String {
        val sb = StringBuilder(80)
        for (i in 0..3) {
            var n = when (i) {0 -> a; 1 -> b; 2 -> c; else -> d }
            for (j in 0..3) {
                sb.append(String.format("%02x", n and 0xFF))
                n = n ushr 8
            }
        }
        return sb.toString()
    }
    fun toByteArray(): ByteArray {
        val md5 = ByteArray(16)
        var count = 0
        for (i in 0..3) {
            var n = when (i) {0 -> a; 1 -> b; 2 -> c; else -> d }
            for (j in 0..3) {
                md5[count++] = n.toByte()
                n = n ushr 8
            }
        }
        return md5
    }

    fun xor(other: md5state) {
        a = a xor other.a
        b = b xor other.b
        c = c xor other.c
        d = d xor other.d
    }
}

object MD5 {
    private val INIT_A = 0x67452301
    private val INIT_B = 0xEFCDAB89L.toInt()
    private val INIT_C = 0x98BADCFEL.toInt()
    private val INIT_D = 0x10325476

    private val SHIFT_AMTS = intArrayOf(
            7, 12, 17, 22,
            5,  9, 14, 20,
            4, 11, 16, 23,
            6, 10, 15, 21
    )

    private val TABLE_T = IntArray(64) {
        ((1L shl 32) * Math.abs(Math.sin(it + 1.0))).toLong().toInt()
    }

    fun compute(message: ByteArray): md5state {
        val messageLenBytes = message.size
        val numBlocks = ((messageLenBytes + 8) ushr 6) + 1
        val totalLen = numBlocks shl 6
        val paddingBytes = ByteArray(totalLen - messageLenBytes)
        paddingBytes[0] = 0x80.toByte()
        var messageLenBits = (messageLenBytes shl 3).toLong()


        for (i in 0..7) {
            paddingBytes[paddingBytes.size - 8 + i] = messageLenBits.toByte()
            messageLenBits = messageLenBits ushr 8
        }

        val s = md5state(INIT_A, INIT_B, INIT_C, INIT_D)
        val buffer = IntArray(16)

        for (i in 0 until numBlocks) {
            var index = i shl 6

            for (j in 0..63) {
                // temp = index < len ? message[index] : padding[index - len];
                val temp =
                        if (index < messageLenBytes)
                            message[index]
                        else
                            paddingBytes[index - messageLenBytes]
                buffer[j ushr 2] = (temp.toInt() shl 24) or (buffer[j ushr 2] ushr 8)
                index++
            }

            val originalS = s

            for (j in 0..63) {
                val div16 = j ushr 4
                var f = 0
                var bufferIndex = j
                when (div16) {
                    0 -> {
                        f = (s.b and s.c) or (s.b.inv() and s.d)
                    }

                    1 -> {
                        f = (s.b and s.d) or (s.c and s.d.inv())
                        bufferIndex = (bufferIndex * 5 + 1) and 0x0F
                    }

                    2 -> {
                        f = s.b xor s.c xor s.d;
                        bufferIndex = (bufferIndex * 3 + 5) and 0x0F
                    }

                    3 -> {
                        f = s.c xor (s.b or s.d.inv());
                        bufferIndex = (bufferIndex * 7) and 0x0F
                    }
                }

                val temp = s.b + Integer.rotateLeft(s.a + f + buffer[bufferIndex] +
                        TABLE_T[j], SHIFT_AMTS[(div16 shl 2) or (j and 3)])
                s.a = s.d
                s.d = s.c
                s.c = s.b
                s.b = temp
            }

            s.a += originalS.a
            s.b += originalS.b
            s.c += originalS.c
            s.d += originalS.d
        }

        return s
    }
}

fun ByteArray.toHexString(): String {
    val sb = StringBuilder(80)
    for (b in this) sb.append(String.format("%02x", b.toInt() and 0xFF))
    return sb.toString()
}

fun String.md5() = MD5.compute(this.toByteArray()).toString()