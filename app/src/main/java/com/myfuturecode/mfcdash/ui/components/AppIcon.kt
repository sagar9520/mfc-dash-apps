package com.myfuturecode.mfcdash.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.theme.Mfc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** App ka logo (Play Store icon URL). Load hone tak / na mile to kit ka 2-akshar tile (KAvatar). */
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

@Composable
fun AppIcon(url: String, name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bmp by remember(url) { mutableStateOf(if (url.isBlank()) null else IconCache.mem.get(url)) }
    LaunchedEffect(url) { if (url.isNotBlank() && bmp == null) bmp = IconCache.get(url, px) }
    val b = bmp
    if (b != null) {
        val shape = RoundedCornerShape(size * .3f)
        val c = Mfc.c
        var m = modifier.size(size).clip(shape)
        // dark / brand band me halki ring (KAvatar jaisi)
        if (c.onBrand) m = modifier.size(size).border(1.5.dp, Color.White.copy(alpha = .7f), shape).clip(shape)
        else if (c.isDark) m = m.border(1.dp, Color.White.copy(alpha = .08f), shape)
        Image(b.asImageBitmap(), name, m, contentScale = ContentScale.Crop)
    } else {
        KAvatar(name, modifier, size = size)
    }
}
