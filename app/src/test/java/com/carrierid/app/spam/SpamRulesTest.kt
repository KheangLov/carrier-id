package com.carrierid.app.spam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamRulesTest {

    private val defaults = SpamSettings()

    @Test fun keysAreCanonical() {
        assertEquals("012345678", SpamRules.key("+855 12 345 678"))
        assertEquals("012345678", SpamRules.key("00855-12-345-678"))
        assertEquals("012345678", SpamRules.key("012 345 678"))
        assertEquals("+2348012345678", SpamRules.key("+234 801 234 5678"))
        assertEquals("+2348012345678", SpamRules.key("002348012345678"))
        assertEquals("023123456", SpamRules.key("+855 23 123 456"))
        assertNull(SpamRules.key(""))
        assertNull(SpamRules.key(null))
    }

    @Test fun blockListExactAndPrefix() {
        val list = listOf(SpamRules.entry("088 234 5678")!!, SpamRules.entry("+234*")!!, SpamRules.entry("023*")!!)
        assertTrue(SpamRules.matches(list, "+855882345678"))
        assertTrue(SpamRules.matches(list, "+2348012345678"))
        assertTrue(SpamRules.matches(list, "023 999 888"))
        assertFalse(SpamRules.matches(list, "012 345 678"))
        assertEquals("012*", SpamRules.entry("+855 12*"))
    }

    @Test fun blockListAlwaysWinsEvenForContacts() {
        val v = SpamRules.evaluate(CallFacts("012345678", inContacts = true, onBlocklist = true), defaults)
        assertTrue(v.block)
    }

    @Test fun contactsAreNeverFlagged() {
        val v = SpamRules.evaluate(
            CallFacts("+2348012345678", inContacts = true),
            defaults.copy(blockForeign = true, blockUnknown = true),
        )
        assertEquals(Risk.NONE, v.risk)
        assertFalse(v.block)
    }

    @Test fun scamCountryCodesBlockedByDefault() {
        val v = SpamRules.evaluate(CallFacts("+2348012345678"), defaults)
        assertEquals(Risk.SPAM, v.risk)
        assertTrue(v.block)
        assertEquals("234", SpamRules.scamCode("+234 801 234 5678"))
        assertNull(SpamRules.scamCode("+66 81 234 5678"))
    }

    @Test fun ordinaryUnknownCambodianNumberIsSafe() {
        val v = SpamRules.evaluate(CallFacts("088 234 5678"), defaults)
        assertEquals(Verdict.SAFE, v)
    }

    @Test fun hiddenNumbersWarnButOnlyBlockWhenAsked() {
        assertFalse(SpamRules.evaluate(CallFacts(null), defaults).block)
        assertEquals(Risk.SUSPICIOUS, SpamRules.evaluate(CallFacts(null), defaults).risk)
        assertTrue(SpamRules.evaluate(CallFacts(""), defaults.copy(blockHidden = true)).block)
    }

    @Test fun foreignNumbers() {
        val thai = CallFacts("+66 81 234 5678")
        assertEquals(Risk.SUSPICIOUS, SpamRules.evaluate(thai, defaults).risk)
        assertFalse(SpamRules.evaluate(thai, defaults).block)
        assertTrue(SpamRules.evaluate(thai, defaults.copy(blockForeign = true)).block)
        assertFalse(SpamRules.isForeign("+855 12 345 678"))
    }

    @Test fun reportedAndRepeatCallers() {
        assertTrue(SpamRules.evaluate(CallFacts("012345678", reportedSpam = true), defaults).block)
        val repeat = CallFacts("012345678", recentUnanswered = 3)
        assertEquals(Risk.SUSPICIOUS, SpamRules.evaluate(repeat, defaults).risk)
        assertFalse(SpamRules.evaluate(repeat, defaults).block)
        assertTrue(SpamRules.evaluate(repeat, defaults.copy(blockSuspicious = true)).block)
    }

    @Test fun strictModeBlocksAllUnknown() {
        val v = SpamRules.evaluate(CallFacts("088 234 5678"), defaults.copy(blockUnknown = true))
        assertTrue(v.block)
        assertEquals("Not in your contacts", v.reason)
    }
}
