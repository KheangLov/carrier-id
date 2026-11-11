package com.carrierid.app.ui

import android.content.Context
import android.provider.CallLog
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.carrier.CarrierLookup

enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, OTHER }

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val kind: CallKind,
    val date: Long,
    val durationSec: Long,
    val carrier: Carrier?,
)

object CallLogRepository {

    /** Most recent calls first. */
    fun load(context: Context, limit: Int = 500): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val num = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val name = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val type = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val date = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val dur = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext() && out.size < limit) {
                val number = c.getString(num).orEmpty()
                out += CallEntry(
                    id = c.getLong(id),
                    number = number,
                    name = c.getString(name)?.takeIf { it.isNotBlank() },
                    kind = when (c.getInt(type)) {
                        CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallKind.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallKind.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallKind.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallKind.BLOCKED
                        else -> CallKind.OTHER
                    },
                    date = c.getLong(date),
                    durationSec = c.getLong(dur),
                    carrier = CarrierLookup.lookup(number),
                )
            }
        }
        return out
    }
}
