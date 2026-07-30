package com.tripletriad.storage

import kotlin.random.Random

/**
 * Thrown when a save file cannot be read back — wrong magic, odd hex, or a failed checksum.
 *
 * A distinct type rather than [IllegalArgumentException] so a caller can tell "this file is not one
 * of ours / is damaged" apart from a programming error, and can offer the user something better
 * than a stack trace.
 */
class SaveCorruptException(message: String) : Exception(message)

/**
 * Turns save JSON into an opaque blob, and back.
 *
 * ### What this is, and what it is not
 *
 * It is **obfuscation**, not encryption, and the distinction is worth being blunt about: the key is
 * a constant in this file, so it ships with every copy of the app and anyone who wants the
 * plaintext can have it. That is not a shortcoming of this implementation — it is inherent to
 * client-side save protection, and it was equally true of the original, whose AES key was the pixel
 * data of an embedded GIF (`utils/CryptoHelper.as` + `assets/tto_key.gif`). The purpose is to stop
 * a save being edited in Notepad, and it does that.
 *
 * ### Not compatible with AS3 `.sav` files
 *
 * The original wrote `Hex.fromArray(AESKey(pixels).encrypt(bytes))`. Reproducing it would mean
 * decoding a GIF to get 31×31 ARGB pixels — 3844 bytes handed to a cipher that takes 16, 24 or 32 —
 * and matching whatever `com.hurlant.crypto`'s raw-block behaviour did with the remainder, with no
 * `.sav` file in the repository to validate against. So the wire format is **new**, and legacy
 * files are not read. What is preserved is what was asked for: the save *contents*
 * ([com.tripletriad.model.GameSave] is field-for-field `Save.DATAS`) and the fact that a save on
 * disk is not human-readable.
 *
 * ### Format
 *
 * ```
 * TTO1<salt:8 hex><body:2 hex per byte>
 * body = (checksum:4 bytes big-endian ++ utf8(json)) xor keystream(salt)
 * ```
 *
 * - **Hex, not Base64.** `CryptoHelper` produced hex and the `.sav` files were hex text, so this
 *   keeps a save recognisably the same kind of artefact. It also avoids
 *   `kotlin.io.encoding.Base64`, which would need an opt-in. A save is a few kilobytes; the 2×
 *   is irrelevant.
 * - **Per-file salt**, so two profiles with similar contents do not produce visibly similar files —
 *   which a fixed keystream would, and which is the one thing that makes XOR obfuscation trivially
 *   readable by eye.
 * - **Checksum before the payload**, verified after deobfuscating, so a truncated or hand-edited
 *   file is reported as [SaveCorruptException] instead of being handed to the JSON parser as
 *   garbage. It is FNV-1a: a corruption detector, not a MAC. Someone who knows the format can
 *   recompute it.
 */
object SaveCodec {
    /** Marks the format and its version. Bump the digit if the layout below ever changes. */
    const val MAGIC: String = "TTO1"

    private const val SALT_HEX_LENGTH = INT_BYTES * 2
    private const val HEADER_LENGTH = MAGIC_LENGTH + SALT_HEX_LENGTH
    private const val CHECKSUM_BYTES = INT_BYTES

    /**
     * Mixed into the keystream seed alongside the per-file salt, so the salt alone is not the whole
     * key. A constant in the binary, and openly so — see the class comment.
     */
    private const val KEY: Int = 0x54_54_4F_21 // "TTO!"

    private const val FNV_OFFSET_BASIS: Int = -0x7EE3623B // 0x811C9DC5
    private const val FNV_PRIME: Int = 0x01000193

    // xorshift32's shift triple. Any other triple from Marsaglia's table would do; these must not
    // change, because a save written with them has to stay readable.
    private const val SHIFT_A = 13
    private const val SHIFT_B = 17
    private const val SHIFT_C = 5

    /**
     * Obfuscates [json].
     *
     * @param random source of the per-file salt. A parameter, not a global, so a test can pin the
     *   exact bytes a given input produces — the same reason [com.tripletriad.model.MatchState]
     *   takes one.
     */
    fun encode(json: String, random: Random = Random.Default): String {
        val salt = nonZeroSalt(random)
        val plain = json.encodeToByteArray()
        val payload = ByteArray(CHECKSUM_BYTES + plain.size)
        writeInt(payload, 0, fnv1a(plain))
        plain.copyInto(payload, CHECKSUM_BYTES)
        applyKeystream(payload, salt)
        return MAGIC + salt.toHex() + payload.toHex()
    }

