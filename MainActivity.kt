package com.afrohouse.daw

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val project = Project.afroHouseDemo()
    private val player = Player(project)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Daw(project, player) } }
    }

    override fun onStop() { player.stop(); super.onStop() }
}

private val Amber = Color(0xFFE8A33D)

@Composable
fun Daw(project: Project, player: Player) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(-1) }
    var bpm by remember { mutableFloatStateOf(project.bpm) }
    var swing by remember { mutableFloatStateOf(project.swing) }
    var importTarget by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(playing) { while (playing) { step = player.currentStep; delay(30) }; step = -1 }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val t = importTarget ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) { SampleLoader.load(ctx, uri) }
                t.sample = data; t.sampleLabel = uri.lastPathSegment?.substringAfterLast('/') ?: "imported"
            } catch (e: Exception) { Toast.makeText(ctx, "Import failed: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)!!.use { Exporter.renderWav(project, 8, it) }
                }
                Toast.makeText(ctx, "Exported 8 bars as WAV", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF111111)).statusBarsPadding().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (playing) { player.stop(); playing = false } else { player.start(); playing = true } }) {
                Text(if (playing) "Stop" else "Play")
            }
            Text("${bpm.toInt()} BPM", color = Color.White)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { saver.launch("afro_house_loop.wav") }) { Text("Export WAV") }
        }
        Slider(value = bpm, onValueChange = { bpm = it; project.bpm = it }, valueRange = 110f..130f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Swing", color = Color.Gray, fontSize = 12.sp)
            Slider(Modifier.weight(1f).padding(start = 8.dp), value = swing, onValueChange = { swing = it; project.swing = it }, valueRange = 0f..0.4f)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(project.tracks) { t -> TrackRow(t, step) { importTarget = t; picker.launch(arrayOf("audio/*")) } }
        }
    }
}

@Composable
fun TrackRow(t: Track, playStep: Int, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1C)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f).clickable { onImport() }) {
                Text(t.name, color = Color.White, fontSize = 14.sp)
                Text(t.sampleLabel + "  · tap to import", color = Color.Gray, fontSize = 10.sp, maxLines = 1)
            }
            FilterChip(selected = t.mute, onClick = { t.mute = !t.mute }, label = { Text("M") })
            FilterChip(selected = t.solo, onClick = { t.solo = !t.solo }, label = { Text("S") })
        }
        Slider(value = t.volume, onValueChange = { t.volume = it }, valueRange = 0f..1f, modifier = Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until STEPS) {
                val on = t.steps[i]
                Box(
                    Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(3.dp))
                        .background(when { on -> Amber; i % 4 == 0 -> Color(0xFF3A3A3A); else -> Color(0xFF2A2A2A) })
                        .then(if (i == playStep) Modifier.background(Color(0x55FFFFFF)) else Modifier)
                        .clickable { t.steps[i] = !on }
                )
                if (i % 4 == 3 && i < STEPS - 1) Spacer(Modifier.width(3.dp))
            }
        }
    }
}
