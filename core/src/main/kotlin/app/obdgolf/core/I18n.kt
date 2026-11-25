package app.obdgolf.core

import java.io.IOException
import java.util.Locale

/**
 * Languages the app ships. English is the base: every [Str] carries its English text, and a
 * missing translation falls back to it. Plain Kotlin (not Android resources) so the same tables
 * work in the iOS build later and can be checked by unit tests.
 */
enum class Lang(val code: String, val nativeName: String, internal val table: Map<Str, String>) {
    EN("en", "English", emptyMap()),
    TR("tr", "Türkçe", TR_STRINGS),
    DE("de", "Deutsch", DE_STRINGS),
    FR("fr", "Français", FR_STRINGS),
    ES("es", "Español", ES_STRINGS),
    IT("it", "Italiano", IT_STRINGS),
    PT("pt", "Português", PT_STRINGS),
    NL("nl", "Nederlands", NL_STRINGS),
    PL("pl", "Polski", PL_STRINGS),
    CS("cs", "Čeština", CS_STRINGS),
    RU("ru", "Русский", RU_STRINGS),
    UK("uk", "Українська", UK_STRINGS);

    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        /** Picks the language for a phone locale ("pl", "pt-BR", "tr-TR"…); English when we don't have it. */
        fun forLocale(locale: Locale): Lang = fromCode(locale.language) ?: EN

        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code == code?.lowercase()?.substringBefore('-') }
    }
}

/** A text to show: either a translatable key with arguments, or raw text (from Android, a device name…). */
sealed interface Msg {
    data class Key(val key: Str, val args: List<Any> = emptyList()) : Msg
    data class Raw(val text: String) : Msg

    companion object {
        fun of(key: Str, vararg args: Any): Msg = Key(key, args.toList())
    }
}

/** An I/O error whose message the UI shows in the user's language. */
open class LocalizedIOException(val msg: Msg, cause: Throwable? = null) : IOException(Strings(Lang.EN).of(msg), cause) {
    constructor(key: Str, vararg args: Any) : this(Msg.Key(key, args.toList()))
}

/** What to show for a failure: its translated text when it has one, otherwise its own message. */
fun Throwable.toMsg(fallback: Str = Str.CONNECTION_ERROR): Msg = when (this) {
    is LocalizedIOException -> msg
    else -> message?.takeIf { it.isNotBlank() }?.let { Msg.Raw(it) } ?: Msg.Key(fallback)
}

class Strings(val lang: Lang) {
    operator fun get(key: Str): String = lang.table[key] ?: key.en

    /** Fills {0}, {1}… with [args]; an argument that is itself a [Msg] or [Str] is translated first. */
    fun format(key: Str, vararg args: Any): String = fill(get(key), args.toList())

    fun of(msg: Msg): String = when (msg) {
        is Msg.Raw -> msg.text
        is Msg.Key -> fill(get(msg.key), msg.args)
    }

    fun pid(pid: Pid): String = get(pid.labelKey)
    fun dtcKind(kind: DtcKind): String = get(kind.labelKey)
    fun describe(dtc: Dtc): String = DtcInfo.describe(dtc.code, lang)

    private fun fill(text: String, args: List<Any>): String =
        args.foldIndexed(text) { i, acc, a ->
            acc.replace("{$i}", when (a) {
                is Msg -> of(a)
                is Str -> get(a)
                else -> a.toString()
            })
        }
}

/**
 * Every text in the app, with its English wording. Placeholders are {0}, {1}…; translations must
 * keep the same ones (a unit test checks). Counts are written as "Label: 3" to avoid plural rules.
 */
enum class Str(val en: String) {
    // Tabs and common words
    TAB_DASHBOARD("Dashboard"),
    TAB_DATA("Data"),
    TAB_FAULTS("Faults"),
    TAB_HISTORY("History"),
    CONNECTION("Connection"),
    CONNECTED("Connected"),
    CONNECTING("Connecting"),
    CONNECTING_ELLIPSIS("Connecting…"),
    WAITING("Waiting"),
    NOT_CONNECTED("Not connected"),
    CANCEL("Cancel"),
    DELETE("Delete"),
    NO_DATA("No data"),

    // Splash
    SPLASH_DEMO_STARTING("Starting demo mode…"),
    SPLASH_CONNECTING("Connecting to {0}…"),
    SPLASH_DEMO_READY("Demo mode ready"),
    SPLASH_CONNECTED("Connected to {0}"),
    SPLASH_PREPARING("Getting ready…"),

