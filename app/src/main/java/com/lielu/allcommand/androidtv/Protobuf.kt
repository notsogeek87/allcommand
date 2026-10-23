package com.lielu.allcommand.androidtv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Protobuf minimal à la main : les messages « Android TV Remote v2 » ne sont que des entiers, des chaînes et
 * des messages imbriqués, ce qui ne justifie ni la bibliothèque protobuf ni un plugin Gradle.
 */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(
        field: Int,
        value: Long,
    ): ProtoWriter {
        writeVarint(field.toLong() shl 3 or WIRE_VARINT)
        writeVarint(value)
        return this
    }

    fun bytes(
        field: Int,
        value: ByteArray,
    ): ProtoWriter {
        writeVarint(field.toLong() shl 3 or WIRE_LENGTH_DELIMITED)
        writeVarint(value.size.toLong())
        out.write(value)
        return this
    }

    fun string(
        field: Int,
        value: String,
    ): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(
        field: Int,
        build: ProtoWriter.() -> Unit,
    ): ProtoWriter = bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    /** Le message précédé de sa longueur : le cadrage utilisé sur les deux ports de la télé. */
    fun toFrame(): ByteArray {
        val body = toByteArray()
        return ProtoWriter().also { it.writeVarint(body.size.toLong()) }.toByteArray() + body
    }

    private fun writeVarint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write((v and 0x7F or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private companion object {
        const val WIRE_VARINT = 0L
        const val WIRE_LENGTH_DELIMITED = 2L
    }
}

/** Champs d'un message décodé : entiers ([Long]) et blocs ([ByteArray], chaîne ou message imbriqué). */
internal class ProtoMessage(
    private val fields: Map<Int, List<Any>>,
) {
    fun has(field: Int): Boolean = fields.containsKey(field)

    fun long(field: Int): Long? = fields[field]?.firstOrNull() as? Long

    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray

    fun message(field: Int): ProtoMessage? = bytes(field)?.let(::decode)

    companion object {
        fun decode(data: ByteArray): ProtoMessage {
            val fields = mutableMapOf<Int, MutableList<Any>>()
            var pos = 0

            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size || shift > MAX_SHIFT) throw IllegalArgumentException("Message tronqué")
                    val b = data[pos++].toInt()
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (pos < data.size) {
                val tag = readVarint()
                val field = (tag ushr 3).toInt()
                val value: Any =
                    when ((tag and 7).toInt()) {
                        0 -> readVarint()
                        2 -> {
                            val length = readVarint().toInt()
                            if (length < 0 ||
                                pos + length > data.size
                            ) {
                                throw IllegalArgumentException("Message tronqué")
                            }
                            data.copyOfRange(pos, pos + length).also { pos += length }
                        }
                        1 -> data.copyOfRange(pos, pos + 8).also { pos += 8 }
                        5 -> data.copyOfRange(pos, pos + 4).also { pos += 4 }
                        else -> throw IllegalArgumentException("Type de champ inconnu")
                    }
                fields.getOrPut(field) { mutableListOf() }.add(value)
            }
            return ProtoMessage(fields)
        }

        /** Lit un message précédé de sa longueur ; [EOFException] si la connexion est fermée. */
        fun readFrame(input: InputStream): ProtoMessage {
            var length = 0
            var shift = 0
            while (true) {
                val b = input.read()
                if (b < 0) throw EOFException()
                length = length or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > MAX_FRAME_SHIFT) throw IllegalArgumentException("Cadre invalide")
            }
            if (length < 0 || length > MAX_FRAME_BYTES) throw IllegalArgumentException("Cadre invalide")
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) throw EOFException()
                read += n
            }
            return decode(body)
        }

        private const val MAX_SHIFT = 63
        private const val MAX_FRAME_SHIFT = 28
        private const val MAX_FRAME_BYTES = 1 shl 20
    }
}
