package com.vanderck.xtreamtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vanderck.xtreamtv.data.LiveFormat
import com.vanderck.xtreamtv.data.Section
import com.vanderck.xtreamtv.data.Shelf
import com.vanderck.xtreamtv.data.TvModel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun SearchScreen(model: TvModel, actions: Actions) {
    var query by rememberSaveable { mutableStateOf("") }
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val catalogs by model.catalogs.collectAsStateWithLifecycle()
    var results by remember { mutableStateOf<List<Shelf>>(emptyList()) }

    LaunchedEffect(query, catalogs) {
        delay(250) // debounce typing
        results = Section.entries.mapNotNull { section ->
            model.search(query, section).takeIf { it.isNotEmpty() }?.let { Shelf("search-${section.name}", section.title, it) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Search", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
        SearchField(query, { query = it }, "Channels, movies and series", Modifier.padding(16.dp))
        when {
            query.trim().length < 2 -> CenteredMessage("Search your whole library", "Type at least two letters.")
            results.isEmpty() -> CenteredMessage("No results for “${query.trim()}”")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = TabBarClearance)) {
                items(results, key = { it.id }) { shelf ->
                    ShelfRow(shelf, favorites, actions::open, actions::toggleFavorite, progressFor = actions::progress)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(model: TvModel) {
    val account by model.account.collectAsStateWithLifecycle()
    val catalogs by model.catalogs.collectAsStateWithLifecycle()
    val liveFormat by model.liveFormat.collectAsStateWithLifecycle()
    val autoPip by model.autoPip.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = TabBarClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))

        SectionTitle("Account")
        Group {
            Line("Server", model.client?.credentials?.server ?: "—")
            Line("Username", account?.username ?: "—")
            Line("Status", account?.status?.ifEmpty { null } ?: "—")
            Line("Expires", account?.expiresAt?.let { DateFormat.getDateInstance().format(Date(it * 1000)) } ?: "Never")
            Line("Connections", listOfNotNull(account?.activeConnections, account?.maxConnections).joinToString(" of ").ifEmpty { "—" })
            if (account?.isTrial == true) Line("Trial", "Yes")
        }

        SectionTitle("Player")
        Group {
            Text("Live stream format", fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in LiveFormat.entries) Chip(f.title, selected = f == liveFormat) { model.setLiveFormat(f) }
            }
            Text(
                "Automatic asks for MPEG-TS and falls back to HLS if the stream won't start.",
                fontSize = 12.sp, color = TvStyle.tertiaryText, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Picture-in-picture on Home", fontSize = 15.sp)
                    Text("Keep watching in a small window when you leave the player.", fontSize = 12.sp, color = TvStyle.tertiaryText)
                }
                Switch(
                    checked = autoPip, onCheckedChange = model::setAutoPip,
                    colors = SwitchDefaults.colors(checkedTrackColor = TvStyle.accent),
                )
            }
        }

        SectionTitle("Library")
        Group {
            for (section in Section.entries) {
                val c = catalogs.getValue(section)
                Line(section.title, when {
                    c.loading -> "Loading…"
                    c.error != null -> "Error"
                    else -> "${c.streams.size} in ${c.categories.size} categories"
                })
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Reload Library", color = TvStyle.accent, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { model.reload() }.padding(vertical = 6.dp),
            )
        }

        Group {
            Text(
                "Sign Out", color = TvStyle.live, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable { confirmSignOut = true }.padding(vertical = 6.dp),
            )
        }
        Text(
            "Tip: long-press any channel, movie or series to add it to Favorites.",
            fontSize = 12.sp, color = TvStyle.tertiaryText,
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your saved password is removed from this phone. Favorites are kept.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; model.signOut() }) { Text("Sign Out", color = TvStyle.live) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(TvStyle.platter, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = TvStyle.secondaryText, maxLines = 1)
    }
}
