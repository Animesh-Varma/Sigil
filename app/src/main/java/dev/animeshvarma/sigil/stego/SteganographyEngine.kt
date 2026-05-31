package dev.animeshvarma.sigil.stego

import dev.animeshvarma.sigil.model.TextStegoMethod

interface TextStegoStrategy {
    fun encode(coverText: String, secretData: String): String
    fun decode(stegoText: String): String
}

object SteganographyEngine {

    fun encode(cover: String, secret: String, method: TextStegoMethod): String {
        val strategy = getStrategy(method)
        return strategy.encode(cover, secret)
    }

    fun decode(stego: String, method: TextStegoMethod): String {
        val strategy = getStrategy(method)
        return strategy.decode(stego)
    }

    private fun getStrategy(method: TextStegoMethod): TextStegoStrategy {
        return when (method) {
            TextStegoMethod.ZERO_WIDTH -> ZeroWidthStrategy
            TextStegoMethod.WHITESPACE_PLACEHOLDER -> throw UnsupportedOperationException("Whitespace Strategy is currently under construction.")
            TextStegoMethod.SYNONYM_LLM_PLACEHOLDER -> throw UnsupportedOperationException("LLM Strategy is currently under construction.")
        }
    }

    internal fun stringToBinary(text: String): String {
        return text.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            byte.toUByte().toString(2).padStart(8, '0')
        }
    }

    internal fun binaryToString(binary: String): String {
        val bytes = binary.chunked(8).map { it.toUByte(2).toByte() }.toByteArray()
        return String(bytes, Charsets.UTF_8)
    }
}

object ZeroWidthStrategy : TextStegoStrategy {
    private const val BIT_0 = '\u200C'
    private const val BIT_1 = '\u200D'

    private const val MAGIC_HEADER = "SGL_START"
    private const val MAGIC_FOOTER = "SGL_END"

    override fun encode(coverText: String, secretData: String): String {
        val cleanCover = coverText.replace(BIT_0.toString(), "").replace(BIT_1.toString(), "").trim()

        val wrappedSecret = MAGIC_HEADER + secretData + MAGIC_FOOTER
        val binary = SteganographyEngine.stringToBinary(wrappedSecret)

        val sequence = StringBuilder()
        for (bit in binary) {
            sequence.append(if (bit == '0') BIT_0 else BIT_1)
        }

        if (cleanCover.isEmpty()) return sequence.toString()

        val result = StringBuilder()
        val seqLen = sequence.length
        val coverLen = cleanCover.length

        var seqIdx = 0
        for (i in 0 until coverLen) {
            result.append(cleanCover[i])
            val charsToAppend = (seqLen - seqIdx) / (coverLen - i)
            for (j in 0 until charsToAppend) {
                result.append(sequence[seqIdx++])
            }
        }
        while (seqIdx < seqLen) {
            result.append(sequence[seqIdx++])
        }

        return result.toString()
    }

    override fun decode(stegoText: String): String {
        val allInvisibles = stegoText.filter { it == BIT_0 || it == BIT_1 }

        if (allInvisibles.isEmpty()) {
            throw IllegalArgumentException("Extraction Failed: No hidden data found in the provided text.")
        }

        val extractedBinary = StringBuilder(allInvisibles.length)
        for (char in allInvisibles) {
            extractedBinary.append(if (char == BIT_0) "0" else "1")
        }

        val binaryStr = extractedBinary.toString()
        val headerBinary = SteganographyEngine.stringToBinary(MAGIC_HEADER)
        val footerBinary = SteganographyEngine.stringToBinary(MAGIC_FOOTER)

        val startIndex = binaryStr.indexOf(headerBinary)
        if (startIndex == -1) {
            throw IllegalArgumentException("Extraction Failed: Invalid data signature. The text does not contain a valid Sigil payload.")
        }

        val payloadStartIndex = startIndex + headerBinary.length

        val endIndex = binaryStr.lastIndexOf(footerBinary)
        if (endIndex == -1 || endIndex < payloadStartIndex) {
            throw IllegalArgumentException("Extraction Failed: Payload is truncated or corrupted (Missing EOF marker).")
        }

        val payloadBinary = binaryStr.substring(payloadStartIndex, endIndex)

        if (payloadBinary.length % 8 != 0) {
            throw IllegalArgumentException("Extraction Failed: Bitstream misalignment. Data was stripped by the platform.")
        }

        return SteganographyEngine.binaryToString(payloadBinary)
    }
}