package app.obdgolf.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnitsTest {
    private val metric = Units()
    private val us = Units(SpeedUnit.IMPERIAL, TempUnit.FAHRENHEIT, PressureUnit.PSI)

    @Test fun metricIsUnchanged() {
        assertEquals(100.0, metric.speed(100.0))
        assertEquals(90.0, metric.temp(90.0))
        assertEquals(101.0, metric.pressure(101.0))
    }

    @Test fun mph() = assertEquals(62.137, us.speed(100.0)!!, 0.001)
    @Test fun miles() = assertEquals(6.214, us.distance(10.0)!!, 0.001)
    @Test fun fahrenheit() = assertEquals(194.0, us.temp(90.0)!!, 0.001)
    @Test fun minus40IsTheSameInBoth() = assertEquals(-40.0, us.temp(-40.0)!!, 0.001)
    @Test fun psi() = assertEquals(14.65, us.pressure(101.0)!!, 0.01)
    @Test fun bar() = assertEquals(1.01, Units(pressure = PressureUnit.BAR).pressure(101.0)!!, 0.0001)
    @Test fun nullStaysNull() = assertNull(us.temp(null))

    @Test fun convertByPidUnit() {
        assertEquals(194.0 to "°F", us.convert(90.0, "°C"))
        assertEquals(12.5 to "L/h", us.convert(12.5, "L/h"))
        assertEquals("mi", us.convert(10.0, "km").second)
    }

    @Test fun barGetsTwoDecimals() = assertEquals(2, Units(pressure = PressureUnit.BAR).decimalsFor("kPa", 0))

    @Test fun regionDefaults() {
        assertEquals(us, Units.defaultFor("us"))
        assertEquals(SpeedUnit.IMPERIAL, Units.defaultFor("GB").speed)
        assertEquals(TempUnit.CELSIUS, Units.defaultFor("GB").temp)
        assertEquals(Units(), Units.defaultFor("PL"))
    }
}
