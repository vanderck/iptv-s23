package com.vanderck.xtreamtv.data

/**
 * Builds the browse shelves from the catalogs. Pure, so it can run off the
 * main thread, and careful not to sort whole catalogs.
 */
object ShelfBuilder {
    fun build(catalogs: Map<Section, Catalog>, favorites: Set<String>, recents: List<String>): Shelves {
        val sections = Section.entries.associateWith { sectionShelves(it, catalogs[it] ?: Catalog(), favorites) }

        val hasArt: (XtreamStream) -> Boolean = { it.backdropUrl != null || it.iconUrl != null }
        val featured = (newest(catalogs[Section.MOVIES]?.streams.orEmpty(), 6, hasArt) +
            newest(catalogs[Section.SERIES]?.streams.orEmpty(), 6, hasArt))
            .sortedByDescending { it.added ?: 0 }
            .take(6)

        return Shelves(home = homeShelves(catalogs, favorites, recents), featured = featured, sections = sections)
    }

    private fun sectionShelves(section: Section, catalog: Catalog, favorites: Set<String>): List<Shelf> {
        val shelves = mutableListOf<Shelf>()
        val key = section.name.lowercase()
        val favs = catalog.streams.filter { it.id in favorites }
        if (favs.isNotEmpty()) shelves += Shelf("$key-favorites", "Favorites", favs)
        if (section != Section.LIVE) {
            val recent = newest(catalog.streams, 30)
            if (recent.isNotEmpty()) shelves += Shelf("$key-new", "Recently Added", recent)
        }
        for (category in catalog.categories) {
            val items = catalog.byCategory[category.id]
            if (!items.isNullOrEmpty()) shelves += Shelf("$key-${category.id}", category.name, items)
        }
        return shelves
    }

    private fun homeShelves(catalogs: Map<Section, Catalog>, favorites: Set<String>, recents: List<String>): List<Shelf> {
        fun lookup(id: String) = Section.entries.firstNotNullOfOrNull { catalogs[it]?.byId?.get(id) }
        val shelves = mutableListOf<Shelf>()

        val upNext = recents.mapNotNull(::lookup)
        if (upNext.isNotEmpty()) shelves += Shelf("home-recent", "Up Next", upNext)

        val favs = favorites.mapNotNull(::lookup).sortedBy { it.name.lowercase() }
        if (favs.isNotEmpty()) shelves += Shelf("home-favorites", "Favorites", favs)

        for ((section, title) in listOf(Section.MOVIES to "New Movies", Section.SERIES to "New Series")) {
            val items = newest(catalogs[section]?.streams.orEmpty(), 25)
            if (items.isNotEmpty()) shelves += Shelf("home-${section.name.lowercase()}", title, items)
        }
        val live = catalogs[Section.LIVE]?.streams.orEmpty()
        if (live.isNotEmpty()) shelves += Shelf("home-live", "Live Channels", live.take(25))
        return shelves
    }

    /** The `limit` most recently added entries, newest first, in one pass. */
    fun newest(items: List<XtreamStream>, limit: Int, include: (XtreamStream) -> Boolean = { true }): List<XtreamStream> {
        val top = ArrayList<XtreamStream>(limit + 1)
        for (item in items) {
            val added = item.added ?: continue
            if (top.size == limit && added <= (top.last().added ?: 0)) continue
            if (!include(item)) continue
            val pos = top.indexOfFirst { (it.added ?: 0) < added }.let { if (it < 0) top.size else it }
            top.add(pos, item)
            if (top.size > limit) top.removeAt(top.size - 1)
        }
        return top
    }
}
