package com.afrohouse.daw

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Procedural built-in sounds (mono, 44.1 kHz). Replace later with bundled sample packs. */
object Synth {
    private fun n(sec: Float) = (sec * SR).toInt()

    fun kick(): FloatArray {
        val out = FloatArray(n(0.45f)); var ph = 0.0
        for (i in out.indices) {
            val t = i.toFloat() / SR
            val f = 42 + 130 * exp(-t * 28f)
            ph += 2 * PI * f / SR
            out[i] = (sin(ph) * exp(-t * 7f) * 1.1).toFloat().coerceIn(-1f, 1f)
        }
        return out
    }

    fun clap(): FloatArray {
        val out = FloatArray(n(0.25f)); val r = Random(1); var prev = 0f
        for (i in out.indices) {
            val t = i.toFloat() / SR
            val x = r.nextFloat() * 2 - 1
            val hp = x - prev; prev = x
            val burst = listOf(0f, 0.011f, 0.022f).sumOf { o -> if (t >= o) exp(-(t - o) * 120f).toDouble() else 0.0 }.toFloat()
            out[i] = hp * 0.5f * (burst * 0.5f + exp(-(t - 0.022f).coerceAtLeast(0f) * 18f) * 0.4f)
        }
        return out
    }

    fun hat(decay: Float): FloatArray {
        val out = FloatArray(n(decay * 3)); val r = Random(2); var prev = 0f
        for (i in out.indices) {
            val x = r.nextFloat() * 2 - 1; val hp = x - prev; prev = x
            out[i] = hp * 0.5f * exp(-(i.toFloat() / SR) / decay * 3f)
        }
        return out
    }

    fun shaker(): FloatArray {
        val out = FloatArray(n(0.12f)); val r = Random(3); var p1 = 0f; var p2 = 0f
        for (i in out.indices) {
            val t = i.toFloat() / SR
            val x = r.nextFloat() * 2 - 1
            val bp = (x - p1) * 0.5f + p2 * 0.3f; p1 = x; p2 = bp
            val env = (t / 0.01f).coerceAtMost(1f) * exp(-t * 28f)
            out[i] = bp * env * 0.6f
        }
        return out
    }

    fun conga(): FloatArray {
        val out = FloatArray(n(0.3f)); var ph = 0.0
        for (i in out.indices) {
            val t = i.toFloat() / SR
            ph += 2 * PI * (190 + 40 * exp(-t * 40f)) / SR
            out[i] = ((sin(ph) + 0.3 * sin(ph * 2.3)) * exp(-t * 14f) * 0.7).toFloat()
        }
        return out
    }

    fun rim(): FloatArray {
        val out = FloatArray(n(0.1f))
        for (i in out.indices) {
            val t = i.toFloat() / SR
            out[i] = (sin(2 * PI * 820 * t) * 0.6 + sin(2 * PI * 1240 * t) * 0.3).toFloat() * exp(-t * 55f)
        }
        return out
    }

    /** A1 sub bass with a touch of saturation. */
    fun bass(): FloatArray {
        val out = FloatArray(n(0.3f))
        for (i in out.indices) {
            val t = i.toFloat() / SR
            val s = sin(2 * PI * 55 * t).toFloat() * 1.6f
            out[i] = (s / (1 + kotlin.math.abs(s))) * (t / 0.004f).coerceAtMost(1f) * exp(-t * 6f)
        }
        return out
    }

    /** Dark minor-7 stab (A, C, E, G). */
    fun stab(): FloatArray {
        val out = FloatArray(n(0.5f))
        val freqs = doubleArrayOf(220.0, 261.63, 329.63, 392.0)
        for (i in out.indices) {
            val t = i.toDouble() / SR
            var s = 0.0
            for (f in freqs) { s += sin(2 * PI * f * t) + 0.35 * sin(2 * PI * f * 2 * t) + 0.15 * sin(2 * PI * f * 3 * t) }
            out[i] = (s * 0.12 * exp(-t * 7.0) * (t / 0.003).coerceAtMost(1.0)).toFloat()
        }
        return out
    }
}
