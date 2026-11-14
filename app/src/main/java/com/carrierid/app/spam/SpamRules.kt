package com.carrierid.app.spam

import com.carrierid.app.carrier.CarrierLookup

enum class Risk { NONE, SUSPICIOUS, SPAM }

/** What the app decided about one incoming call. */
data class Verdict(val risk: Risk, val reason: String?, val block: Boolean) {
    companion object {
        val SAFE = Verdict(Risk.NONE, null, false)
    }
}

/** The user's blocking choices (Settings → Spam & blocking). */
data class SpamSettings(
    val blockSpam: Boolean = true,        // reported numbers + known scam country codes
    val blockHidden: Boolean = false,     // no caller ID
    val blockForeign: Boolean = false,    // any non-Cambodian number
    val blockUnknown: Boolean = false,    // anyone not in contacts
    val blockSuspicious: Boolean = false, // repeat ring-and-hang-up callers
)

/** Everything known about a call at the moment it rings. */
data class CallFacts(
    val number: String?,
    val inContacts: Boolean = false,
    val onBlocklist: Boolean = false,
    val reportedSpam: Boolean = false,
    /** Unanswered calls from this number in the last 24 hours. */
    val recentUnanswered: Int = 0,
    /** The network said the caller ID was spoofed (STIR/SHAKEN). */
    val verificationFailed: Boolean = false,
)

/**
 * Offline spam rules. No number ever leaves the phone.
 *
 * Order matters: your own block list always wins, then contacts are always
 * allowed, then the automatic rules.
 */
object SpamRules {

    /**
     * Country codes widely reported for "one ring" (Wangiri) and premium-rate
     * scam calls. Calls from these to Cambodia are almost never genuine for
     * most people. Remove a code here if you have family there.
     */
    val SCAM_CODES: List<String> = listOf(
        "216", "222", "224", "225", "231", "232", "233", "234", "235", "236", "237",
        "242", "243", "245", "247", "252", "269", "375", "381", "382", "383", "591",
        "592", "675", "677", "678", "679", "682", "685", "686", "688", "690", "691",
        "692", "870", "881", "882", "883", "960", "979",
    )

    fun evaluate(f: CallFacts, s: SpamSettings): Verdict {
        val raw = f.number?.trim().orEmpty()
        val digits = raw.filter(Char::isDigit)

        if (f.onBlocklist) return Verdict(Risk.SPAM, "On your block list", true)
        if (f.inContacts) return Verdict.SAFE

        val v = when {
            f.reportedSpam -> Verdict(Risk.SPAM, "You reported this number as spam", s.blockSpam)
            digits.isEmpty() -> Verdict(Risk.SUSPICIOUS, "Hidden number", s.blockHidden)
            f.verificationFailed -> Verdict(Risk.SPAM, "Caller ID looks faked", s.blockSpam)
            scamCode(raw) != null ->
                Verdict(Risk.SPAM, "Scam-prone country code +${scamCode(raw)}", s.blockSpam || s.blockForeign)
            f.recentUnanswered >= 2 -> Verdict(
                Risk.SUSPICIOUS, "Called ${f.recentUnanswered} times today without an answer",
                s.blockSuspicious,
            )
            isForeign(raw) -> Verdict(Risk.SUSPICIOUS, "International number", s.blockForeign)
            else -> Verdict.SAFE
        }
        // "Block callers not in contacts" covers every rule above
        if (s.blockUnknown && !v.block) {
            return v.copy(block = true, reason = v.reason ?: "Not in your contacts")
        }
        return v
    }

    /** The dialling code if [raw] is from a scam-prone country, else null. */
    fun scamCode(raw: String?): String? {
        val intl = international(raw) ?: return null
        return SCAM_CODES.firstOrNull { intl.startsWith(it) }
    }

    /** True for numbers from outside Cambodia. */
    fun isForeign(raw: String?): Boolean {
        val intl = international(raw) ?: return false
        return !intl.startsWith("855")
    }

    /** Digits after the + for numbers written internationally ("+234…", "00234…"), else null. */
    private fun international(raw: String?): String? {
        val t = raw?.trim() ?: return null
        val d = t.filter(Char::isDigit)
        return when {
            t.startsWith("+") -> d
            d.startsWith("00") -> d.drop(2)
            else -> null
        }.takeIf { !it.isNullOrEmpty() }
    }

    /**
     * One canonical form per number so "+855 12 345 678", "012345678" and
     * "00855-12-345-678" are the same entry: "012345678". Foreign numbers keep
     * their "+code". Null for hidden numbers.
     */
    fun key(raw: String?): String? {
        val t = raw?.trim() ?: return null
        CarrierLookup.normalize(t)?.let { return it }
        val d = t.filter(Char::isDigit)
        if (d.isEmpty()) return null
        return when {
            t.startsWith("+") && d.startsWith("855") -> "0" + d.drop(3)
            t.startsWith("+") -> "+$d"
            d.startsWith("00855") -> "0" + d.drop(5)
            d.startsWith("00") -> "+" + d.drop(2)
            else -> d
        }
    }

    /**
     * A block-list entry as typed by the user. A trailing * blocks a whole
     * prefix: "+234*" (a country), "023*" (Phnom Penh landlines).
     */
    fun entry(typed: String): String? {
        val t = typed.trim()
        if (!t.endsWith("*")) return key(t)
        val body = t.dropLast(1).trim()
        val d = body.filter(Char::isDigit)
        if (d.isEmpty()) return null
        val p = when {
            body.startsWith("+") && d.startsWith("855") -> "0" + d.drop(3)
            body.startsWith("+") -> "+$d"
            d.startsWith("00855") -> "0" + d.drop(5)
            d.startsWith("00") -> "+" + d.drop(2)
            else -> d
        }
        return "$p*"
    }

    /** True if [raw] matches any entry (exact number or "prefix*"). */
    fun matches(entries: Collection<String>, raw: String?): Boolean {
        val k = key(raw) ?: return false
        return entries.any { e ->
            if (e.endsWith("*")) k.startsWith(e.dropLast(1)) else e == k
        }
    }
}
