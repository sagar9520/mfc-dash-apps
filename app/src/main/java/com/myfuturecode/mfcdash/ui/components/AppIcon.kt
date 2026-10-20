package com.myfuturecode.mfcdash.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** App ka logo (Play Store icon URL). Load hone tak / na mile to naam ka pehla akshar rangeen dibbe me. */
private object IconCache {
    val mem = LruCache<String, Bitmap>(80)
    val failed = HashSet<String>()
    val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    suspend fun get(url: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        mem.get(url)?.let { return@withContext it }
        if (url in failed) return@withContext null
        // Play icons: "=s512" jaisa suffix hota hai; chhota version mangwao
        val u = if (url.contains("googleusercontent.com") && !url.contains("=")) "$url=s${px.coerceIn(48, 256)}" else url
        val bmp = runCatching {
            client.newCall(Request.Builder().url(u).build()).execute().use { r -> if (r.isSuccessful) r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } else null }
        }.getOrNull()
        if (bmp != null) mem.put(url, bmp) else failed.add(url)
        bmp
    }
}

private val AVATAR = listOf(Color(0xFF4A1FC4), Color(0xFF9C02BA), Color(0xFF0B1F9E), Color(0xFF12B76A), Color(0xFFF59E0B), Color(0xFF2563EB), Color(0xFFE11D48))

@Composable
fun AppIcon(url: String, name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    var bmp by remember(url) { mutableStateOf(if (url.isBlank()) null else IconCache.mem.get(url)) }
    LaunchedEffect(url) { if (url.isNotBlank() && bmp == null) bmp = IconCache.get(url, px) }
    val shape = RoundedCornerShape(size * 0.24f)
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), name, modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        val c = AVATAR[(name.hashCode() and 0x7fffffff) % AVATAR.size]
        Box(modifier.size(size).clip(shape).background(c), contentAlignment = Alignment.Center) {
            Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}
