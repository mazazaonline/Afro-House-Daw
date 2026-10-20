package com.afrohouse.daw

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
import androidx.compose.foundation.ScrollState

val HEADER_W = 92.dp
val ROW_H = 54.dp
val RULER_H = 26.dp
val ZOOMS = listOf(36.dp, 56.dp, 84.dp)

@Composable
fun PlaylistScreen(project: Project, player: Player, onImport: (Track) -> Unit) {
    var zoom by remember { mutableIntStateOf(1) }
    var follow by remember { mutableStateOf(true) }
    var selTrack by remember { mutableStateOf<Track?>(null) }
    var sel by remember { mutableStateOf<Clip?>(null) }
    var brushPat by remember { mutableIntStateOf(0) }
    var brushLen by remember { mutableIntStateOf(1) }
    var addMenu by remember { mutableStateOf(false) }
    val barW = ZOOMS[zoom]
    val hs = rememberScrollState()
    val vs = rememberScrollState()
    val totalW = barW * TIMELINE_BARS
    val gridH = RULER_H + ROW_H * project.tracks.size

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Loop", color = Color.Gray, fontSize = 12.sp)
            listOf(8, 16, 32).forEach { n -> Pill(n.toString(), project.loopBars == n) { project.loopBars = n } }
            Spacer(Modifier.weight(1f))
            Pill("Follow", follow, Color(0xFF2EC4B6)) { follow = !follow }
            Pill("Zoom", false) { zoom = (zoom + 1) % ZOOMS.size }
        }

        Row(Modifier.weight(1f).verticalScroll(vs)) {
            Column(Modifier.width(HEADER_W)) {
                Spacer(Modifier.height(RULER_H))
                project.tracks.forEachIndexed { i, t ->
                    key(t.id) {
                        TrackHeader(t, i, player, onImport) { dead ->
                            if (selTrack === dead) { selTrack = null; sel = null }
                            project.tracks.remove(dead)
                        }
                    }
                }
                Box {
                    Box(
                        Modifier.width(HEADER_W).height(44.dp).clickable { addMenu = true },
                        contentAlignment = Alignment.Center
                    ) { Text("+ Track", color = Color.Gray, fontSize = 12.sp) }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        SOUNDS.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { addMenu = false; project.addSound(s) })
                        }
                    }
                }
            }
            Box(Modifier.horizontalScroll(hs)) {
                Column(Modifier.width(totalW)) {
                    Ruler(barW, project.loopBars)
                    project.tracks.forEach { t ->
                        key(t.id) {
                            Lane(
                                t, barW, project.loopBars, sel,
                                onPlace = { b ->
                                    val c = t.place(b, brushPat, brushLen)
                                    if (c != null) { sel = c; selTrack = t }
                                },
                                onSelect = { c -> sel = c; selTrack = t }
                            )
                        }
                    }
                }
                Playhead(player, barW, gridH, hs, follow)
            }
        }

        val clip = sel
        val tr = selTrack
        val activePat = clip?.pat ?: brushPat
        val activeLen = clip?.len ?: brushLen
        Column(Modifier.fillMaxWidth().background(SURFACE).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (clip != null && tr != null) "${tr.name} clip  ·  bar ${clip.start + 1}" else "Draw: tap an empty bar to add a clip",
                color = Color.Gray, fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PATTERN_NAMES.forEachIndexed { i, n ->
                    Pill(n, activePat == i, if (tr != null) PALETTE[tr.color % PALETTE.size] else PINK) {
                        brushPat = i
                        if (clip != null) clip.pat = i
                    }
                }
                Spacer(Modifier.weight(1f))
                Pill("−", false) {
                    if (clip != null) clip.len = maxOf(1, clip.len - 1) else brushLen = maxOf(1, brushLen - 1)
                }
                Text("$activeLen bar", color = Color.White, fontSize = 12.sp)
                Pill("+", false) {
                    if (clip != null && tr != null) {
                        if (clip.len + 1 <= tr.maxLenAt(clip.start, clip)) clip.len = clip.len + 1
                    } else brushLen = minOf(16, brushLen + 1)
                }
            }
            if (clip != null && tr != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Duplicate", false) {
                        val n = tr.place(clip.start + clip.len, clip.pat, clip.len)
                        if (n != null) sel = n
                    }
                    Pill("Delete", false, PINK) { tr.clips.remove(clip); sel = null }
                    Pill("Done", false) { sel = null }
                }
            }
        }
    }
}

