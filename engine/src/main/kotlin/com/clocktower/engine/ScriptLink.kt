package com.clocktower.engine

/**
 * Decodes share links from the official script tool
 * (script.bloodontheclocktower.com/?script=...): the parameter is
 * URL-encoded, base64-encoded, gzipped JSON. Implemented in pure Kotlin
 * (no java.util) so the same file runs on Android, the JVM and
 * WebAssembly.
 */
object ScriptLink {

    /** True when the text looks like a script link rather than raw JSON. */
    fun isLink(text: String): Boolean {
        val t = text.trim()
        return t.contains("script=") || t.startsWith("http://") || t.startsWith("https://")
    }

    /** Returns the decoded script JSON, or null if this isn't a valid link. */
    fun decode(text: String): String? {
        val trimmed = text.trim()
        val param = when {
            trimmed.contains("script=") -> trimmed.substringAfter("script=").substringBefore('&')
            else -> return null
        }
        return try {
            Inflate.gunzip(base64Decode(percentDecode(param))).decodeToString()
        } catch (e: Exception) {
            null
        }
    }

    /** application/x-www-form-urlencoded percent-decoding. */
    private fun percentDecode(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            when (val c = s[i]) {
                '%' -> {
                    require(i + 2 < s.length) { "truncated escape" }
                    sb.append(s.substring(i + 1, i + 3).toInt(16).toChar())
                    i += 3
                }
                '+' -> { sb.append(' '); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun base64Decode(s: String): ByteArray {
        val clean = s.filter { !it.isWhitespace() && it != '=' }
        val out = ByteArray(clean.length * 3 / 4)
        var buffer = 0
        var bitsHeld = 0
        var written = 0
        for (c in clean) {
            val value = B64.indexOf(c)
            require(value >= 0) { "bad base64 char '$c'" }
            buffer = (buffer shl 6) or value
            bitsHeld += 6
            if (bitsHeld >= 8) {
                bitsHeld -= 8
                out[written++] = ((buffer shr bitsHeld) and 0xFF).toByte()
            }
        }
        return if (written == out.size) out else out.copyOf(written)
    }
}

/**
 * Minimal, dependency-free gzip/DEFLATE decompressor (RFC 1951/1952)
 * following the classic "puff" reference algorithm: stored, fixed-Huffman
 * and dynamic-Huffman blocks — everything gzip can emit.
 */
internal object Inflate {

    fun gunzip(data: ByteArray): ByteArray {
        var pos = 0
        fun u8(): Int = data[pos++].toInt() and 0xFF
        require(u8() == 0x1f && u8() == 0x8b) { "not a gzip stream" }
        require(u8() == 8) { "unsupported compression method" }
        val flags = u8()
        pos += 6 // mtime(4), xfl, os
        if (flags and 0x04 != 0) { // FEXTRA
            val extraLen = u8() or (u8() shl 8)
            pos += extraLen
        }
        if (flags and 0x08 != 0) while (u8() != 0) Unit // FNAME (zero-terminated)
        if (flags and 0x10 != 0) while (u8() != 0) Unit // FCOMMENT
        if (flags and 0x02 != 0) pos += 2 // FHCRC
        return inflate(data, pos)
    }

    private class BitReader(private val data: ByteArray, startByte: Int) {
        var bitPos = startByte * 8

        /** Reads [n] bits, LSB-first per DEFLATE. */
        fun bits(n: Int): Int {
            var result = 0
            for (i in 0 until n) {
                val byte = data[bitPos ushr 3].toInt() and 0xFF
                result = result or (((byte ushr (bitPos and 7)) and 1) shl i)
                bitPos++
            }
            return result
        }

        fun alignToByte() {
            bitPos = (bitPos + 7) and (7.inv())
        }

        fun readAlignedByte(): Byte {
            val b = data[bitPos ushr 3]
            bitPos += 8
            return b
        }
    }

    /** Canonical Huffman decoder built from per-symbol code lengths. */
    private class Huffman(lengths: IntArray) {
        val counts = IntArray(16)
        val symbols: IntArray

        init {
            for (len in lengths) if (len > 0) counts[len]++
            val offsets = IntArray(16)
            var total = 0
            for (len in 1..15) {
                offsets[len] = total
                total += counts[len]
            }
            symbols = IntArray(total)
            for (symbol in lengths.indices) {
                val len = lengths[symbol]
                if (len > 0) symbols[offsets[len]++] = symbol
            }
        }
    }

    private fun decodeSymbol(reader: BitReader, huffman: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (len in 1..15) {
            code = code or reader.bits(1)
            val count = huffman.counts[len]
            if (code - first < count) return huffman.symbols[index + (code - first)]
            index += count
            first = (first + count) shl 1
            code = code shl 1
        }
        throw IllegalStateException("invalid Huffman code")
    }

    private val LEN_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LEN_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    )
    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )
    private val CLEN_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private val FIXED_LITLEN = Huffman(
        IntArray(288) { i ->
            when {
                i < 144 -> 8
                i < 256 -> 9
                i < 280 -> 7
                else -> 8
            }
        },
    )
    private val FIXED_DIST = Huffman(IntArray(30) { 5 })

    private fun inflate(data: ByteArray, startByte: Int): ByteArray {
        val reader = BitReader(data, startByte)
        var out = ByteArray(8 * 1024)
        var outSize = 0

        fun push(b: Byte) {
            if (outSize == out.size) out = out.copyOf(out.size * 2)
            out[outSize++] = b
        }

        while (true) {
            val isFinal = reader.bits(1) == 1
            when (val blockType = reader.bits(2)) {
                0 -> { // stored, byte-aligned
                    reader.alignToByte()
                    val lo = reader.readAlignedByte().toInt() and 0xFF
                    val hi = reader.readAlignedByte().toInt() and 0xFF
                    reader.bitPos += 16 // skip one's-complement length
                    val storedLen = lo or (hi shl 8)
                    repeat(storedLen) { push(reader.readAlignedByte()) }
                }
                1, 2 -> {
                    val tables = if (blockType == 1) {
                        FIXED_LITLEN to FIXED_DIST
                    } else {
                        readDynamicTables(reader)
                    }
                    val (litLen, dist) = tables
                    while (true) {
                        val symbol = decodeSymbol(reader, litLen)
                        when {
                            symbol < 256 -> push(symbol.toByte())
                            symbol == 256 -> break
                            else -> {
                                val lenIndex = symbol - 257
                                val copyLen = LEN_BASE[lenIndex] + reader.bits(LEN_EXTRA[lenIndex])
                                val distSymbol = decodeSymbol(reader, dist)
                                val distBack = DIST_BASE[distSymbol] + reader.bits(DIST_EXTRA[distSymbol])
                                repeat(copyLen) { push(out[outSize - distBack]) }
                            }
                        }
                    }
                }
                else -> throw IllegalStateException("invalid block type")
            }
            if (isFinal) break
        }
        return out.copyOf(outSize)
    }

    private fun readDynamicTables(reader: BitReader): Pair<Huffman, Huffman> {
        val hlit = reader.bits(5) + 257
        val hdist = reader.bits(5) + 1
        val hclen = reader.bits(4) + 4
        val clenLengths = IntArray(19)
        for (i in 0 until hclen) {
            clenLengths[CLEN_ORDER[i]] = reader.bits(3)
        }
        val clenHuffman = Huffman(clenLengths)

        val lengths = IntArray(hlit + hdist)
        var i = 0
        while (i < lengths.size) {
            when (val symbol = decodeSymbol(reader, clenHuffman)) {
                in 0..15 -> lengths[i++] = symbol
                16 -> {
                    require(i > 0) { "repeat with no previous length" }
                    val previous = lengths[i - 1]
                    repeat(3 + reader.bits(2)) { lengths[i++] = previous }
                }
                17 -> repeat(3 + reader.bits(3)) { lengths[i++] = 0 }
                18 -> repeat(11 + reader.bits(7)) { lengths[i++] = 0 }
                else -> throw IllegalStateException("bad code-length symbol $symbol")
            }
        }
        return Huffman(lengths.copyOfRange(0, hlit)) to Huffman(lengths.copyOfRange(hlit, hlit + hdist))
    }
}
