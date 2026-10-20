package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.components.ChipRow
import com.myfuturecode.mfcdash.ui.components.MChip
import com.myfuturecode.mfcdash.ui.theme.BrandGradient
import com.myfuturecode.mfcdash.ui.theme.HeroGradient
import com.myfuturecode.mfcdash.util.LANGS
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.t

@Composable
fun LoginScreen(vm: DashVm) {
    var url by remember { mutableStateOf(vm.baseUrl) }
    var user by remember { mutableStateOf(vm.username) }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(HeroGradient).statusBarsPadding().padding(24.dp, 36.dp, 24.dp, 64.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF3EE68A)))
                Spacer(Modifier.width(8.dp))
                Text("LIVE", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelSmall, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp))
                Spacer(Modifier.weight(1f))
                // Language: English (default) / Hinglish
                LANGS.forEach { (code, label) ->
                    TextButton(onClick = { vm.setLang(code) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(label, color = if (Lang.code == code) Color.White else Color.White.copy(alpha = .55f), style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (Lang.code == code) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("MFC", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("Apps Dashboard", color = Color(0xFFE7A6FF), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text("Revenue, ad spend, ROAS, users and crashes, all in one place.".t(), color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
        }
        Column(
            Modifier.padding(horizontal = 16.dp).offset(y = (-40).dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface).padding(22.dp)
        ) {
            Text("Sign in".t(), style = MaterialTheme.typography.titleLarge)
            Text("Same username/password as the website.".t(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(user, { user = it }, label = { Text("Username".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(pass, { pass = it }, label = { Text("Password".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } })
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { advanced = !advanced }, contentPadding = PaddingValues(0.dp)) { Text(if (advanced) "Hide server address".t() else "Change server address".t()) }
            if (advanced) OutlinedTextField(url, { url = it }, label = { Text("Dashboard URL".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("e.g. https://tirangae-online.live/mfc_dash/".t()) })
            if (err != null) { Spacer(Modifier.height(10.dp)); Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (user.isBlank() || pass.isBlank()) { err = "Enter both username and password".t(); return@Button }
                    busy = true; err = null
                    vm.login(url, user.trim(), pass) { e -> busy = false; err = e }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues(0.dp),
            ) {
                Box(Modifier.fillMaxSize().background(BrandGradient, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Sign in".t(), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Login is saved encrypted on the phone; if the session expires the app signs in again by itself.".t(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Language".t(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow { LANGS.forEach { (code, label) -> MChip(label, Lang.code == code, { vm.setLang(code) }) } }
        }
    }
}
