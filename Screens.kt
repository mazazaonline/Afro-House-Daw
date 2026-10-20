package com.afrohouse.daw

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PatternScreen(project: Project, player: Player, onImport: (Track) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pattern", color = Color.Gray, fontSize = 12.sp)
            PATTERN_NAMES.forEachIndexed { i, n -> Pill(n, project.editPattern == i) { project.editPattern = i } }
            Spacer(Modifier.weight(1f))
            Pill("Clear", false) {
                for (t in project.tracks) for (k in 0 until STEPS) t.steps[project.editPattern * STEPS + k] = false
            }
        }
        Text("Play here loops this pattern on every track. Place patterns as clips in Playlist.", color = Color.Gray, fontSize = 11.sp)
        StepRuler(player)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = project.tracks, key = { it.id }) { t -> PadRow(t, project.editPattern, onImport) }
            item {
                Box {
                    Pill("+ Add sound", false) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        SOUNDS.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { menu = false; project.addSound(s) })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun StepRuler(player: Player) {
    var step by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) { while (true) { step = player.currentStep; delay(30) } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until STEPS) {
            Box(
                Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i == step) Color.White else if (i % 4 == 0) Color(0xFF4A4470) else SURFACE2)
            )
            if (i % 4 == 3 && i < STEPS - 1) Spacer(Modifier.width(3.dp))
        }
    }
}

@Composable
fun PadRow(t: Track, pat: Int, onImport: (Track) -> Unit) {
    val col = PALETTE[t.color % PALETTE.size]
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SURFACE).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(col))
            Column(Modifier.weight(1f).clickable { onImport(t) }) {
                Text(t.name, color = Color.White, fontSize = 14.sp)
                Text(t.sampleLabel + "  · tap to import", color = Color.Gray, fontSize = 10.sp, maxLines = 1)
            }
            MiniToggle("M", t.mute, PINK) { t.mute = !t.mute }
            MiniToggle("S", t.solo, Color(0xFFFFD23F)) { t.solo = !t.solo }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until STEPS) {
                val on = t.steps[pat * STEPS + i]
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (on) col else if (i % 4 == 0) Color(0xFF3A3560) else SURFACE2)
                        .clickable { t.steps[pat * STEPS + i] = !on }
                )
                if (i % 4 == 3 && i < STEPS - 1) Spacer(Modifier.width(3.dp))
            }
        }
    }
}

@Composable
fun Knob(label: String, value: Float, range: ClosedFloatingPointRange<Float>, color: Color = PINK, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.width(84.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
            modifier = Modifier.weight(1f).height(30.dp)
        )
    }
}

@Composable
fun MixerScreen(project: Project, player: Player) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SURFACE).padding(8.dp)) {
                Text("Master", color = ORANGE, fontSize = 14.sp)
                Knob("BPM ${project.bpm.toInt()}", project.bpm, 90f..140f, ORANGE) { project.bpm = it }
                Knob("Swing", project.swing, 0f..0.4f, ORANGE) { project.swing = it }
                Knob("Delay", project.delayMix, 0f..0.6f, ORANGE) { project.delayMix = it }
                Knob("Feedback", project.delayFeedback, 0f..0.7f, ORANGE) { project.delayFeedback = it }
                Spacer(Modifier.height(4.dp))
                Pill("Save project", false) {
                    val ok = Store.save(ctx, project)
                    android.widget.Toast.makeText(ctx, if (ok) "Project saved" else "Save failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        itemsIndexed(items = project.tracks, key = { _, t -> t.id }) { i, t ->
            val col = PALETTE[t.color % PALETTE.size]
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SURFACE).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                    Text(t.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.width(90.dp))
                    Meter(player, i, col, Modifier.weight(1f))
                }
                Knob("Volume", t.volume, 0f..1f, col) { t.volume = it }
                Knob("Pan", t.pan, -1f..1f, col) { t.pan = it }
                Knob("Pitch ${t.pitch.toInt()}", t.pitch, -12f..12f, col) { t.pitch = it }
                Knob("Filter", t.cutoff, 0.05f..1f, col) { t.cutoff = it }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