    /**
     * Reverses [encode].
     *
     * @throws SaveCorruptException if [blob] is not a save of this format, or has been damaged.
     */
    fun decode(blob: String): String {
        val text = blob.trim()
        ensureIntact(text.startsWith(MAGIC)) { "not a $MAGIC save file" }

        val bodyLength = text.length - HEADER_LENGTH
        ensureIntact(bodyLength >= CHECKSUM_BYTES * 2 && bodyLength % 2 == 0) {
            "save file is truncated (${text.length} chars)"
        }

        val salt = parseHexInt(text.substring(MAGIC_LENGTH, HEADER_LENGTH))
        val payload = hexToBytes(text.substring(HEADER_LENGTH))
        applyKeystream(payload, salt)

        val expected = readInt(payload, 0)
        val plain = payload.copyOfRange(CHECKSUM_BYTES, payload.size)
        ensureIntact(fnv1a(plain) == expected) {
            "save file checksum does not match; it has been altered"
        }
        return plain.decodeToString()
    }

    /**
     * XORs [bytes] in place with the keystream for [salt]. Its own inverse, which is why one
     * function serves both directions.
     *
     * xorshift32 rather than a library PRNG: [Random] gives no guarantee that a given seed
     * yields the same sequence across Kotlin versions or platforms, and a save written by the
     * Android build must be readable by the desktop build in five years. Ten lines of arithmetic
     * that cannot change is the right trade; the quality of the stream is irrelevant here.
     */
    private fun applyKeystream(bytes: ByteArray, salt: Int) {
        // Seeded from salt xor KEY, forced non-zero: xorshift is stuck at 0 forever.
        var state = (salt xor KEY).let { if (it == 0) 1 else it }
        for (i in bytes.indices) {
            state = state xor (state shl SHIFT_A)
            state = state xor (state ushr SHIFT_B)
            state = state xor (state shl SHIFT_C)
            bytes[i] = (bytes[i].toInt() xor (state and BYTE_MASK)).toByte()
        }
    }

    /** FNV-1a, 32-bit. Chosen for being short enough to read and verify by eye. */
    private fun fnv1a(bytes: ByteArray): Int {
        var hash = FNV_OFFSET_BASIS
        for (byte in bytes) {
            hash = (hash xor (byte.toInt() and BYTE_MASK)) * FNV_PRIME
        }
        return hash
    }

    /** A salt that is never 0, so [applyKeystream]'s guard is never the thing that saves us. */
    private fun nonZeroSalt(random: Random): Int {
        val salt = random.nextInt()
        return if (salt == 0) 1 else salt
    }
}

// ---------------------------------------------------------------------------------------------
// Hex and big-endian Int helpers.
//
// File-private rather than members of [SaveCodec]: they are format plumbing with no knowledge of
// saves, and keeping them out leaves the object at five functions that each say something about the
// format.
// ---------------------------------------------------------------------------------------------

private const val MAGIC_LENGTH = 4
private const val INT_BYTES = 4
private const val BYTE_MASK = 0xFF
private const val NIBBLE_MASK = 0xF
private const val BITS_PER_NIBBLE = 4
private const val BITS_PER_BYTE = 8
private const val HEX_DIGITS = "0123456789abcdef"

/** Raises [SaveCorruptException] unless [intact]. The single throw site for a damaged file. */
private inline fun ensureIntact(intact: Boolean, message: () -> String) {
    if (!intact) throw SaveCorruptException(message())
}

private fun Int.toHex(): String = buildString(INT_BYTES * 2) {
    for (nibble in (INT_BYTES * 2 - 1) downTo 0) {
        append(HEX_DIGITS[(this@toHex ushr (nibble * BITS_PER_NIBBLE)) and NIBBLE_MASK])
    }
}

private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val int = byte.toInt() and BYTE_MASK
        append(HEX_DIGITS[int ushr BITS_PER_NIBBLE])
        append(HEX_DIGITS[int and NIBBLE_MASK])
    }
}

private fun hexToBytes(hex: String): ByteArray {
    val bytes = ByteArray(hex.length / 2)
    for (i in bytes.indices) {
        val high = hexDigit(hex[i * 2]) shl BITS_PER_NIBBLE
        bytes[i] = (high or hexDigit(hex[i * 2 + 1])).toByte()
    }
    return bytes
}

private fun parseHexInt(hex: String): Int {
    var value = 0
    for (char in hex) {
        value = (value shl BITS_PER_NIBBLE) or hexDigit(char)
    }
    return value
}

private fun hexDigit(char: Char): Int {
    val digit = HEX_DIGITS.indexOf(char.lowercaseChar())
    ensureIntact(digit >= 0) { "save file contains non-hex character '$char'" }
    return digit
}

private fun writeInt(target: ByteArray, offset: Int, value: Int) {
    for (i in 0 until INT_BYTES) {
        target[offset + i] = (value ushr ((INT_BYTES - 1 - i) * BITS_PER_BYTE)).toByte()
    }
}

private fun readInt(source: ByteArray, offset: Int): Int {
    var value = 0
    for (i in 0 until INT_BYTES) {
        value = (value shl BITS_PER_BYTE) or (source[offset + i].toInt() and BYTE_MASK)
    }
    return value
}
