package com.dominikgold.tonka.backup

import okio.Buffer
import okio.BufferedSink

class ZipFormatException(message: String) : Exception(message)

// A store-only (uncompressed) zip writer and reader; images are JPEGs already, so compression would gain little
class StoreZipWriter(private val sink: BufferedSink) {

    private class CentralEntry(val name: ByteArray, val crc: Long, val size: Long, val offset: Long)

    private val entries = mutableListOf<CentralEntry>()
    private var offset = 0L

    fun add(name: String, bytes: ByteArray) {
        val nameBytes = name.encodeToByteArray()
        val crc = Crc32.of(bytes)
        val header = Buffer().apply {
            writeIntLe(LOCAL_HEADER_SIGNATURE)
            writeShortLe(VERSION)
            writeShortLe(UTF8_FLAG)
            writeShortLe(METHOD_STORED)
            writeShortLe(0)
            writeShortLe(DOS_DATE_1980)
            writeIntLe(crc.toInt())
            writeIntLe(bytes.size)
            writeIntLe(bytes.size)
            writeShortLe(nameBytes.size)
            writeShortLe(0)
            write(nameBytes)
        }
        val headerSize = header.size
        sink.writeAll(header)
        sink.write(bytes)
        entries += CentralEntry(nameBytes, crc, bytes.size.toLong(), offset)
        offset += headerSize + bytes.size
    }

    fun finish() {
        val centralStart = offset
        var centralSize = 0L
        for (entry in entries) {
            val record = Buffer().apply {
                writeIntLe(CENTRAL_HEADER_SIGNATURE)
                writeShortLe(VERSION)
                writeShortLe(VERSION)
                writeShortLe(UTF8_FLAG)
                writeShortLe(METHOD_STORED)
                writeShortLe(0)
                writeShortLe(DOS_DATE_1980)
                writeIntLe(entry.crc.toInt())
                writeIntLe(entry.size.toInt())
                writeIntLe(entry.size.toInt())
                writeShortLe(entry.name.size)
                writeShortLe(0)
                writeShortLe(0)
                writeShortLe(0)
                writeShortLe(0)
                writeIntLe(0)
                writeIntLe(entry.offset.toInt())
                write(entry.name)
            }
            centralSize += record.size
            sink.writeAll(record)
        }
        sink.writeIntLe(END_SIGNATURE)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(entries.size)
        sink.writeShortLe(entries.size)
        sink.writeIntLe(centralSize.toInt())
        sink.writeIntLe(centralStart.toInt())
        sink.writeShortLe(0)
        sink.flush()
    }

    internal companion object {
        const val LOCAL_HEADER_SIGNATURE = 0x04034b50
        const val CENTRAL_HEADER_SIGNATURE = 0x02014b50
        const val END_SIGNATURE = 0x06054b50
        const val VERSION = 20
        const val UTF8_FLAG = 0x0800
        const val METHOD_STORED = 0
        const val DOS_DATE_1980 = 0x21
    }
}

object StoreZipReader {

    fun read(bytes: ByteArray): Map<String, ByteArray> {
        val endOffset = (bytes.size - END_RECORD_SIZE downTo maxOf(0, bytes.size - END_RECORD_SIZE - MAX_COMMENT))
            .firstOrNull { bytes.intLe(it) == StoreZipWriter.END_SIGNATURE }
            ?: throw ZipFormatException("Not a backup file")
        val entryCount = bytes.shortLe(endOffset + 10)
        var cursor = bytes.intLe(endOffset + 16)
        val result = linkedMapOf<String, ByteArray>()
        repeat(entryCount) {
            if (cursor < 0 || cursor + 46 > bytes.size || bytes.intLe(cursor) != StoreZipWriter.CENTRAL_HEADER_SIGNATURE) {
                throw ZipFormatException("The backup's file index is damaged")
            }
            val method = bytes.shortLe(cursor + 10)
            val crc = bytes.intLe(cursor + 16).toLong() and 0xFFFFFFFFL
            val compressedSize = bytes.intLe(cursor + 20)
            val nameLength = bytes.shortLe(cursor + 28)
            val extraLength = bytes.shortLe(cursor + 30)
            val commentLength = bytes.shortLe(cursor + 32)
            val localOffset = bytes.intLe(cursor + 42)
            val name = bytes.copyOfRange(cursor + 46, cursor + 46 + nameLength).decodeToString()
            if (method != StoreZipWriter.METHOD_STORED) throw ZipFormatException("Compressed entries are not supported")
            if (bytes.intLe(localOffset) != StoreZipWriter.LOCAL_HEADER_SIGNATURE) throw ZipFormatException("A file in the backup is damaged")
            val dataStart = localOffset + 30 + bytes.shortLe(localOffset + 26) + bytes.shortLe(localOffset + 28)
            if (dataStart + compressedSize > bytes.size) throw ZipFormatException("The backup is truncated")
            val data = bytes.copyOfRange(dataStart, dataStart + compressedSize)
            if (Crc32.of(data) != crc) throw ZipFormatException("A file in the backup is corrupted: $name")
            result[name] = data
            cursor += 46 + nameLength + extraLength + commentLength
        }
        return result
    }

    private fun ByteArray.intLe(offset: Int): Int {
        if (offset < 0 || offset + 4 > size) return 0
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun ByteArray.shortLe(offset: Int): Int {
        if (offset < 0 || offset + 2 > size) return 0
        return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private const val END_RECORD_SIZE = 22
    private const val MAX_COMMENT = 0xFFFF
}

object Crc32 {

    private val table = LongArray(256) { index ->
        var value = index.toLong()
        repeat(8) { value = if (value and 1L != 0L) (value ushr 1) xor 0xEDB88320L else value ushr 1 }
        value
    }

    fun of(bytes: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (byte in bytes) {
            crc = table[((crc xor byte.toLong()) and 0xFF).toInt()] xor (crc ushr 8)
        }
        return crc xor 0xFFFFFFFFL
    }
}
