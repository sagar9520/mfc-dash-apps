package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.ui.theme.parseHex
import com.myfuturecode.mfcdash.util.*

@Composable
fun LiveScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    TabScreen("Live", vm, st, nav, showRange = false) { d ->
        val ex = MaterialTheme.extra
        val r = d.r
        GradientHeader("LIVE USERS NOW", "har 20 sec refresh") {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(num(d.liveTotal), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(num(d.live30) + " in 30 min", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(d.liveCountries.sortedByDescending { it.n }.take(5).joinToString(" · ") { it.cc + " " + num(it.n) }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .75f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("App-wise live")
        Panel {
            val tot = d.liveTotal
            val rows = d.apps.map { it to (d.liveByApp[it.pkg] ?: 0.0) }.sortedByDescending { it.second }
            rows.forEach { (a, n) -> HBars(listOf(Triple(a.name, num(n), shareOf(n, tot))), color = parseHex(a.color)) }
            if (rows.isEmpty()) Empty("App list nahi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Live feed", "latest impressions (30 min)")
        Panel {
            Table(listOf("Time", "App", "Network", "Format", "CC", "₹"),
                d.recent.take(60).map { listOf(tOnly(it.ts), d.appName(it.pkg), it.net, it.fmt, it.cc, num(it.revUsd * r, 3)) },
                empty = if (d.today) "30 min mein koi impression nahi" else "Live feed sirf aaj ke period mein")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("New installs", "last 6 ghante")
        Panel {
            Table(listOf("Time", "App", "CC", "Source", "Phone"),
                d.recentInstalls.take(60).map { listOf(tOnly(it.firstSeen), d.appName(it.pkg), it.cc, it.src.ifBlank { "–" }, it.model) },
                empty = if (d.today) "6 ghante mein naya install nahi" else "Sirf aaj ke period mein")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Crashes", "is period mein")
        Panel {
            Table(listOf("App", "Ver", "Error", "Where", "Count"),
                d.crashes.sortedByDescending { it.n }.map { listOf(d.appName(it.pkg), it.ver, it.error, it.where, num(it.n)) }, empty = "Koi crash nahi 🎉")
        }
        Spacer(Modifier.height(8.dp))
        Text("Period badalne ke liye Home/Paisa tab ke chips use karo. Live feed hamesha aaj ka hai.", style = MaterialTheme.typography.bodySmall, color = ex.muted)
    }
}
