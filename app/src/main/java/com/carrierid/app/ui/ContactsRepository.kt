package com.carrierid.app.ui

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.carrier.CarrierLookup

data class ContactNumber(
    val contactId: Long,
    val name: String,
    val number: String,
    val carrier: Carrier?,
)

object ContactsRepository {

    /** Reads every phone number in the device's contacts and tags it with its carrier. */
    fun load(context: Context): List<ContactNumber> {
        val result = mutableListOf<ContactNumber>()
        val seen = HashSet<String>()   // same contact + same number saved twice

        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER),
            null, null,
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val nameCol = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
            val numCol = c.getColumnIndexOrThrow(Phone.NUMBER)
            while (c.moveToNext()) {
                val number = c.getString(numCol) ?: continue
                val id = c.getLong(idCol)
                val key = "$id:" + (CarrierLookup.normalize(number) ?: number.filter { it.isDigit() })
                if (!seen.add(key)) continue
                result += ContactNumber(
                    contactId = id,
                    name = c.getString(nameCol) ?: number,
                    number = number,
                    carrier = CarrierLookup.lookup(number),
                )
            }
        }
        return result
    }
}
