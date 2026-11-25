package com.vanderck.xtreamtv.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vanderck.xtreamtv.data.Phase
import com.vanderck.xtreamtv.data.Playable
import com.vanderck.xtreamtv.data.Section
import com.vanderck.xtreamtv.data.TvModel
import com.vanderck.xtreamtv.data.XtreamStream
import com.vanderck.xtreamtv.player.PlayerActivity

enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LIVE("Live TV", Icons.Default.LiveTv),
    MOVIES("Movies", Icons.Default.Movie),
    SERIES("Series", Icons.Default.VideoLibrary),
    SEARCH("Search", Icons.Default.Search),
    SETTINGS("Settings", Icons.Default.Settings),
}

/** Actions screens use to open things, so they need not know about navigation. */
class Actions(
    val model: TvModel,
    private val nav: NavHostController,
    private val context: Context,
) {
    /** Live channels play straight away; movies and series open their detail page. */
    fun open(stream: XtreamStream) {
        if (stream.section == Section.LIVE) play(model.playable(stream)) else nav.navigate("detail/${stream.id}")
    }

    fun play(playable: Playable?) {
        if (playable == null) return
        PlayerActivity.launch(context, playable)
    }

    fun toggleFavorite(stream: XtreamStream) {
        val adding = !model.isFavorite(stream)
        model.toggleFavorite(stream)
        Toast.makeText(context, if (adding) "Added to Favorites" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
    }

    fun seeAll(section: Section, shelfId: String) = nav.navigate("grid/${section.name}/$shelfId")
    fun back() = nav.popBackStack()

    /** Resume progress (0–1) for a movie, or null. */
    fun progress(stream: XtreamStream): Float? {
        if (stream.section != Section.MOVIES) return null
        val d = model.store.duration(stream.id)
        return if (d > 0) model.store.position(stream.id).toFloat() / d else null
    }
}

@Composable
fun AppRoot(model: TvModel) {
    val phase by model.phase.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = phase is Phase.Ready,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "phase",
    ) { ready ->
        if (ready) MainScreen(model) else SignInScreen(model)
    }
}

@Composable
private fun MainScreen(model: TvModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val actions = Actions(model, nav, context)

    NavHost(nav, startDestination = "tabs", modifier = Modifier.fillMaxSize().background(TvStyle.background)) {
        composable("tabs") { TabsScreen(model, actions) }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(model, actions, id)
        }
        composable("grid/{section}/{shelf}") { entry ->
            val section = Section.valueOf(entry.arguments?.getString("section") ?: "MOVIES")
            val shelf = entry.arguments?.getString("shelf").orEmpty()
            GridScreen(model, actions, section, shelf)
        }
    }
}

@Composable
internal fun TabsScreen(model: TvModel, actions: Actions) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    Box(Modifier.fillMaxSize().background(TvStyle.background)) {
        when (tab) {
            Tab.HOME -> HomeScreen(model, actions)
            Tab.LIVE -> LiveScreen(model, actions)
            Tab.MOVIES -> BrowseScreen(model, actions, Section.MOVIES)
            Tab.SERIES -> BrowseScreen(model, actions, Section.SERIES)
            Tab.SEARCH -> SearchScreen(model, actions)
            Tab.SETTINGS -> SettingsScreen(model)
        }
        TabBar(tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** The floating, centered pill tab bar. */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .background(Color(0xF2202028), CircleShape)
            .border(1.dp, Color.White.copy(0.08f), CircleShape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (tab in Tab.entries) {
            val isSelected = tab == selected
            Column(
                Modifier
                    .background(if (isSelected) Color.White else Color.Transparent, CircleShape)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.title,
                    tint = if (isSelected) Color.Black else Color.White.copy(0.85f),
                    modifier = Modifier.size(22.dp),
                )
                if (isSelected) {
                    Text(tab.title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }
            }
        }
    }
}
