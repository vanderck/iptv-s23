package com.vanderck.xtreamtv.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Ported from the macOS app's XtreamTests. */
class XtreamTest {
    private val client = XtreamClient(XtreamCredentials("http://tv.example:8080", "alice", "s3cret"))

    @Test fun credentialsFromBareHost() {
        val c = XtreamCredentials.parse("tv.example:8080/", "a", "b")!!
        assertEquals("http://tv.example:8080", c.server)
    }

    @Test fun credentialsFromPastedPlaylistLink() {
        val c = XtreamCredentials.parse("https://tv.example/get.php?username=bob&password=pw&type=m3u_plus")!!
        assertEquals("https://tv.example", c.server)
        assertEquals("bob", c.username)
        assertEquals("pw", c.password)
    }

    @Test fun credentialsKeepPanelPrefix() {
        val c = XtreamCredentials.parse("http://tv.example/panel/player_api.php", "a", "b")!!
        assertEquals("http://tv.example/panel", c.server)
    }

    @Test fun credentialsRequireUsername() {
        assertNull(XtreamCredentials.parse("tv.example"))
        assertNull(XtreamCredentials.parse("  "))
    }

    @Test fun apiUrl() {
        assertEquals(
            "http://tv.example:8080/player_api.php?username=alice&password=s3cret&action=get_live_streams&category_id=7",
            client.apiUrl("get_live_streams", mapOf("category_id" to "7")).toString(),
        )
    }

    @Test fun streamUrls() {
        val live = XtreamStream(Section.LIVE, 42, "News")
        assertEquals("http://tv.example:8080/live/alice/s3cret/42.m3u8", client.streamUrl(live))
        assertEquals("http://tv.example:8080/live/alice/s3cret/42.ts", client.streamUrl(live, "ts"))
        val movie = XtreamStream(Section.MOVIES, 9, "Film", containerExtension = "mkv")
        assertEquals("http://tv.example:8080/movie/alice/s3cret/9.mkv", client.streamUrl(movie))
    }

    @Test fun accountWithStringlyTypedFields() {
        val json = """
        {"user_info":{"username":"alice","auth":1,"status":"Active","exp_date":"1767225600",
          "is_trial":"0","active_cons":"0","max_connections":"2","allowed_output_formats":["ts","m3u8"]},
         "server_info":{"url":"tv.example","port":8080,"timezone":"UTC"}}
        """
        val account = XtreamAccount.from(JSONObject(json))
        assertTrue(account.isAuthenticated)
        assertEquals(2, account.maxConnections)
        assertEquals(1_767_225_600L, account.expiresAt)
        assertTrue(account.allowsTs)
        assertTrue(account.allowsHls)
    }

    @Test fun liveStreamsSkipBrokenEntries() {
        val json = """
        [
          {"num":1,"name":"BBC One","stream_id":"101","stream_icon":"http://img.example/bbc one.png",
           "category_id":"3","epg_channel_id":"bbc1.uk","tv_archive":1,"tv_archive_duration":"7"},
          {"num":2,"name":"Placeholder","stream_id":null},
          {"num":"3","name":"  CNN  ","stream_id":102,"stream_icon":"","category_id":4,"category_ids":[4,"9"]}
        ]
        """
        val streams = XtreamStream.list(JSONArray(json), Section.LIVE)
        assertEquals(listOf(101, 102), streams.map { it.streamId })
        assertEquals("http://img.example/bbc%20one.png", streams[0].iconUrl)
        assertTrue(streams[0].hasArchive)
        assertEquals(7, streams[0].archiveDays)
        assertEquals("CNN", streams[1].name)
        assertEquals(3, streams[1].number)
        assertNull(streams[1].iconUrl)
        assertEquals("4", streams[1].categoryId)
        assertTrue(streams[1].belongs("9"))
    }

    @Test fun seriesUseSeriesIdAndCover() {
        val json = """
        [{"series_id":"55","name":"Show","cover":"http://img.example/c.jpg","rating":"",
          "rating_5based":4,"backdrop_path":["http://img.example/b.jpg"],"category_id":"1"}]
        """
        val series = XtreamStream.list(JSONArray(json), Section.SERIES)
        assertEquals(55, series.first().streamId)
        assertEquals("http://img.example/c.jpg", series.first().iconUrl)
        assertEquals("http://img.example/b.jpg", series.first().backdropUrl)
        assertEquals(8.0, series.first().rating!!, 0.001)
    }

    @Test fun seriesDetailsKeyedBySeason() {
        val json = """
        {"info":{"name":"Show","plot":"Things happen.","backdrop_path":"http://img.example/b.jpg"},
         "episodes":{"2":[{"id":"902","episode_num":"1","title":"Back","season":2,"container_extension":"mp4"}],
                     "1":[{"id":"802","episode_num":2,"title":"Two","season":1},
                          {"id":"801","episode_num":1,"title":"One","season":1,"info":{"duration_secs":"1500"}}]}}
        """
        val details = XtreamSeriesDetails.from(JSONObject(json))
        assertEquals(listOf(1, 2), details.seasons)
        assertEquals(listOf("801", "802"), details.episodesBySeason[1]!!.map { it.id })
        assertEquals(1500, details.episodesBySeason[1]!!.first().durationSeconds)
        assertEquals("Things happen.", details.info.plot)
        assertEquals("http://img.example/b.jpg", details.info.backdropUrl)
        assertEquals("http://tv.example:8080/series/alice/s3cret/902.mp4", client.episodeUrl(details.episodesBySeason[2]!![0]))
    }

    @Test fun seriesDetailsWithEmptyInfoAndEpisodes() {
        val details = XtreamSeriesDetails.from(JSONObject("""{"info":[],"episodes":[]}"""))
        assertTrue(details.seasons.isEmpty())
        assertNull(details.info.plot)
    }

    @Test fun shortEpgDecodesBase64() {
        fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
        val json = """
        {"epg_listings":[
          {"id":"2","title":"${b64("Later")}","description":"","start_timestamp":"2000","stop_timestamp":"3000"},
          {"id":"1","title":"${b64("Now")}","description":"${b64("Live news")}","start_timestamp":"1000","stop_timestamp":"2000"},
          {"id":"3","title":"broken"}
        ]}
        """
        val listings = XtreamProgramme.list(JSONObject(json))
        assertEquals(listOf("Now", "Later"), listings.map { it.title })
        assertEquals("Live news", listings[0].description)
        assertEquals(0.5f, listings[0].progress(1_500_000), 0.001f)
    }

    @Test fun emptyJsonDetection() {
        assertTrue(isEmptyJson(" [] "))
        assertTrue(isEmptyJson("null"))
        assertFalse(isEmptyJson("[{}]"))
    }

    @Test fun newestPicksMostRecent() {
        val items = (1..10).map { XtreamStream(Section.MOVIES, it, "M$it", added = (it * 7 % 10).toLong()) }
        assertEquals(listOf(9, 8, 7), ShelfBuilder.newest(items, 3).map { it.added!!.toInt() })
    }

    @Test fun foldIgnoresCaseAndAccents() {
        assertEquals("cafe tele", TvModel.fold("Café Télé"))
    }
}
