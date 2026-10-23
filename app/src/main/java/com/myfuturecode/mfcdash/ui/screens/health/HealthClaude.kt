package com.myfuturecode.mfcdash.ui.screens.health

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.myfuturecode.mfcdash.data.HCxApp
import com.myfuturecode.mfcdash.ui.HState
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.components.AppIcon
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KDivider
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelBlock
import com.myfuturecode.mfcdash.ui.kit.KSkelCircle
import com.myfuturecode.mfcdash.ui.kit.KSkelLine
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.kStyle
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import java.io.File

// "Export for Claude": Issues tab ka "For Claude" -> ye sheet (apps + filters) -> server .md banata hai -> Copy / Share file.
// Issue page ke stack trace par "Copy" (ek issue) bhi yahin se.

private val CX_DAYS = listOf(7, 28, 90)
private val CX_TOP = listOf(5, 10, 0)   // 0 = har app ke sab

/**
 * Isse lambi file (akshar) clipboard me nahi: clipboard ka text binder se UTF-16 (2 byte / akshar) jaata hai, had ~1 MB sab ke saath
 * baanti hui (keyboard ki clipboard history / Android 13+ ka overlay bhi dobara padhte hain). 150k akshar ~300 KB: baaki seedha file share.
 */
private const val CX_COPY_MAX = 150_000

/** "4 crashes · 1 ANR" (sirf jo hain). */
private fun cxCounts(c: Int, a: Int): String = listOfNotNull(
    if (c > 0 || a == 0) (if (c == 1) "1 crash".t() else tf("%s crashes", hN(c))) else null,
    if (a > 0) (if (a == 1) "1 ANR".t() else tf("%s ANRs", hN(a))) else null,
).joinToString(" · ")

/** Text clipboard par (koi message nahi). false = copy nahi hua (bada text / OEM ki rok). */
private fun hClip(clip: ClipboardManager, text: String): Boolean = runCatching { clip.setText(AnnotatedString(text)) }.isSuccess

/** Android 13+ khud "Copied" dikhata hai: apna snackbar sirf uske neeche. */
private val hSysCopyToast = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Issue page: text clipboard par + snackbar. false = copy nahi hua. (Sheet apni galti khud dikhati hai: snackbar sheet ke peeche chhupta.) */
internal fun hCopy(clip: ClipboardManager, text: String, vm: HealthVm): Boolean {
    val ok = hClip(clip, text)
    if (!ok) vm.tell("Copy failed".t())
    else if (!hSysCopyToast) vm.tell("Copied".t())
    return ok
}

/**
 * crashes_for_claude.md -> cache/share -> share chooser (wahi FileProvider: packageName + ".files", jaise GadsBits ka shareFile).
 * shareFile fail par poora text EXTRA_TEXT me bhejta: 1.5 MB binder me nahi jaata, chup-chaap kuch nahi hota. Yahan false = sheet me galti.
 */
