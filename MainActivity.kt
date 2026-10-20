package com.afrohouse.daw

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

val BG = Color(0xFF0E0B1F)
val SURFACE = Color(0xFF1A1633)
val SURFACE2 = Color(0xFF2A2548)
val PINK = Color(0xFFFF4D6D)
val ORANGE = Color(0xFFFF9F1C)

class MainActivity : ComponentActivity() {
    private lateinit var project: Project
    private lateinit var player: Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = Store.load(this) ?: Project.afroHouseDemo()
        player = Player(project)
        val scheme = darkColorScheme(primary = PINK, background = BG, surface = SURFACE)
        setContent { MaterialTheme(colorScheme = scheme) { Daw(project, player) } }
    }

    override fun onStop() {
        player.stop()
        Store.save(this, project)
        super.onStop()
    }
}

@Composable
fun Daw(project: Project, player: Player) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var screen by remember { mutableIntStateOf(0) }
    var importTarget by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(Unit) { while (true) { playing = player.isPlaying; delay(100) } }
    LaunchedEffect(screen) { project.patternMode = (screen == 1) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val t = importTarget ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) { SampleLoader.load(ctx, uri) }
                t.sample = data
                t.sampleFile = null
                t.sampleLabel = uri.lastPathSegment?.substringAfterLast('/') ?: "imported"
            } catch (e: Exception) {
                Toast.makeText(ctx, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bars = project.loopBars
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)!!.use { Exporter.renderWav(project, bars, true, it) }
                }
                Toast.makeText(ctx, "Exported $bars bars as WAV", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val onImport: (Track) -> Unit = { t -> importTarget = t; picker.launch(arrayOf("audio/*")) }

    Column(Modifier.fillMaxSize().background(BG).statusBarsPadding()) {
        Transport(project, player, playing) { saver.launch("afro_house_song.wav") }
        Box(Modifier.weight(1f)) {
            when (screen) {
                0 -> PlaylistScreen(project, player, onImport)
                1 -> PatternScreen(project, player, onImport)
                else -> MixerScreen(project, player)
            }
        }
        NavigationBar(containerColor = SURFACE) {
            listOf("Playlist" to "▤", "Pattern" to "▦", "Mixer" to "☰").forEachIndexed { i, (name, glyph) ->
                NavigationBarItem(
                    selected = screen == i,
                    onClick = { screen = i },
                    icon = { Text(glyph, fontSize = 18.sp) },
                    label = { Text(name) }
                )
            }
        }
    }
}

@Composable
fun Transport(project: Project, player: Player, playing: Boolean, onExport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(50.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(PINK, ORANGE)))
                .clickable { if (player.isPlaying) player.stop() else player.start() },
            contentAlignment = Alignment.Center
        ) { Text(if (playing) "■" else "▶", color = Color.White, fontSize = 20.sp) }

        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(SURFACE2), verticalAlignment = Alignment.CenterVertically) {
            Text("−", color = Color.White, fontSize = 22.sp,
                modifier = Modifier.clickable { project.bpm = (project.bpm - 1f).coerceAtLeast(90f) }.padding(horizontal = 12.dp, vertical = 6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${project.bpm.toInt()}", color = Color.White, fontSize = 18.sp)
                Text("BPM", color = Color.Gray, fontSize = 9.sp)
            }
            Text("+", color = Color.White, fontSize = 22.sp,
                modifier = Modifier.clickable { project.bpm = (project.bpm + 1f).coerceAtMost(140f) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        PositionReadout(player)
        Spacer(Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(SURFACE2).clickable { onExport() }.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("Export", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
fun PositionReadout(player: Player) {
    var txt by remember { mutableStateOf("1.1") }
    LaunchedEffect(Unit) {
        while (true) {
            val b = player.currentBar
            val s = player.currentStep
            txt = if (b < 0 || s < 0) "1.1" else "${b + 1}.${s / 4 + 1}"
            delay(50)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(txt, color = Color.White, fontSize = 18.sp)
        Text("BAR", color = Color.Gray, fontSize = 9.sp)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, color: Color = PINK, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) color else SURFACE2)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) Color.Black else Color.White, fontSize = 12.sp) }
}

@Composable
fun MiniToggle(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (on) color else SURFACE2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (on) Color.Black else Color.White, fontSize = 10.sp) }
}

@Composable
fun Meter(player: Player, index: Int, color: Color, modifier: Modifier = Modifier) {
    var lv by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(index) { while (true) { lv = player.peak(index).coerceIn(0f, 1f); delay(50) } }
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x22FFFFFF))) {
        Box(Modifier.fillMaxWidth(lv).fillMaxHeight().background(color))
    }
}
