package com.vanderck.xtreamtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vanderck.xtreamtv.data.TvModel
import com.vanderck.xtreamtv.ui.AppRoot
import com.vanderck.xtreamtv.ui.XtreamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val model = TvModel.get(this)
        model.restoreSession()
        setContent { XtreamTheme { AppRoot(model) } }
    }
}
