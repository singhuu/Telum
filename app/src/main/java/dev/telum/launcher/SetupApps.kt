package dev.telum.launcher

/** App choices remain a draft until setup finishes. Keys identify launchable activities. */
data class SetupApps(val order: List<String>, val selected: Set<String>) {
    fun toggle(key: String): SetupApps {
        if (key !in order) return this
        return copy(selected = if (key in selected) selected - key else selected + key)
    }

    fun selectAll() = copy(selected = order.toSet())

    fun clear() = copy(selected = emptySet())

    fun move(key: String, delta: Int): SetupApps {
        val from = order.indexOf(key)
        if (from < 0) return this
        val to = (from.toLong() + delta).coerceIn(0L, order.lastIndex.toLong()).toInt()
        if (from == to) return this
        val moved = order.toMutableList()
        moved.add(to, moved.removeAt(from))
        return copy(order = moved)
    }

    /** Keep prior choices while installed apps change during an unfinished setup. */
    fun reconcile(available: List<String>, defaultSelected: Set<String>): SetupApps {
        val keys = available.distinct()
        val remaining = order.distinct().filter { it in keys }
        val added = keys.filter { it !in remaining }
        return SetupApps(remaining + added,
            selected.intersect(remaining.toSet()) + added.filter { it in defaultSelected })
    }
}

data class SetupLocation(
    val place: String = "",
    val lat: Double = Double.NaN,
    val lon: Double = Double.NaN,
)
