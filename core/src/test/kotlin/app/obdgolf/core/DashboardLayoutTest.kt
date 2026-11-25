package app.obdgolf.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardLayoutTest {
    @Test fun everyPresetSurvivesTheCodec() {
        for (p in Preset.entries) assertEquals(p.dashboard, DashboardCodec.decode(DashboardCodec.encode(p.dashboard)))
    }

    /** Driving first: a glance at the first page must be enough. */
    @Test fun firstPageOfEveryPresetHasAtMostFourTiles() {
        for (p in Preset.entries) assertTrue(p.dashboard.pages.first().gauges.size <= 4, p.id)
    }

    @Test fun pagesFillTheTwoColumnGridWithoutHoles() {
        for (p in Preset.entries) for (page in p.dashboard.pages) {
            val cells = page.gauges.sumOf { it.size.columns * it.size.rows }
            assertEquals(0, cells % 2, "${p.id}: odd number of cells leaves a hole")
        }
    }

    @Test fun everyMetricHasAStableId() {
        for (m in Metric.all) assertEquals(m, Metric.byId(m.id))
    }

    @Test fun unknownMetricIsSkipped() {
        val d = DashboardCodec.decode("sport\nrpm,arc,2x2,,,1;warp_drive,digital,1x1,,,1;speed,digital,1x1,,,1")!!
        assertEquals(listOf(Metric.Core.RPM, Metric.Core.SPEED), d.pages.single().gauges.map { it.metric })
    }

    @Test fun badStyleAndSizeFallBack() {
        val g = DashboardCodec.decode("minimal\nrpm,hologram,9x9,,,1")!!.pages.single().gauges.single()
        assertEquals(GaugeStyle.DIGITAL, g.style)
        assertEquals(GaugeSize.S1X1, g.size)
    }

    @Test fun garbageGivesNull() {
        assertNull(DashboardCodec.decode(null))
        assertNull(DashboardCodec.decode("minimal"))
    }

    @Test fun defaultCoolantWarning() {
        val a = Gauge(Metric.Core.COOLANT).alarm(CarData(coolantC = 108.0))!!
        assertTrue(a.above)
        assertEquals(105.0, a.limit)
    }

    @Test fun ownLimitWins() = assertNull(Gauge(Metric.Core.COOLANT, alarmAbove = 110.0).alarm(CarData(coolantC = 108.0)))

    @Test fun alarmsCanBeTurnedOff() = assertNull(Gauge(Metric.Core.BATTERY, alarms = false).alarm(CarData(batteryV = 11.0)))

    @Test fun lowBattery() = assertFalse(Gauge(Metric.Core.BATTERY).alarm(CarData(batteryV = 11.5))!!.above)

    @Test fun noValueNoAlarm() = assertTrue(Preset.DIAGNOSTIC.dashboard.alarms(CarData()).isEmpty())

    @Test fun sameMetricOnTwoPagesAlarmsOnce() =
        assertEquals(1, Preset.ECO.dashboard.alarms(CarData(coolantC = 120.0)).size)

    @Test fun extraMetricReadsFromExtras() =
        assertEquals(94.0, Metric.Extra(Pid.OIL_TEMP).read(CarData(extra = mapOf(Pid.OIL_TEMP to 94.0))))

    @Test fun moveRemoveAdd() {
        val p = Preset.MINIMAL.dashboard.pages.first()
        assertEquals(Metric.Core.COOLANT, p.move(1, -1).gauges[0].metric)
        assertEquals(p, p.move(0, -1))
        assertEquals(Metric.Core.RPM, p.remove(0).add(Gauge(Metric.Core.RPM)).gauges.last().metric)
    }
}
