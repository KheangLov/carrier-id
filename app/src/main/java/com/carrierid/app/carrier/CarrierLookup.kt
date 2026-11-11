package com.carrierid.app.carrier

/**
 * Works out the operator of a Cambodian (+855) number from its prefix.
 *
 * Prefix table: Telecommunication Regulator of Cambodia (TRC) mobile prefix list,
 * as published Jan 2025. Operators add prefixes over time, so keep this updated.
 *
 * Limitation: a prefix tells you which operator ISSUED the number. If a number
 * has been moved to another operator, the result will be wrong.
 */
object CarrierLookup {

    private val prefixes: Map<String, Carrier> = buildMap {
        fun add(carrier: Carrier, vararg p: String) = p.forEach { put(it, carrier) }

        add(Carrier.CELLCARD, "011", "012", "014", "017", "061", "076", "077",
            "078", "085", "089", "092", "095", "099")
        add(Carrier.METFONE, "031", "060", "066", "067", "068", "071", "088",
            "090", "097")
        add(Carrier.SMART, "010", "015", "016", "069", "070", "081", "086",
            "087", "093", "096", "098")
        add(Carrier.SEATEL, "018")
        add(Carrier.COOTEL, "038")
        add(Carrier.QB, "013", "080", "083", "084")
    }

    /** All prefixes for a carrier, e.g. for a settings / info screen. */
    fun prefixesOf(carrier: Carrier): List<String> =
        prefixes.filterValues { it == carrier }.keys.sorted()

    /**
     * Converts any common way of writing a Cambodian mobile number into the
     * domestic form "0XXXXXXXX" (9–10 digits). Returns null for foreign or
     * malformed numbers.
     *
     *   "+855 12 345 678" -> "012345678"
     *   "00855 88 234 5678" -> "0882345678"
     *   "012-345-678" -> "012345678"
     *   "12345678" -> "012345678"
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        var d = trimmed.filter { it.isDigit() }

        d = when {
            d.startsWith("00855") -> "0" + d.removePrefix("00855")
            trimmed.startsWith("+") && d.startsWith("855") -> "0" + d.removePrefix("855")
            trimmed.startsWith("+") -> return null           // other country
            d.startsWith("855") && d.length in 11..12 -> "0" + d.removePrefix("855")
            !d.startsWith("0") && d.length in 8..9 -> "0$d"
            else -> d
        }
        // Cambodian mobile: 0 + 8 or 9 digits
        return if (d.startsWith("0") && d.length in 9..10) d else null
    }

    fun lookup(raw: String?): Carrier? = normalize(raw)?.let { prefixes[it.take(3)] }
}
