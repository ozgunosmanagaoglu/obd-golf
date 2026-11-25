package app.obdgolf.core

/** A GATT characteristic as discovered on the adapter: its UUID and Android property bits. */
data class GattChar(val uuid: String, val properties: Int) {
    val canNotify: Boolean get() = properties and (PROP_NOTIFY or PROP_INDICATE) != 0
    val canWrite: Boolean get() = properties and (PROP_WRITE or PROP_WRITE_NO_RESPONSE) != 0

    companion object {
        // Same values as android.bluetooth.BluetoothGattCharacteristic.PROPERTY_*.
        const val PROP_WRITE_NO_RESPONSE = 0x04
        const val PROP_WRITE = 0x08
        const val PROP_NOTIFY = 0x10
        const val PROP_INDICATE = 0x20
    }
}

data class GattService(val uuid: String, val characteristics: List<GattChar>)

/** Where to send commands and where replies arrive on a BLE OBD adapter. */
data class BleChannel(val serviceUuid: String, val writeUuid: String, val notifyUuid: String)

/**
 * BLE OBD adapters do not share one standard service: each chip maker picked its own UUIDs.
 * Known layouts are tried first; otherwise any vendor service with a notify and a write
 * characteristic is used, which covers most unknown clones.
 */
object BleProfiles {

    private fun full(short: String) = "0000$short-0000-1000-8000-00805f9b34fb"

    private data class Known(val name: String, val service: String, val write: String, val notify: String)

    private val known = listOf(
        // Vgate iCar Pro BLE, many "ELM327 BLE 4.0" clones
        Known("FFF0", full("fff0"), full("fff2"), full("fff1")),
        // HM-10 style modules (Veepeak BLE, cheap clones): one characteristic for both directions
        Known("FFE0", full("ffe0"), full("ffe1"), full("ffe1")),
        // vLinker and some newer adapters
        Known("18F0", full("18f0"), full("2af1"), full("2af0")),
        // LELink, Konnwei and others using this vendor service
        Known("E781", "e7810a71-73ae-499d-8c15-faa9aef0c3f2",
            "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f", "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"),
    )

    /** Standard Bluetooth services that never carry OBD data. */
    private val standard = setOf("1800", "1801", "180a", "180f").map(::full).toSet()

    fun pick(services: List<GattService>): BleChannel? {
        val byUuid = services.associateBy { it.uuid.lowercase() }
        for (k in known) {
            val s = byUuid[k.service] ?: continue
            val w = s.characteristics.firstOrNull { it.uuid.lowercase() == k.write && it.canWrite } ?: continue
            val n = s.characteristics.firstOrNull { it.uuid.lowercase() == k.notify && it.canNotify } ?: continue
            return BleChannel(s.uuid, w.uuid, n.uuid)
        }
        for (s in services) {
            if (s.uuid.lowercase() in standard) continue
            val n = s.characteristics.firstOrNull { it.canNotify } ?: continue
            val w = s.characteristics.firstOrNull { it.canWrite && it.uuid == n.uuid }
                ?: s.characteristics.firstOrNull { it.canWrite }
                ?: continue
            return BleChannel(s.uuid, w.uuid, n.uuid)
        }
        return null
    }

    /** Names BLE OBD adapters advertise, used to sort the scan list. */
    fun looksLikeObd(name: String?): Boolean {
        val n = name?.uppercase() ?: return false
        return listOf("OBD", "ELM", "VLINK", "V-LINK", "VLINKER", "VEEPEAK", "ICAR", "VGATE", "OBDLINK", "KIWI", "LELINK", "KONNWEI")
            .any { it in n }
    }
}
