package com.example.ava.audio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WavFileWriter(
    private val context: Context,
    private val sampleRate: Int = 16000
) {
    private val outputStream = ByteArrayOutputStream(16384)

    fun write(buffer: ByteBuffer) {
        val byteArray = ByteArray(buffer.remaining())
        buffer.get(byteArray)
        outputStream.write(byteArray, 0, byteArray.size)
    }

    fun save() {
        val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
        val filename = "ava_rec_${formatter.format(Date())}"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$filename.wav")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/Ava")
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val audioData = outputStream.toByteArray()
            try {
                resolver.openOutputStream(it)?.use { wavStream ->
                    writeWavHeader(
                        wavStream,
                        audioData.size,
                        sampleRate,
                        1,
                        16
                    )
                    wavStream.write(audioData)
                }
            } catch (e: IOException) {
                Timber.e(e, "Error writing WAV file")
            }
        }
    }

    @Throws(IOException::class)
    private fun writeWavHeader(
        out: OutputStream,
        audioDataSize: Int,
        sampleRate: Int,
        channelCount: Int,
        bitDepth: Int
    ) {
        val byteRate = sampleRate * channelCount * bitDepth / 8
        val blockAlign = channelCount * bitDepth / 8
        val totalDataLen = audioDataSize + 36
        val header = ByteArray(44)

        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // 2 bytes: format type 1 for PCM
        header[21] = 0
        header[22] = channelCount.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = blockAlign.toByte()
        header[33] = 0
        header[34] = bitDepth.toByte()
        header[35] = 0

        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (audioDataSize and 0xff).toByte()
        header[41] = (audioDataSize shr 8 and 0xff).toByte()
        header[42] = (audioDataSize shr 16 and 0xff).toByte()
        header[43] = (audioDataSize shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }
}