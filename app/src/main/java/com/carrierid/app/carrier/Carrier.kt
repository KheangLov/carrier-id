package com.carrierid.app.carrier

/**
 * Cambodian mobile operators.
 *
 * [color] is an approximate brand colour (ARGB) used for the fallback badge.
 * [logoName] is the drawable name the app looks for. Drop a PNG/WebP/vector named
 * e.g. `logo_smart.png` into app/src/main/res/drawable/ and it is used automatically.
 */
enum class Carrier(val displayName: String, val color: Long, val logoName: String) {
    SMART("Smart", 0xFF00A651, "logo_smart"),
    CELLCARD("Cellcard", 0xFFF39200, "logo_cellcard"),
    METFONE("Metfone", 0xFFE4002B, "logo_metfone"),
    SEATEL("Seatel", 0xFF0072BC, "logo_seatel"),
    COOTEL("CooTel", 0xFF6A1B9A, "logo_cootel"),
    QB("qb", 0xFF607D8B, "logo_qb");

    val colorInt: Int get() = color.toInt()
    val initial: String get() = displayName.first().uppercase()
}