    // Live values
    RPM("RPM"),
    SPEED("Speed"),
    COOLANT_TEMP("Coolant temp"),
    INTAKE_TEMP("Intake air temp"),
    OUTSIDE_TEMP("Outside temp"),
    OUTSIDE_SHORT("Outside {0}"),
    ENGINE_LOAD("Engine load"),
    ACCELERATOR("Accelerator"),
    BATTERY_VOLTAGE("Battery voltage"),
    BATTERY("Battery"),

    // Data screen
    ALL_DATA("All data"),
    LIVE_HZ("Live · {0} Hz"),
    VEHICLE("Vehicle"),
    UNKNOWN_VEHICLE("Unknown vehicle"),
    ADAPTER("Adapter"),
    STANDARD_OBD("Standard OBD"),
    OTHER_DATA("Other data this car supports"),
    OTHER_DATA_HINT("These values are read in turns and refresh every few seconds."),
    BRAND_DATA("Maker data ({0})"),
    BRAND_DATA_HINT("Read straight from the engine and gearbox control units. These are not part of the OBD standard, so only values your car answers sensibly are shown."),
    BRAND_OIL_TEMP("Engine oil temperature"),
    BRAND_GEARBOX_TEMP("Gearbox oil temperature"),

    // Connection screen
    CONNECTED_WITH("Connected · {0}"),
    PERMISSION_NEEDED("Permission needed"),
    RETRYING("Retrying"),
    PICK_ADAPTER("Pick your adapter below"),
    ALLOW("Allow"),
    ALLOW_HINT("If no window opens, the settings page opens: Permissions > Nearby devices > Allow."),
    DISCONNECT("Disconnect"),
    ADAPTER_HINT_CLASSIC("Classic Bluetooth: Vgate iCar Pro BT 3.0, OBDLink LX/MX+, most cheap ELM327. Pair it in your phone's Bluetooth settings first."),
    ADAPTER_HINT_BLE("Bluetooth LE: Vgate iCar Pro BLE, vLinker, OBDLink CX, Veepeak BLE. No pairing needed, the app searches for it."),
    ADAPTER_HINT_WIFI("Wi-Fi: ELM327 Wi-Fi, Vgate iCar Wi-Fi, vLinker Wi-Fi. The phone joins the adapter's Wi-Fi network."),
    CLASSIC_PAIRED("Classic Bluetooth (paired)"),
    REFRESH("Refresh"),
    BT_OFF_OR_DENIED("Bluetooth is off or permission was denied"),
    NO_PAIRED("No paired devices. Pair the adapter in your phone's Bluetooth settings (PIN is usually 1234 or 0000)."),
    CONNECT("Connect"),
    NOT_OBD("Not OBD?"),
    BLE_ADAPTER("Bluetooth LE adapter"),
    SEARCHING("Searching…"),
    SEARCH("Search"),
    BLE_INTRO("BLE adapters (Vgate BLE, vLinker, OBDLink CX, Veepeak…) are not paired in the phone settings. Plug in the adapter, turn the ignition on, then tap \"{0}\"."),
    WIFI_ADAPTER("Wi-Fi adapter"),
    WIFI_INTRO("First join the adapter's network in your phone's Wi-Fi settings (usually called WiFi_OBDII, OBDII or V-LINK). Most adapters use the address 192.168.0.10:35000."),
    WIFI_ADDRESS("Address (IP:port)"),
    WIFI_CONNECT("Connect via Wi-Fi"),
    SETTINGS("Settings"),
    AUTO_CONNECT("Connect automatically on start"),
    DEMO_MODE("Demo mode (no car)"),
    LANGUAGE("Language"),
    LANGUAGE_SYSTEM("Phone language"),
    UNITS("Units"),
    SPEED_DISTANCE("Speed and distance"),
    TEMPERATURE("Temperature"),
    PRESSURE("Pressure"),
    ADAPTER_LOG("Adapter log"),
    ADAPTER_LOG_HINT("If an adapter or car doesn't work, share this log. It holds the commands sent to the adapter and its replies, no personal data."),
    SHARE_LOG("Share log"),
    SHARE_LOG_SUBJECT("Revora adapter log"),

