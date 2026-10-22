package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.myfuturecode.mfcdash.BuildConfig
import com.myfuturecode.mfcdash.R
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KNum
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.LANGS
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.t

/** Login: upar logo + MFC (brand me gradient band), neeche card me username / password, server address (chhupa). */
@Composable
fun LoginScreen(vm: DashVm) {
    var url by remember { mutableStateOf(vm.baseUrl) }
    var user by remember { mutableStateOf(vm.username) }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        if (user.isBlank() || pass.isBlank()) {
            err = "Enter both username and password".t()
            return
        }
        busy = true
        err = null
        vm.login(url, user.trim(), pass) { e -> busy = false; err = e }
    }

    val field = RoundedCornerShape(14.dp)
    // Root ka Scaffold yahan nahi: status bar ki jagah khud (brand me upar ki patti KStatusBarBand se)
    Box(Modifier.fillMaxSize().background(Mfc.c.page)) {
        KScreen(
            modifier = Modifier.statusBarsPadding().imePadding(),
            top = { LgTop(vm) },
        ) {
            val c = Mfc.c
            KCard(Modifier.widthIn(max = 480.dp).align(Alignment.CenterHorizontally)) {
                LgText("Sign in".t(), 20.sp, c.ink, weight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    user, { user = it }, label = { Text("Username".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(KIcons.User, null) }, shape = field,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Password".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(KIcons.Lock, null) }, shape = field,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Hide".t() else "Show".t())
                        }
                    },
                )
                LgLink(if (advanced) "Hide server address".t() else "Change server address".t()) { advanced = !advanced }
                if (advanced) OutlinedTextField(
                    url, { url = it }, label = { Text("Dashboard URL".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(BuildConfig.DEFAULT_BASE_URL, maxLines = 1, overflow = TextOverflow.Ellipsis) }, shape = field,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                )
                err?.let { LgText(it, 14.sp, c.red, Modifier.padding(top = 10.dp), maxLines = 4) }
                KButton("Sign in".t(), { submit() }, Modifier.fillMaxWidth().padding(top = 16.dp), busy = busy)
            }
        }
    }
}

/** Upar: bhasha (English / Hinglish), logo, MFC. */
@Composable
private fun LgTop(vm: DashVm) {
    val c = Mfc.c
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        LANGS.forEach { (code, label) -> LgLang(label, Lang.code == code) { vm.setLang(code) } }
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp)) {
        val shape = RoundedCornerShape(24)
        var logo = Modifier.size(64.dp)
        logo = when {
            c.onBrand -> logo.shadow(6.dp, shape).border(1.5.dp, Color.White.copy(alpha = .7f), shape)
            c.isDark -> logo.border(1.dp, Color.White.copy(alpha = .12f), shape)
            else -> logo
        }
        Image(painterResource(R.drawable.mfc_mark), contentDescription = "MFC", modifier = logo.clip(shape))
        Spacer(Modifier.height(14.dp))
        LgText("MFC", 32.sp, c.ink, weight = FontWeight.Bold)
        LgText("Apps Dashboard", 17.sp, c.ink2)
    }
}

@Composable
private fun LgLang(label: String, on: Boolean, onClick: () -> Unit) {
    val c = Mfc.c
    val shape = RoundedCornerShape(24.dp)
    val pill = Modifier.height(36.dp).clip(shape)
    Box(
        Modifier.heightIn(min = 48.dp).clip(shape).selectable(selected = on, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box((if (on) pill.background(c.pNBg) else pill).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            LgText(label, 15.sp, if (on) c.ink else c.ink2, weight = if (on) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/** "Server address badlo" (brand rang, 48dp). */
@Composable
private fun LgLink(text: String, onClick: () -> Unit) {
    val c = Mfc.c
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) { LgText(text, 14.sp, c.brand, weight = FontWeight.SemiBold) }
}

@Composable
private fun LgText(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal, maxLines: Int = 1,
) {
    Text(
        text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = KNum.copy(fontSize = size, fontWeight = weight, lineHeight = (size.value * 1.3f).sp, letterSpacing = (-0.01).em),
    )
}