@Composable
fun TrackHeader(t: Track, index: Int, player: Player, onImport: (Track) -> Unit, onDelete: (Track) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val col = PALETTE[t.color % PALETTE.size]
    Row(Modifier.width(HEADER_W).height(ROW_H).background(SURFACE).border(0.5.dp, Color(0x22FFFFFF))) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(col))
        Column(Modifier.weight(1f).padding(horizontal = 5.dp, vertical = 3.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box {
                Text(t.name, color = Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.clickable { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Import sample") }, onClick = { menu = false; onImport(t) })
                    DropdownMenuItem(text = { Text("Delete track") }, onClick = { menu = false; onDelete(t) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniToggle("M", t.mute, PINK) { t.mute = !t.mute }
                MiniToggle("S", t.solo, Color(0xFFFFD23F)) { t.solo = !t.solo }
            }
            Meter(player, index, col)
        }
    }
}

@Composable
fun Ruler(barW: Dp, loopBars: Int) {
    Row(Modifier.height(RULER_H)) {
        for (b in 0 until TIMELINE_BARS) {
            Box(
                Modifier.width(barW).fillMaxHeight()
                    .background(if (b < loopBars) SURFACE2 else Color(0xFF14102A))
                    .border(0.5.dp, Color(0x22FFFFFF)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("${b + 1}", color = if (b % 4 == 0) Color.White else Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
fun Lane(t: Track, barW: Dp, loopBars: Int, selected: Clip?, onPlace: (Int) -> Unit, onSelect: (Clip) -> Unit) {
    val col = PALETTE[t.color % PALETTE.size]
    Box(Modifier.width(barW * TIMELINE_BARS).height(ROW_H)) {
        Row {
            for (b in 0 until TIMELINE_BARS) {
                Box(
                    Modifier.width(barW).fillMaxHeight()
                        .background(
                            if (b >= loopBars) Color(0xFF100D22)
                            else if (b % 4 == 0) Color(0xFF211C40)
                            else Color(0xFF1A1633)
                        )
                        .border(0.5.dp, Color(0x18FFFFFF))
                        .clickable { onPlace(b) }
                )
            }
        }
        t.clips.forEach { c -> key(c) { ClipView(t, c, col, barW, c === selected, onSelect) } }
    }
}

@Composable
fun ClipView(t: Track, c: Clip, col: Color, barW: Dp, selected: Boolean, onSelect: (Clip) -> Unit) {
    Box(
        Modifier.offset(x = barW * c.start).width(barW * c.len).fillMaxHeight().padding(2.dp)
            .clip(RoundedCornerShape(7.dp)).background(col)
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(7.dp)) else Modifier)
            .clickable { onSelect(c) }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val len = c.len
            val w = size.width / (len * STEPS)
            for (bar in 0 until len) {
                for (s in 0 until STEPS) {
                    if (t.steps[c.pat * STEPS + s]) {
                        drawRect(Color(0x99000000), Offset((bar * STEPS + s) * w, size.height * 0.3f), Size(w * 0.8f, size.height * 0.45f))
                    }
                }
            }
        }
        Text(PATTERN_NAMES[c.pat], color = Color.Black, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, top = 1.dp))
    }
}

@Composable
fun Playhead(player: Player, barW: Dp, height: Dp, hs: ScrollState, follow: Boolean) {
    var bar by remember { mutableIntStateOf(-1) }
    var step by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        while (true) {
            bar = player.currentBar
            step = player.currentStep.coerceAtLeast(0)
            delay(30)
        }
    }
    LaunchedEffect(bar, follow, barW) {
        if (follow && bar >= 0) {
            val target = with(density) { (barW * maxOf(bar - 1, 0)).roundToPx() }
            hs.animateScrollTo(target)
        }
    }
    if (bar >= 0) {
        Box(Modifier.offset(x = barW * (bar + step / 16f)).width(2.dp).height(height).background(Color.White))
    }
}