    // Faults screen
    FAULT_CODES("Fault codes"),
    LAST_READ("Last read {0}"),
    FAULT_SUBTITLE("Engine and transmission (OBD-II)"),
    READ_CODES("Read codes"),
    CLEAR_CODES("Clear codes"),
    FAULT_HINT_CONNECTED("Turn the ignition on (the engine may run) and tap \"{0}\"."),
    FAULT_HINT_DISCONNECTED("Connect to the adapter first. Not near the car? Turn on demo mode in the Connection tab to try it."),
    CODES_CLEARED("Codes cleared. If the fault is still there, it comes back on the next drive."),
    FAULT_FOOTER("This screen reads the engine control unit and any other unit that answers standard OBD (often the transmission). ABS, airbag, A/C and other units need manufacturer protocols; that part comes later."),
    CLEAR_TITLE("Clear the codes?"),
    CLEAR_BODY("• Engine off, ignition on.\n• Stored and pending codes and freeze frame data are erased, the engine light goes out.\n• Emission readiness tests reset; they complete by themselves over a few drives.\n• It does not fix the fault: if the problem remains, the code comes back.\n\nTake a screenshot of the codes before clearing."),
    CLEAR("Clear"),
    MIL("Check engine light"),
    NOT_READ_YET("Not read yet"),
    MIL_UNKNOWN("The car did not report the lamp state"),
    MIL_ON("Check engine light is ON"),
    MIL_OFF("Check engine light is off"),
    STORED_CODES("Stored codes: {0}"),
    NO_STORED_CODES("No stored codes"),
    NO_CODES("No fault codes"),
    CODES_FOUND("Codes found: {0}"),
    MANUFACTURER_HINT("For details you may need a manufacturer tool (for example VCDS, OBDeleven or FORScan)."),
    FREEZE_FRAME("Values when the fault was set"),
    DTC_STORED("Stored"),
    DTC_PENDING("Pending"),
    DTC_PERMANENT("Permanent"),
    FAULT_CONNECT_FIRST("Connect to the adapter first (or turn on demo mode in the Connection tab)."),
    READING_CODES("Reading fault codes…"),
    READ_FAILED("Couldn't read: {0}"),
    CLEARING_CODES("Clearing codes…"),
    CLEAR_NOT_CONFIRMED("The car did not confirm the clear command. The ignition must be on and the engine off."),
    CLEAR_FAILED("Couldn't clear: {0}"),

    // History
    YOUR_DRIVES("Your drives"),
    TOTAL("Total"),
    DRIVES("Drives"),
    DURATION("Time"),
    NO_DRIVES("No drives yet. Drives are saved automatically when you connect to the car and start the engine."),
    HOURS_MINUTES("{0} h {1} min"),
    MINUTES("{0} min"),
    MAX_VALUE("max {0}"),
    DISTANCE("Distance"),
    AVG_SPEED("Avg speed"),
    MAX_SPEED("Max speed"),
    MAX_RPM("Max RPM"),
    IDLE("Idle"),
    MAX_COOLANT("Max coolant"),
    MIN_BATTERY("Min battery"),
    DELETE_DRIVE_TITLE("Delete this drive?"),
    DELETE_DRIVE_BODY("This record is deleted for good."),

    // Errors
    CONNECTION_ERROR("Connection error"),
    CONNECTION_CLOSED("Connection closed"),
    ADAPTER_LINK_LOST("Lost the link to the adapter"),
    SCAN_PERMISSION_NEEDED("Permission is needed to search."),
    BT_PERMISSION_DENIED("Bluetooth permission was not granted. Tap \"{0}\" below."),
    NO_BT_PERMISSION("No Bluetooth permission"),
    NO_BLE_FOUND("No BLE device found. Is the adapter plugged in and the ignition on?"),
    BLE_SCAN_FAILED("BLE search failed"),
    BT_OFF("Bluetooth is off"),
    NO_BLUETOOTH("This phone has no Bluetooth"),
    BLE_SCAN_NOT_STARTED("BLE search didn't start (code {0}). Try turning Bluetooth off and on."),
    BLE_CONNECT_FAILED("Couldn't connect to the BLE adapter (status {0}). Is the ignition on?"),
    BLE_START_FAILED("Couldn't start the BLE connection"),
    BLE_SERVICES_FAILED("Couldn't read the BLE services"),
    BLE_NOT_OBD("This BLE device doesn't look like an OBD adapter (no OBD service)."),
    BLE_NO_REPLY("The BLE adapter didn't answer. Is the ignition on and the adapter nearby?"),
    BLE_WRITE_FAILED("Couldn't write to the BLE adapter"),
    BT_BLOCKED("Android blocked the connection because a permission is missing.\n{0}"),
    BT_CONNECT_FAILED("Couldn't connect to the adapter (is the ignition on?)\n{0}"),
    WIFI_CONNECT_FAILED("Couldn't connect to the Wi-Fi adapter ({0}). Is the phone on the adapter's Wi-Fi network?"),
    VEHICLE_NO_REPLY("The car is not answering. Is the ignition on?{0}"),
    ADAPTER_NO_REPLY("The adapter is not answering. Try unplugging it and plugging it back in."),
    VEHICLE_LINK_LOST("Lost the link to the car. Was the ignition turned off?"),
    ADAPTER_TIMEOUT("The adapter didn't answer \"{0}\" in time"),
    ADAPTER_CLOSED("The adapter closed the connection"),

