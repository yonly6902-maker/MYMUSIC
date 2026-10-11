package com.example.velvetmusic

import android.Manifest
import android.content.ContentUris
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Song(val id: Long, val title: String, val artist: String, val duration: Long, val uri: Uri) {
    fun item() = MediaItem.Builder().setMediaId(id.toString()).setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build()
}
private val bg = Color(0xFF0B0D0C)
private val green = Color(0xFF1ED760)
private val muted = Color(0xFF9BA49D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { PlayerScreen() } }
}

@Composable
private fun PlayerScreen() {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var message by remember { mutableStateOf("Your music, beautifully simple.") }
    var search by remember { mutableStateOf("") }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var current by remember { mutableIntStateOf(-1) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }

    fun scan(): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val result = mutableListOf<Song>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) result += Song(c.getLong(id), c.getString(title) ?: "Unknown title", c.getString(artist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist", c.getLong(duration), ContentUris.withAppendedId(collection, c.getLong(id)))
        }
        return result
    }
    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        MediaController.Builder(context, token).buildAsync().also { future ->
            future.addListener({ try { controller = future.get() } catch (_: Exception) { message = "Could not start player. Please reopen the app." } }, ContextCompat.getMainExecutor(context))
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        if (ok) { songs = scan(); message = "${songs.size} offline tracks found"; connect() } else message = "Music access is needed to scan local songs."
    }
    LaunchedEffect(Unit) { if (allowed) { songs = scan(); message = "${songs.size} offline tracks found"; connect() } }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { current = controller?.currentMediaItemIndex ?: -1 }
        }
        controller?.addListener(listener); onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller) { while (true) { controller?.let { playing = it.isPlaying; current = it.currentMediaItemIndex; pos = it.currentPosition.coerceAtLeast(0); dur = it.duration.coerceAtLeast(0) }; delay(500) } }
    val filtered = songs.filter { it.title.contains(search, true) || it.artist.contains(search, true) }
    val active = songs.getOrNull(current)

    MaterialTheme(colorScheme = darkColorScheme(primary = green, background = bg, surface = Color(0xFF171B18), onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            Column {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF19442D), bg))).padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(green), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = bg, modifier = Modifier.size(28.dp)) }
                        Spacer(Modifier.width(12.dp)); Column { Text("VELVET", fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("OFFLINE MUSIC", fontSize = 10.sp, color = green, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.weight(1f)); IconButton(onClick = { if (allowed) { songs = scan(); message = "${songs.size} offline tracks found" } else permissionLauncher.launch(permission) }) { Icon(Icons.Default.Refresh, "Rescan") }
                    }
                    Spacer(Modifier.height(24.dp)); Text("Your music.", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold); Text("No streaming. Just your songs.", color = muted)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search songs or artists", color = muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = muted) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = green, unfocusedBorderColor = Color(0xFF3B453E), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = green))
                    Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LibraryMusic, null, tint = green, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text(message, color = muted, fontSize = 12.sp) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("YOUR LIBRARY", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp); Spacer(Modifier.weight(1f)); Text("${filtered.size} SONGS", color = muted, fontSize = 11.sp) }
                if (!allowed) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.FolderOpen, null, tint = green, modifier = Modifier.size(50.dp)); Spacer(Modifier.height(12.dp)); Text("Your songs stay on your phone", fontWeight = FontWeight.Bold); Text("Allow access to scan downloaded audio.", color = muted); Spacer(Modifier.height(16.dp)); Button(onClick = { permissionLauncher.launch(permission) }, colors = ButtonDefaults.buttonColors(containerColor = green)) { Text("Scan my music", color = bg) } }
                } else if (filtered.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.MusicOff, null, tint = muted, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(12.dp)); Text("No songs found", fontWeight = FontWeight.Bold); Text("Add audio files to Music or Download, then tap refresh.", color = muted) }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        items(filtered, key = { it.id }) { song ->
                            val index = songs.indexOfFirst { it.id == song.id }; val selected = active?.id == song.id
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0xFF1D3024) else Color.Transparent).clickable {
                                val c = controller
                                if (c != null) { c.setMediaItems(songs.map { it.item() }, index, 0L); c.prepare(); c.play() } else { connect(); message = "Player starting — tap the song again." }
                            }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) green else Color(0xFF29352D)), contentAlignment = Alignment.Center) { Icon(if (selected && playing) Icons.Default.Equalizer else Icons.Default.MusicNote, null, tint = if (selected) bg else green) }
                                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(song.title, color = if (selected) green else Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Text(time(song.duration), color = muted, fontSize = 11.sp); IconButton(onClick = { controller?.let { it.setMediaItems(songs.map { s -> s.item() }, index, 0L); it.prepare(); it.play() } ?: connect() }) { Icon(Icons.Default.PlayArrow, "Play ${song.title}", tint = green) }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().background(Color(0xFF121613)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                    if (active != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(active.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(active.artist, color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous") }
                            IconButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }, modifier = Modifier.size(46.dp).clip(CircleShape).background(green)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", tint = bg, modifier = Modifier.size(28.dp)) }
                            IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next") }
                        }
                        Slider(value = if (dur > 0) pos.coerceIn(0, dur).toFloat() / dur else 0f, onValueChange = { controller?.seekTo((dur * it).toLong()) }, colors = SliderDefaults.colors(thumbColor = green, activeTrackColor = green, inactiveTrackColor = Color(0xFF3B453E)), modifier = Modifier.height(26.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(time(pos), color = muted, fontSize = 10.sp); Text(time(dur), color = muted, fontSize = 10.sp) }
                    } else Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Headphones, null, tint = green); Spacer(Modifier.width(10.dp)); Text("Pick a song to start listening", color = muted, fontSize = 13.sp) }
                }
            }
        }
    }
}
private fun time(ms: Long): String { val s = ms.coerceAtLeast(0); return String.format(Locale.getDefault(), "%d:%02d", TimeUnit.MILLISECONDS.toMinutes(s), TimeUnit.MILLISECONDS.toSeconds(s) % 60) }
