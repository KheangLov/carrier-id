# Carrier ID (Cambodia)

Shows the caller's mobile operator (Smart, Cellcard, Metfone, Seatel, CooTel, qb)
as a floating card over the Samsung incoming-call screen, plus a contacts list
with a carrier logo next to every number.

## Open & run

1. Open this folder in **Android Studio** (Ladybug or newer) → let Gradle sync.
2. Plug in your Samsung phone with USB debugging on → **Run ▶**.
3. In the app's **Setup** tab, enable all 4 steps:
   1. **Caller ID & spam app** – Android then sends every incoming number to the app.
   2. **Display over other apps** – lets the card appear above the call screen.
   3. **Contacts & phone** – contacts list; also needed so calls from *saved*
      contacts reach the app, and to hide the card when the call ends.
   4. **Battery** – set the app to *Unrestricted*. On Samsung also add it to
      *Settings → Battery → Background usage limits → Never sleeping apps*.
4. Type a number in **Check a number** and tap **Preview call overlay** to test
   without a real call. Then have someone call you.

Minimum Android version: 10 (API 29). Target: Android 15 (API 35).

## Adding the real logos

The app draws a coloured letter badge until you add logo files. Put square
PNG/WebP images (about 192×192 px) in `app/src/main/res/drawable/` with these
names, and they are used everywhere automatically:

| Carrier  | File name           |
|----------|---------------------|
| Smart    | `logo_smart.png`    |
| Cellcard | `logo_cellcard.png` |
| Metfone  | `logo_metfone.png`  |
| Seatel   | `logo_seatel.png`   |
| CooTel   | `logo_cootel.png`   |
| qb       | `logo_qb.png`       |

Logos are trademarks of each operator. Personal use is fine; for a Play Store
release, get permission or keep the coloured badges.

## How it works

| File | Role |
|------|------|
| `carrier/CarrierLookup.kt` | Prefix table + number normalising (`+855…`, `00855…`, `0…`) |
| `call/CallerIdScreeningService.kt` | Receives incoming calls, never blocks them |
| `call/CallerOverlay.kt` | The floating card (drag up/down to move; position is remembered) |
| `call/CallWatcherService.kt` | Keeps the app alive while the card is visible |
| `ui/MainActivity.kt` | Setup checklist, number checker, contacts list with filters |

## Updating prefixes

Edit the table in `CarrierLookup.kt`. The current table follows the TRC
(Telecommunication Regulator of Cambodia) mobile prefix list. Run the unit tests
in `app/src/test` after changes.

## Known limitations

- **Moved numbers:** the prefix shows which operator *issued* the number. A
  number moved to another operator will show the old one.
- **Lock screen:** Android may draw the card under the lock screen on some One
  UI versions. Test with the phone both locked and unlocked.
- **Samsung Contacts app:** Android can't add icons to Samsung's own contacts
  list, so the logos are shown in this app's Contacts tab. A possible next step:
  a sync account that adds a "Metfone"-style row on each contact's detail page
  in Samsung Contacts.
- **Play Store:** Google reviews call-related permissions and the
  `specialUse` foreground service. Explain the feature clearly in the listing.
