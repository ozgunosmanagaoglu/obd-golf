package app.obdgolf.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardGridTest {
    private val S = GaugeSize.S1X1
    private val W = GaugeSize.S2X1
    private val L = GaugeSize.S2X2

    @Test fun heroThenTwoSmallOnPhone() {
        val cells = GridPacker.pack(listOf(L, S, S), 2)
        assertEquals(listOf(Cell(0, 0, 2, 2), Cell(0, 2, 1, 1), Cell(1, 2, 1, 1)), cells)
        assertEquals(3, GridPacker.rows(cells))
    }

    @Test fun heroBesideTilesOnWideScreen() {
        val cells = GridPacker.pack(listOf(L, S, S, S, S), 4)
        assertEquals(Cell(0, 0, 2, 2), cells[0])
        assertEquals(listOf(Cell(2, 0, 1, 1), Cell(3, 0, 1, 1), Cell(2, 1, 1, 1), Cell(3, 1, 1, 1)), cells.drop(1))
        assertEquals(2, GridPacker.rows(cells))
    }

    @Test fun smallTileFillsTheHoleBeforeAWideOne() {
        val cells = GridPacker.pack(listOf(S, W, S), 2)
        assertEquals(listOf(Cell(0, 0, 1, 1), Cell(0, 1, 2, 1), Cell(1, 0, 1, 1)), cells)
    }

    @Test fun everyPresetPageFillsWithoutHolesOnBothGrids() {
        for (p in Preset.entries) for (page in p.dashboard.pages) for (cols in listOf(2, 4)) {
            val cells = GridPacker.pack(page.gauges.map { it.size }, cols)
            val used = cells.sumOf { it.cols * it.rows }
            if (cols == 2) assertEquals(GridPacker.rows(cells) * cols, used, "${p.id} on $cols columns")
        }
    }

    @Test fun historyKeepsOneSamplePerStepAndOneMinute() {
        val h = MetricHistory(windowMs = 10_000, stepMs = 1_000)
        val m = Metric.Core.RPM
        for (t in 0L..20_000L step 500) h.add(t, CarData(rpm = t.toDouble()), listOf(m))
        val v = h.values(m)
        assertEquals(20_000.0, v.last())
        assertEquals(11, v.size) // 10 s window at 1 s steps, both ends included
    }

    @Test fun missingValuesAreNotRecorded() {
        val h = MetricHistory()
        h.add(0, CarData(), listOf(Metric.Core.COOLANT))
        assertEquals(emptyList(), h.values(Metric.Core.COOLANT))
    }
}
