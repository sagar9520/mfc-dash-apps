package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.cpFmtName
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

/** api/crosspromo.php: kis app mein kis app ka promo, ad type, views/clicks/installs, design-wise CTR. */
@Composable
fun CrossPromoScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    TabScreen("Cross promo", vm, st, nav, back = true) { d ->
        val ex = MaterialTheme.extra
        val cp = st.cp
        val rows = cp?.rows ?: emptyList()
        val v = rows.sumOf { it.views }; val c = rows.sumOf { it.clicks }; val i = rows.sumOf { it.installs }; val rw = rows.sumOf { it.rewards }
        TileGrid(listOf(
            Triple("Views", num(v), null), Triple("Clicks", num(c), null), Triple("CTR", pct(if (v > 0) c / v else null), null),
            Triple("Installs", num(i), "1 phone = 1"), Triple("Install / click", pct(if (c > 0) i / c else null), null), Triple("Rewards", num(rw), "rewarded"),
        ), colors = mapOf(3 to ex.good))

        SectionTitle("Ad type wise")
        Panel {
            val g = rows.groupBy { it.fmt }.map { (f, l) -> Triple(cpFmtName(f), l.sumOf { it.views }, l.sumOf { it.installs }) }.sortedByDescending { it.second }
            Table(listOf("Ad type", "Views", "Installs", "Inst / 1000 views"), g.map { listOf(it.first, num(it.second), num(it.third), num(if (it.second > 0) it.third / it.second * 1000 else null, 1)) }, empty = "Is period mein promo nahi dikha")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Kis app mein kis app ka promo")
        Panel {
            Table(listOf("Shown in", "Promoted", "Type", "Views", "Clicks", "CTR", "Installs", "Inst/click"),
                rows.sortedByDescending { it.views }.map { listOf(d.appName(it.src), d.appName(it.tgt), cpFmtName(it.fmt), num(it.views), num(it.clicks), pct(it.ctr), num(it.installs), pct(it.installPerClick)) },
                empty = "Is period mein cross promo nahi dikha")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Promoted app wise", "kisko kitne installs mile")
        Panel {
            val g = rows.groupBy { it.tgt }.map { (t, l) -> Triple(d.appName(t), l.sumOf { it.installs }, l.sumOf { it.clicks }) }.sortedByDescending { it.second }
            val tot = g.sumOf { it.second }
            HBars(g.map { Triple(it.first, num(it.second) + " installs · " + num(it.third) + " clicks", shareOf(it.second, tot)) }, color = ex.good)
            if (g.isEmpty()) Empty("Data nahi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Design wise", "kaunsa mockup zyada click laata hai")
        Panel {
            val ds = cp?.designs ?: emptyList()
            Table(listOf("Shown in", "Design", "Type", "Views", "Clicks", "CTR"),
                ds.sortedByDescending { it.ctr ?: 0.0 }.map { listOf(d.appName(it.src), it.design.ifBlank { "–" }, cpFmtName(it.fmt), num(it.views), num(it.clicks), pct(it.ctr)) }, empty = "Abhi design data nahi")
        }
        Spacer(Modifier.height(8.dp))
        Text("Install tabhi ginta hai jab promote hua app bhi dashboard se juda ho (tracker_url/tracker_key) aur naye tracker wala version ho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
