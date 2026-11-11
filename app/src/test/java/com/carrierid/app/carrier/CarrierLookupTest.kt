package com.carrierid.app.carrier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarrierLookupTest {

    @Test fun domesticFormats() {
        assertEquals(Carrier.CELLCARD, CarrierLookup.lookup("012 345 678"))
        assertEquals(Carrier.METFONE, CarrierLookup.lookup("088 234 5678"))
        assertEquals(Carrier.SMART, CarrierLookup.lookup("010-234-567"))
        assertEquals(Carrier.SEATEL, CarrierLookup.lookup("018 912 3456"))
        assertEquals(Carrier.COOTEL, CarrierLookup.lookup("038 383 8338"))
    }

    @Test fun internationalFormats() {
        assertEquals(Carrier.METFONE, CarrierLookup.lookup("+855 97 234 5678"))
        assertEquals(Carrier.SMART, CarrierLookup.lookup("+85570123456"))
        assertEquals(Carrier.CELLCARD, CarrierLookup.lookup("00855 92 123 456"))
        assertEquals(Carrier.CELLCARD, CarrierLookup.lookup("85512345678"))
    }

    @Test fun missingLeadingZero() {
        assertEquals(Carrier.SMART, CarrierLookup.lookup("96 123 4567"))
        assertEquals(Carrier.CELLCARD, CarrierLookup.lookup("85 123 456"))  // 085, not +855
    }

    @Test fun rejectsForeignAndJunk() {
        assertNull(CarrierLookup.lookup("+66 81 234 5678"))   // Thailand
        assertNull(CarrierLookup.lookup("+1 212 555 0100"))
        assertNull(CarrierLookup.lookup("123"))
        assertNull(CarrierLookup.lookup(""))
        assertNull(CarrierLookup.lookup(null))
        assertNull(CarrierLookup.lookup("023 123 456"))       // Phnom Penh landline
    }

    @Test fun normalizes() {
        assertEquals("012345678", CarrierLookup.normalize("+855 12 345 678"))
        assertEquals("0882345678", CarrierLookup.normalize("(088) 234-5678"))
    }
}
