package com.vanderck.xtreamtv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Which container live channels are requested in. */
enum class LiveFormat(val title: String) {
    AUTO("Automatic"), TS("MPEG-TS"), HLS("HLS (m3u8)")
}

/**
 * Persistent settings. The password lives in encrypted preferences (backed by
 * the Android Keystore); favorites, recents and resume points in plain ones.
 */
class Store(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("xtreamtv", Context.MODE_PRIVATE)

    private val secure: SharedPreferences by lazy {
        fun open() = EncryptedSharedPreferences.create(
            context,
            "xtreamtv_secure",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        // A file left by an old install can be unreadable with a new keystore
        // key; drop it (the user signs in again) and start clean.
        runCatching { open() }.getOrElse {
            context.deleteSharedPreferences("xtreamtv_secure")
            open()
        }
    }

    var credentials: XtreamCredentials?
        get() {
            val server = prefs.getString("server", null) ?: return null
            val user = prefs.getString("username", null) ?: return null
            val pass = secure.getString("password", null) ?: return null
            return XtreamCredentials(server, user, pass)
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove("server").remove("username").apply()
                secure.edit().remove("password").apply()
            } else {
                prefs.edit().putString("server", value.server).putString("username", value.username).apply()
                secure.edit().putString("password", value.password).apply()
            }
        }

    val savedServer: String get() = prefs.getString("server", "") ?: ""
    val savedUsername: String get() = prefs.getString("username", "") ?: ""

    var favorites: Set<String>
        get() = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("favorites", value).apply()

    var recents: List<String>
        get() = prefs.getString("recents", "")!!.split('\n').filter { it.isNotEmpty() }
        set(value) = prefs.edit().putString("recents", value.joinToString("\n")).apply()

    var liveFormat: LiveFormat
        get() = runCatching { LiveFormat.valueOf(prefs.getString("liveFormat", "AUTO")!!) }.getOrDefault(LiveFormat.AUTO)
        set(value) = prefs.edit().putString("liveFormat", value.name).apply()

    var lastChannel: String?
        get() = prefs.getString("lastChannel", null)
        set(value) = prefs.edit().putString("lastChannel", value).apply()

    var autoPip: Boolean
        get() = prefs.getBoolean("autoPip", true)
        set(value) = prefs.edit().putBoolean("autoPip", value).apply()

    /** Resume point for a movie or episode, in milliseconds, keyed by a playable id. */
    fun position(key: String): Long = prefs.getLong("pos.$key", 0L)
    fun duration(key: String): Long = prefs.getLong("dur.$key", 0L)

    fun savePosition(key: String, position: Long, duration: Long) {
        val e = prefs.edit()
        // Treat the last 5% (or 3 minutes) as watched, and the first 30s as not started.
        if (duration > 0 && (position > duration * 0.95 || duration - position < 180_000) || position < 30_000) {
            e.remove("pos.$key").remove("dur.$key")
        } else {
            e.putLong("pos.$key", position).putLong("dur.$key", duration)
        }
        e.apply()
    }
}