private fun cxShare(ctx: Context, fileName: String, text: String, subject: String): Boolean = runCatching {
    // purani share files (unme token tha) hatao: sirf aakhri file rahe
    val dir = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val f = File(dir, fileName).apply { writeText(text, Charsets.UTF_8) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, subject)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(fileName, uri)   // naye Android par read permission isi se milti hai
    ctx.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

/** Ek line text ki chaudai (jaise dikhega). */
@Composable
internal fun hTextW(text: String, style: TextStyle): Dp {
    val tm = rememberTextMeasurer()
    return with(LocalDensity.current) { tm.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() }
}

/** KButton ki poori chaudai: 20 + icon 18 + 8 + text (15sp bold) + 20. Line me jagah na ho (chhota phone / Hinglish / bada font) to buttons neeche. */
@Composable
internal fun hBtnW(text: String): Dp = hTextW(text, kStyle(15.sp, FontWeight.Bold)) + 66.dp

/** Section ke daayein "Copy" (brand halka pill, 48dp touch): issue page ka stack trace. */
@Composable
internal fun HCopyLink(onClick: () -> Unit) {
    val c = Mfc.c
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.brandSoft).padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(KIcons.Copy, null, tint = c.brand, modifier = Modifier.size(18.dp))
            Text("Copy".t(), color = c.brand, style = hStyle(15.sp, FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/** Toggle chip (mockup): on = brand halka + check, off = sirf line. cycle = har tap agla option (period / top), hamesha on. */
@Composable
private fun CxChip(text: String, on: Boolean, cycle: Boolean = false, onClick: () -> Unit) {
    val c = Mfc.c
    val shape = RoundedCornerShape(18.dp)
    val tap = if (cycle) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier.toggleable(value = on, role = Role.Switch, onValueChange = { onClick() })
    Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).then(tap), contentAlignment = Alignment.Center) {
        var pill = Modifier.height(36.dp).clip(shape).background(if (on) c.brandSoft else Color.Transparent)
        if (!on) pill = pill.border(1.5.dp, c.line, shape)
        Row(
            pill.padding(start = if (on) 10.dp else 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (on) Icon(KIcons.Check, null, tint = c.brand, modifier = Modifier.size(16.dp))
            Text(text, color = if (on) c.brand else c.ink2, style = hStyle(14.sp, FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/** Checkbox (24dp, gol kinare): on = brand + check, off = grey ring. */
@Composable
private fun CxCheck(on: Boolean) {
    val c = Mfc.c
    val shape = RoundedCornerShape(7.dp)
    var m = Modifier.size(24.dp).clip(shape).background(if (on) c.btnBg else Color.Transparent)
    if (!on) m = m.border(2.dp, c.swOff, shape)
    Box(m, contentAlignment = Alignment.Center) { if (on) Icon(KIcons.Check, null, tint = c.btnFg, modifier = Modifier.size(16.dp)) }
}

/** Ek app: logo + naam + "4 crashes · 1 ANR" (jo file me jaayenge: top N, summary isi ka jod) + checkbox (poori line tap). */
@Composable
private fun CxAppRow(a: HCxApp, on: Boolean, crash: Boolean, anr: Boolean, onToggle: () -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() }).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(a.icon, a.name, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                a.name, color = if (on) c.ink else c.ink2, style = hStyle(16.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, 21.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                cxCounts(if (crash) a.crashesTop else 0, if (anr) a.anrsTop else 0), Modifier.padding(top = 1.dp), color = c.ink2,
                style = hStyle(14.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        CxCheck(on)
    }
}

/** List aane tak: 3 app lines (logo + 2 lines + checkbox). */
@Composable
private fun CxSkel() {
    KSkeleton(Modifier.padding(top = 4.dp)) {
        repeat(3) { i ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KSkelCircle(40.dp)
                Column(Modifier.weight(1f)) {
                    KSkelLine(listOf(.62f, .48f, .55f)[i], 14.dp)
                    KSkelLine(.34f, 11.dp, Modifier.padding(top = 7.dp))
                }
                KSkelBlock(24.dp, RoundedCornerShape(7.dp), Modifier.width(24.dp))
            }
        }
    }
}

/**
 * "Export for Claude" sheet: All apps | Pick apps, app lines (server ki ginti, sirf jin me issue hai), chips (Crashes, ANRs, Open only,
 * period 7/28/90, top 5/10/sab), summary, Copy (clipboard; bahut badi file = share) aur Share file (crashes_for_claude.md, FileProvider).
 * prePkg = Issues tab me chuna app (sheet "Pick apps" par us app ke saath khulti hai).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeSheet(h: HState, vm: HealthVm, prePkg: String, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        KPageTokens { ClaudeSheetBody(h, vm, prePkg, onClose) }
    }
}

/** Sheet ke andar ka hissa (chips / list / buttons ka state yahin, sheet khuli rahe tab tak). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClaudeSheetBody(h: HState, vm: HealthVm, prePkg: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var pick by remember { mutableStateOf(prePkg.isNotBlank()) }
    var picked by remember { mutableStateOf(if (prePkg.isBlank()) emptySet() else setOf(prePkg)) }
    var crash by remember { mutableStateOf(true) }
    var anr by remember { mutableStateOf(true) }
    var openOnly by remember { mutableStateOf(true) }
    var days by remember { mutableIntStateOf(28) }
    var top by remember { mutableIntStateOf(5) }
    var doing by remember { mutableStateOf("") }                 // "copy" / "share": spinner kis button par
    var note by remember { mutableStateOf<Pair<String, KKind>?>(null) }   // "bahut bada tha: file share hui" / share fail (sheet me: snackbar sheet ke peeche chhupta)
    val types = listOfNotNull("crash".takeIf { crash }, "anr".takeIf { anr }).joinToString(",")
    val f = mapOf("types" to types, "status" to if (openOnly) "open" else "all", "days" to days.toString(), "top" to top.toString())
    val key = f.toSortedMap().toString()
    LaunchedEffect(key) { if (types.isNotEmpty()) vm.loadClaudeApps(f) }

    val data = h.cx
    val apps = data?.apps.orEmpty()
    val fresh = data != null && h.cxKey == key                                  // dikh rahi ginti isi filter ki
    val failed = h.cxErrKey == key && (h.cxOld || h.cxErr != null)              // isi filter ki ginti fail: spinner band, "Try again"
    // naye filter ki ginti aa rahi (ya pehli baar)
    val loading = types.isNotEmpty() && !fresh && !failed
    val chosen = if (pick) apps.filter { it.pkg in picked } else apps
    // type chip turant (server ka jawab aane se pehle bhi sahi ginti)
    val nCrash = if (crash) chosen.sumOf { it.crashesTop } else 0
    val nAnr = if (anr) chosen.sumOf { it.anrsTop } else 0
    val nApps = chosen.count { (crash && it.crashesTop > 0) || (anr && it.anrsTop > 0) }
    val busy = "claude" in h.busy
    // purane filter ki ginti par Copy / Share nahi (file naye filter ki banti, ginti alag hoti)
    val ready = fresh && types.isNotEmpty() && nApps > 0 && nCrash + nAnr > 0
    // sab chune = "sab apps" (lamba URL nahi)
    val pkgs = if (!pick || chosen.size == apps.size) "" else chosen.joinToString(",") { it.pkg }

    fun go(what: String) {
        if (!ready || busy) return
        doing = what
        note = null
        vm.claudeExport(f + ("pkgs" to pkgs)) { r ->
            if (what == "copy") {
                if (r.md.length <= CX_COPY_MAX && hClip(clip, r.md)) {
                    if (!hSysCopyToast) vm.tell("Copied".t())   // sheet band hone ke baad dikhta hai
                    onClose()
                    return@claudeExport
                }
                // bahut bada / copy fail: file share (sheet khuli, note yahin)
                note = (if (r.md.length > CX_COPY_MAX) tf("Too big to copy (%s), so it is shared as a file", bytes(r.bytes))
                    else "Could not copy, so it is shared as a file".t()) to KKind.ASK
            }
            if (!cxShare(ctx, r.fileName, r.md, "Crashes for Claude".t())) note = "Could not share the file. Free some space on the phone and try again.".t() to KKind.BAD
        }
    }

    val c = Mfc.c
    // chhoti screen (landscape): sab ek saath scroll; warna sirf app list scroll, chips + buttons neeche tike
    val short = LocalConfiguration.current.screenHeightDp < 560
    Column(Modifier.fillMaxWidth().then(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KIconTile(KIcons.Spark, KKind.ASK)
            Text(
                "Export for Claude".t(), Modifier.weight(1f), color = c.ink, style = hStyle(20.sp, FontWeight.Bold, 26.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.offset(x = 10.dp)) { KBarIcon(KIcons.Close, "Close".t(), onClose) }
        }
        KSegTabs(listOf("All apps".t(), "Pick apps".t()), if (pick) 1 else 0, { pick = it == 1 }, Modifier.padding(top = 10.dp), track = c.well)

        Column((if (short) Modifier else Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())).padding(top = 6.dp)) {
            when {
                data == null && (h.cxOld || h.cxErr != null) -> Unit   // galti neeche, buttons ke upar (purane filter ki list ho to dikhti rahe)
                data == null -> CxSkel()
                apps.isEmpty() -> KEmpty("No crash or ANR in this period".t(), KIcons.Ok)
                else -> apps.forEachIndexed { i, a ->
                    if (i > 0) KDivider(inset = 52.dp)
                    CxAppRow(a, !pick || a.pkg in picked, crash, anr) {
                        // "All apps" me tick hatana = baaki sab chune (Pick apps)
                        if (!pick) { pick = true; picked = apps.map { it.pkg }.toSet() - a.pkg }
                        else picked = if (a.pkg in picked) picked - a.pkg else picked + a.pkg
                    }
                }
            }
        }

        KDivider()
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CxChip("Crashes".t(), crash) { crash = !crash }
            CxChip("ANRs".t(), anr) { anr = !anr }
            CxChip("Open only".t(), openOnly) { openOnly = !openOnly }
            CxChip(tf("Last %s days", days), true, cycle = true) { days = CX_DAYS[(CX_DAYS.indexOf(days) + 1) % CX_DAYS.size] }
            CxChip(if (top > 0) tf("Top %s per app", top) else "All issues per app".t(), true, cycle = true) {
                top = CX_TOP[(CX_TOP.indexOf(top) + 1) % CX_TOP.size]
            }
        }

        // purana server / galti / "file share hui"
        if (h.cxOld) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KIconTile(KIcons.Dhyan, KKind.WARN)
                Column(Modifier.weight(1f)) {
                    Text("Not on server".t(), color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp), maxLines = 1)
                    Text(
                        "Upload the new lib/health.php and api/health.php to mfc_dash, then tap Try again.".t(), Modifier.padding(top = 2.dp),
                        color = c.ink2, style = hStyle(13.sp, lh = 18.sp),
                    )
                }
            }
        } else h.cxErr?.let { HNote(it, KKind.BAD, maxLines = 4) }
        if (h.cxOld || failed || (h.cxErr != null && data == null)) {
            KButton("Try again".t(), { if (types.isNotEmpty()) vm.loadClaudeApps(f) }, Modifier.fillMaxWidth().padding(top = 8.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
        }
        note?.let { (t, k) -> HNote(t, k, maxLines = 4) }

        // summary: "2 apps · 7 crashes · 3 ANRs" (jo file me jaayega). Pehli ginti aa rahi = "Counting…"; galti (list nahi) = summary nahi
        if (fresh || loading || types.isEmpty()) Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(top = 10.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Description, null, tint = c.brand, modifier = Modifier.size(18.dp))
            val sum = when {
                types.isEmpty() -> AnnotatedString("Pick crashes or ANRs".t())
                data == null -> AnnotatedString("Counting issues…".t())
                pick && picked.none { p -> apps.any { it.pkg == p } } && data != null -> AnnotatedString("Pick at least one app".t())
                else -> buildAnnotatedString {
                    val parts = listOfNotNull(
                        nApps to (if (nApps == 1) "app" else "apps"),
                        (nCrash to (if (nCrash == 1) "crash" else "crashes")).takeIf { crash },
                        (nAnr to (if (nAnr == 1) "ANR" else "ANRs")).takeIf { anr },
                    )
                    parts.forEachIndexed { i, (n, w) ->
                        if (i > 0) append(" · ")
                        withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold)) { append(hN(n)) }
                        append(" " + w.t())
                    }
                }
            }
            // aa rahi ginti tak purani halki (buttons band)
            Text(sum, Modifier.weight(1f).alpha(if (fresh || data == null) 1f else .5f), color = c.ink2, style = hStyle(15.sp, lh = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = c.brand, strokeWidth = 2.dp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            val copy = @Composable { m: Modifier ->
                KButton("Copy".t(), { go("copy") }, m, kind = KBtn.SOFT, icon = KIcons.Copy, enabled = ready && !(busy && doing != "copy"), busy = busy && doing == "copy")
            }
            val share = @Composable { m: Modifier ->
                KButton("Share file".t(), { go("share") }, m, icon = Icons.Outlined.Share, enabled = ready && !(busy && doing != "share"), busy = busy && doing == "share")
            }
            // 1 : 1.5 me dono label poore aate hain? Nahi (chhota phone / Hinglish / bada font) = upar-neeche, poori chaudai
            val each = (maxWidth - 8.dp) / 2.5f
            if (hBtnW("Copy".t()) <= each && hBtnW("Share file".t()) <= each * 1.5f) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { copy(Modifier.weight(1f)); share(Modifier.weight(1.5f)) }
            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { share(Modifier.fillMaxWidth()); copy(Modifier.fillMaxWidth()) }
        }
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}
