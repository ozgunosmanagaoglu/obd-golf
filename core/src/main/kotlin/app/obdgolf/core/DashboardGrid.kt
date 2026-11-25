package app.obdgolf.core

/** Where a tile sits on the grid, in cells. */
data class Cell(val col: Int, val row: Int, val cols: Int, val rows: Int)

/**
 * Packs tiles onto a grid of [columns] (2 on a phone, 4 on tablets and head units) in order,
 * each into the first free spot, row by row. Tiles wider than the grid are clamped to it.
 */
object GridPacker {
    fun pack(sizes: List<GaugeSize>, columns: Int): List<Cell> {
        val taken = mutableListOf<BooleanArray>()
        fun free(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > columns) return false
            for (rr in r until r + h) {
                val row = taken.getOrNull(rr) ?: continue
                for (cc in c until c + w) if (row[cc]) return false
            }
            return true
        }
        return sizes.map { s ->
            val w = s.columns.coerceAtMost(columns)
            val h = s.rows
            var r = 0
            while (true) {
                val c = (0..columns - w).firstOrNull { free(it, r, w, h) }
                if (c != null) {
                    while (taken.size < r + h) taken += BooleanArray(columns)
                    for (rr in r until r + h) for (cc in c until c + w) taken[rr][cc] = true
                    return@map Cell(c, r, w, h)
                }
                r++
            }
            @Suppress("UNREACHABLE_CODE") Cell(0, 0, 0, 0)
        }
    }

    fun rows(cells: List<Cell>): Int = cells.maxOfOrNull { it.row + it.rows } ?: 0
}

/**
 * The last [windowMs] of each metric, one sample per [stepMs], for sparkline tiles.
 * Fed with every new [CarData]; a newer sample in the same [stepMs] slot replaces the older one.
 */
class MetricHistory(private val windowMs: Long = 60_000, private val stepMs: Long = 1_000) {
    private val samples = mutableMapOf<String, ArrayDeque<Pair<Long, Double>>>()

    fun add(nowMs: Long, data: CarData, metrics: Collection<Metric>) {
        for (m in metrics) {
            val v = m.read(data) ?: continue
            val q = samples.getOrPut(m.id) { ArrayDeque() }
            if (q.isNotEmpty() && q.last().first / stepMs == nowMs / stepMs) q.removeLast()
            q.addLast(nowMs to v)
            while (q.isNotEmpty() && nowMs - q.first().first > windowMs) q.removeFirst()
        }
    }

    fun values(m: Metric): List<Double> = samples[m.id]?.map { it.second } ?: emptyList()

    fun snapshot(): Map<String, List<Double>> = samples.mapValues { (_, q) -> q.map { it.second } }
}
