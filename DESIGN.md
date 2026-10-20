# Afro-House DAW (Android) — Technical Design v0.1

## Goals
Full multitrack DAW for Afro-House: built-in sounds + imported samples, export WAV and MP3.

## Architecture
UI (Jetpack Compose) → Project model → Audio engine → Output (AudioTrack live / file offline)

- **Project model**: Project (bpm, swing, tracks) → Track (sample, 16-step pattern, volume, pan, mute, solo).
- **Renderer**: one sample-accurate mixer used for BOTH live playback and offline export (what you hear = what you export).
- **Player**: audio thread, AudioTrack float stereo, low-latency mode.
- **SampleLoader**: MediaCodec decode of wav/mp3/ogg/m4a/flac → mono float 44.1 kHz.
- **Synth**: procedural kick, clap, hats, shaker, conga, rim, sub bass, minor-7 stab.
- **Exporter**: offline render → 16-bit WAV via Storage Access Framework (no storage permission needed).

## Afro-House defaults
118–126 BPM (default 122), 4-on-the-floor kick, syncopated congas/perc, rolling off-beat bass, shaker 16ths, swing on odd 16ths.

## Roadmap
1. **v0.1 (this)**: pattern sequencer, 9 built-in tracks, import samples, mixer basics, WAV export.
2. **v0.2**: Oboe (C++) engine for lower latency; timeline/arrangement with clips and sections (intro/build/drop/break); save/load projects (JSON + sample files).
3. **v0.3**: per-track FX (EQ, filter, delay, reverb, sidechain compression); piano roll for bass/chords/melody; stereo samples, pitch/time-stretch.
4. **v0.4**: MP3 export, audio recording, automation lanes, undo/redo, MIDI input, bundled royalty-free Afro-House sample packs.

## MP3 export
Android MediaCodec has no MP3 encoder. Plan: bundle LAME (libmp3lame) through the NDK + JNI, implement the `Mp3Encoder` interface in Io.kt, and feed it the same offline render used for WAV. (Fallback: AAC/.m4a via MediaCodec.) Note LAME is LGPL — dynamic linking required.

## Known limits of v0.1
- Not yet compiled/tested on a device — open in Android Studio and fix any build errors.
- Single 16-step bar pattern; export loops it 8 bars.
- Imported samples downmixed to mono, max 20 s.
- Model uses Compose state directly (fine for a prototype; split into a pure engine model later).
- No project save yet.
