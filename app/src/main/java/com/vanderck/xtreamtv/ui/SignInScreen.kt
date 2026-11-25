package com.vanderck.xtreamtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vanderck.xtreamtv.data.Phase
import com.vanderck.xtreamtv.data.TvModel
import com.vanderck.xtreamtv.data.XtreamCredentials

@Composable
fun SignInScreen(model: TvModel) {
    val phase by model.phase.collectAsStateWithLifecycle()
    var server by rememberSaveable { mutableStateOf(model.store.savedServer) }
    var username by rememberSaveable { mutableStateOf(model.store.savedUsername) }
    // Prefilled when a saved login failed (e.g. offline at launch), so retrying is one tap.
    var password by rememberSaveable { mutableStateOf(model.store.credentials?.password ?: "") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    // Pasting a full get.php link fills in the username and password.
    LaunchedEffect(server) {
        if (server.contains("username=")) {
            XtreamCredentials.parse(server)?.let {
                if (username.isEmpty()) username = it.username
                if (password.isEmpty()) password = it.password
            }
        }
    }

    val busy = phase is Phase.SigningIn
    val error = localError ?: (phase as? Phase.Failed)?.message

    fun submit() {
        val credentials = XtreamCredentials.parse(server, username, password)
        if (credentials == null) {
            localError = "Enter the server address and username from your provider."
            return
        }
        localError = null
        model.signIn(credentials)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0x593F51B5), TvStyle.background), radius = 1400f))
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Default.LiveTv, null, tint = Color.White, modifier = Modifier.size(64.dp))
            Text("Sign In to Your Provider", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "Use the Xtream Codes server, username and password from your IPTV provider, or paste a get.php link.",
                fontSize = 15.sp, color = TvStyle.secondaryText, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))

            val colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(0.2f),
                focusedLabelColor = Color.White,
                cursorColor = Color.White,
            )
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Server") },
                placeholder = { Text("http://provider.example:8080") },
                singleLine = true, enabled = !busy, colors = colors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true, enabled = !busy, colors = colors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true, enabled = !busy, colors = colors,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show password")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(error, color = TvStyle.live, fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            Button(
                onClick = ::submit,
                enabled = !busy && server.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Text("Sign In", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
