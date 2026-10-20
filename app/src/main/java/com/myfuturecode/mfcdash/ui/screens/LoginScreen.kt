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
import com.myfuturecode.mfcdash.ui.theme.BrandGradient
import com.myfuturecode.mfcdash.ui.theme.HeroGradient

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
            }
            Spacer(Modifier.height(18.dp))
            Text("MFC", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("Apps Dashboard", color = Color(0xFFE7A6FF), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text("Revenue, ad spend, ROAS, users aur crashes, sab ek jagah.", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
        }
        Column(
            Modifier.padding(horizontal = 16.dp).offset(y = (-40).dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface).padding(22.dp)
        ) {
            Text("Sign in", style = MaterialTheme.typography.titleLarge)
            Text("Wahi username/password jo website par hai.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } })
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { advanced = !advanced }, contentPadding = PaddingValues(0.dp)) { Text(if (advanced) "Server address chhupao" else "Server address badlo") }
            if (advanced) OutlinedTextField(url, { url = it }, label = { Text("Dashboard URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Jaise https://tirangae-online.live/mfc_dash/") })
            if (err != null) { Spacer(Modifier.height(10.dp)); Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (user.isBlank() || pass.isBlank()) { err = "Username aur password dono bharo"; return@Button }
                    busy = true; err = null
                    vm.login(url, user.trim(), pass) { e -> busy = false; err = e }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues(0.dp),
            ) {
                Box(Modifier.fillMaxSize().background(BrandGradient, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Sign in", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Login phone mein encrypted save rehta hai; session expire ho to app khud dobara login karta hai.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
