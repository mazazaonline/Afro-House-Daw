package com.afrohouse.daw

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Step-sequencer mixer. Same code drives live playback and offline export,
 * so what you hear is what you render.
 */
class Renderer(private val p: Project) {
    private class Voice(val data: FloatArray, val gl: Float, val gr: Float) { var i = 0 }

    private val voices = ArrayList<Voice>()
    private var pos = 0L
    private var stepCounter = 0L
    private var nextStart = 0.0
    @Volatile var currentStep = -1; private set
    var triggering = true

    fun render(out: FloatArray, frames: Int) {
        java.util.Arrays.fill(out, 0, frames * 2, 0f)
        val spf = SR * 60.0 / p.bpm / 4.0
        val anySolo = p.tracks.any { it.solo }
        for (n in 0 until frames) {
            if (triggering && pos >= nextStart) {
                val s = (stepCounter % STEPS).toInt()
                currentStep = s
                for (t in p.tracks) {
                    if (!t.steps[s] || t.mute || (anySolo && !t.solo)) continue
                    val a = ((t.pan.coerceIn(-1f, 1f) + 1f) * PI.toFloat() / 4f)
                    if (voices.size < 96) voices += Voice(t.sample, cos(a) * t.volume, sin(a) * t.volume)
                }
                stepCounter++
                nextStart = stepCounter * spf + if (stepCounter % 2 == 1L) p.swing * spf else 0.0
            }
            var l = 0f; var r = 0f
            val it = voices.iterator()
            while (it.hasNext()) {
                val v = it.next()
                val x = v.data[v.i++]
                l += x * v.gl; r += x * v.gr
                if (v.i >= v.data.size) it.remove()
            }
            out[n * 2] = (l * 0.7f).coerceIn(-1f, 1f)
            out[n * 2 + 1] = (r * 0.7f).coerceIn(-1f, 1f)
            pos++
        }
    }
}

class Player(private val project: Project) {
    @Volatile private var running = false
    @Volatile private var renderer: Renderer? = null
    private var thread: Thread? = null
    val isPlaying get() = running
    val currentStep get() = if (running) renderer?.currentStep ?: -1 else -1

    fun start() {
        if (running) return
        running = true
        val r = Renderer(project); renderer = r
        thread = Thread {
            val min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            val at = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(maxOf(min, 4096 * 2 * 4))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            at.play()
            val buf = FloatArray(512 * 2)
            while (running) { r.render(buf, 512); at.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING) }
            at.stop(); at.release()
        }.also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    fun stop() { running = false; thread?.join(500); thread = null }
}
