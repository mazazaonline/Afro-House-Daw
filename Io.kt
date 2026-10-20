package com.afrohouse.daw

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SampleLoader {
    private const val MAX_SECONDS = 20

    /** Decodes any Android-supported audio file (wav/mp3/ogg/m4a/flac) to mono float @ 44.1 kHz. */
    fun load(ctx: Context, uri: Uri): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { ex.selectTrack(i); fmt = f; break }
        }
        requireNotNull(fmt) { "No audio track found" }
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var acc = FloatArray(SR * 2); var len = 0
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false
        try {
            while (!outDone && len < sr * MAX_SECONDS) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    val b: ByteBuffer = codec.getOutputBuffer(o)!!
                    b.position(info.offset); b.limit(info.offset + info.size)
                    val sb = b.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val frames = sb.remaining() / ch
                    if (len + frames > acc.size) acc = acc.copyOf(maxOf(acc.size * 2, len + frames))
                    for (f in 0 until frames) {
                        var m = 0f
                        for (c in 0 until ch) m += sb.get()
                        acc[len++] = m / ch / 32768f
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    sr = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        } finally { codec.stop(); codec.release(); ex.release() }
        val mono = acc.copyOf(len)
        return if (sr == SR) mono else resample(mono, sr)
    }

    private fun resample(src: FloatArray, from: Int): FloatArray {
        val outLen = (src.size.toLong() * SR / from).toInt()
        val out = FloatArray(outLen)
        val ratio = from.toDouble() / SR
        for (i in out.indices) {
            val x = i * ratio; val i0 = x.toInt(); val fr = (x - i0).toFloat()
            val a = src[i0.coerceAtMost(src.size - 1)]; val b = src[(i0 + 1).coerceAtMost(src.size - 1)]
            out[i] = a + (b - a) * fr
        }
        return out
    }
}

object Exporter {
    /** Offline-renders [bars] bars (plus a 1.5 s tail) and writes 16-bit stereo WAV. */
    fun renderWav(project: Project, bars: Int, out: OutputStream) {
        val r = Renderer(project)
        val spf = SR * 60.0 / project.bpm / 4.0
        val loopFrames = (bars * STEPS * spf).toInt()
        val total = loopFrames + (1.5 * SR).toInt()
        val bytes = total * 4
        val bo = java.io.BufferedOutputStream(out)
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(SR).putInt(SR * 4).putShort(4).putShort(16)
        h.put("data".toByteArray()).putInt(bytes)
        bo.write(h.array())
        val buf = FloatArray(1024 * 2)
        val pcm = ByteBuffer.allocate(1024 * 4).order(ByteOrder.LITTLE_ENDIAN)
        var done = 0
        while (done < total) {
            val n = minOf(1024, total - done)
            r.triggering = done < loopFrames
            r.render(buf, n)
            pcm.clear()
            for (i in 0 until n * 2) pcm.putShort((buf[i] * 32767f).toInt().toShort())
            bo.write(pcm.array(), 0, n * 4)
            done += n
        }
        bo.flush()
    }
}

/** MP3: Android's MediaCodec has no MP3 encoder. Plug LAME (NDK) in here — see DESIGN.md. */
interface Mp3Encoder { fun encode(pcmStereo16: ShortArray, frames: Int, out: OutputStream); fun finish(out: OutputStream) }