    // Fault code groups (when a code has no exact text)
    DTC_MANUFACTURER("Manufacturer-specific code ({0})"),
    DTC_GROUP_FUEL_AIR("Fuel and air metering"),
    DTC_GROUP_IGNITION("Ignition / misfire"),
    DTC_GROUP_EMISSIONS("Emission controls"),
    DTC_GROUP_SPEED_IDLE("Speed, idle and electrics"),
    DTC_GROUP_ECU("Engine computer and outputs"),
    DTC_GROUP_TRANSMISSION("Transmission"),
    DTC_GROUP_HYBRID("Hybrid system"),
    DTC_GROUP_POWERTRAIN("Engine / transmission"),
    DTC_GROUP_CHASSIS("Chassis (ABS, ESP, steering)"),
    DTC_GROUP_BODY("Body (airbag, A/C, comfort)"),
    DTC_GROUP_NETWORK("Communication between control units (CAN)"),
    DTC_GROUP_UNKNOWN("Unknown code"),

    // OBD values (mode 01 PIDs), one per Pid: PID_<name>
    PID_ENGINE_LOAD("Engine load"),
    PID_COOLANT_TEMP("Coolant temperature"),
    PID_RPM("Engine speed"),
    PID_SPEED("Vehicle speed"),
    PID_INTAKE_TEMP("Intake air temperature"),
    PID_THROTTLE("Throttle position"),
    PID_AMBIENT_TEMP("Outside temperature"),
    PID_SHORT_FUEL_TRIM_1("Short term fuel trim (B1)"),
    PID_LONG_FUEL_TRIM_1("Long term fuel trim (B1)"),
    PID_SHORT_FUEL_TRIM_2("Short term fuel trim (B2)"),
    PID_LONG_FUEL_TRIM_2("Long term fuel trim (B2)"),
    PID_FUEL_PRESSURE("Fuel pressure"),
    PID_MAP("Intake manifold pressure"),
    PID_TIMING_ADVANCE("Ignition timing advance"),
    PID_MAF("Air flow (MAF)"),
    PID_RUN_TIME("Engine run time"),
    PID_DISTANCE_WITH_MIL("Distance with engine light on"),
    PID_FUEL_RAIL_PRESSURE("Fuel rail pressure"),
    PID_COMMANDED_EGR("Commanded EGR"),
    PID_FUEL_LEVEL("Fuel level"),
    PID_DISTANCE_SINCE_CLEAR("Distance since codes cleared"),
    PID_BARO("Barometric pressure"),
    PID_CATALYST_TEMP_1("Catalyst temperature (B1S1)"),
    PID_MODULE_VOLTAGE("Control module voltage"),
    PID_ABSOLUTE_LOAD("Absolute load"),
    PID_LAMBDA("Commanded lambda"),
    PID_RELATIVE_THROTTLE("Relative throttle position"),
    PID_ACCEL_PEDAL_D("Accelerator pedal (D)"),
    PID_ACCEL_PEDAL_E("Accelerator pedal (E)"),
    PID_THROTTLE_ACTUATOR("Commanded throttle"),
    PID_ETHANOL("Ethanol content"),
    PID_OIL_TEMP("Oil temperature"),
    PID_FUEL_RATE("Fuel consumption"),
    PID_DEMANDED_TORQUE("Demanded torque"),
    PID_ACTUAL_TORQUE("Actual torque"),
    ;

    /** The {0}, {1}… placeholders this text uses. */
    val placeholders: Set<String> get() = Regex("\\{\\d}").findAll(en).map { it.value }.toSet()
}
