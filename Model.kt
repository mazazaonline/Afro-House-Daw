package com.afrohouse.daw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val SR = 44100
const val STEPS = 16

class Track(val name: String, sample: FloatArray, pattern: List<Int> = emptyList(), vol: Float = 0.8f, pan: Float = 0f) {
    @Volatile var sample: FloatArray = sample
    var sampleLabel by mutableStateOf("built-in")
    val steps = mutableStateListOf<Boolean>().apply { repeat(STEPS) { add(it in pattern) } }
    var volume by mutableFloatStateOf(vol)
    var pan by mutableFloatStateOf(pan)
    var mute by mutableStateOf(false)
    var solo by mutableStateOf(false)
}

class Project {
    @Volatile var bpm = 122f
    @Volatile var swing = 0.15f // 0..0.5, delays odd 16ths
    val tracks = mutableStateListOf<Track>()

    companion object {
        fun afroHouseDemo() = Project().apply {
            tracks += Track("Kick", Synth.kick(), listOf(0, 4, 8, 12), 0.95f)
            tracks += Track("Clap", Synth.clap(), listOf(4, 12), 0.7f, 0.1f)
            tracks += Track("Closed Hat", Synth.hat(0.05f), listOf(2, 6, 10, 14), 0.45f, -0.2f)
            tracks += Track("Open Hat", Synth.hat(0.22f), listOf(6, 14), 0.4f, 0.2f)
            tracks += Track("Shaker", Synth.shaker(), (0 until 16).toList(), 0.3f, 0.3f)
            tracks += Track("Conga", Synth.conga(), listOf(3, 7, 10, 15), 0.6f, -0.35f)
            tracks += Track("Perc", Synth.rim(), listOf(5, 13), 0.4f, 0.4f)
            tracks += Track("Bass", Synth.bass(), listOf(2, 3, 6, 10, 11, 14), 0.8f)
            tracks += Track("Stab", Synth.stab(), listOf(3, 10), 0.45f, 0.05f)
        }
    }
}
