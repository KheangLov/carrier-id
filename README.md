# Carrier ID (Cambodia)

Shows the caller's mobile operator (Smart, Cellcard, Metfone, Seatel, CooTel, qb)
as a floating card over the Samsung incoming-call screen, plus a contacts list
with a carrier logo next to every number.

## Open & run

1. Open this folder in **Android Studio** (Ladybug or newer) → let Gradle sync.
2. Plug in your Samsung phone with USB debugging on → **Run ▶**.
3. In the app's **Settings** tab, enable all 4 setup steps:
   1. **Caller ID & spam app** – Android then sends every incoming number to the app.
   2. **Display over other apps** – lets the card appear above the call screen.
   3. **Contacts & phone** – contacts list; also needed so calls from *saved*
      contacts reach the app, and to hide the card when the call ends.
   4. **Battery** – set the app to *Unrestricted*. On Samsung also add it to
      *Settings → Battery → Background usage limits → Never sleeping apps*.
4. Type a number in **Check a number** and tap **Preview call overlay** to test
   without a real call. Then have someone call you.

Minimum Android version: 10 (API 29). Target: Android 15 (API 35).

## Features

**Spam & blocking** (Settings → Spam & blocking). Everything is checked on the
phone; no number is uploaded.
- Red warning on the call card with the reason (reported by you, faked caller
  ID, scam-prone country code, hidden number, repeat ring-and-hang-up, abroad).
- Automatic blocking you choose: spam & scam codes (on by default), hidden
  numbers, international numbers, repeat callers, or everyone not in contacts.
- Your own block list, including prefixes (`+234*`, `023*`). Contacts are never
  blocked unless you add them.
- Block / report from the call card or by tapping a call in Recents.
- Blocked calls are rejected silently, listed in the app, with an optional
  quiet notification. Needs the *Caller ID & spam app* setup step.

**Messages** (Settings → Messages)
- Quick replies on the call card: one tap texts the caller and declines the call.
- Auto-reply: texts people back after a missed or declined call (contacts only
  by default, Cambodian mobiles only, once per 30 min, never to spam).

**Notes & live text** (Settings → Notes & live text)
- After-call card: add a note, send a message or block when a call ends.
- Notes show in Recents and on the call card the next time that person calls.
- Live text: captions during a call (Khmer or English) using Google speech
  recognition, saved into the call note. Android doesn't give apps the call
  audio, so it listens with the microphone: your voice, and the other side with
  the speaker on. Some phones mute the mic for other apps during calls.

## Adding the real logos

**In the app (easiest):** Settings → *Networks & logos* → tap a network → pick
the logo image from your photos. It is used on the call card, in Recents and
in Contacts. Tap *Remove* to go back to the coloured letter badge.

**Bundled with the app:** put square PNGs named `logo_smart.png`,
`logo_cellcard.png`, `logo_metfone.png`, `logo_seatel.png`, `logo_cootel.png`,
`logo_qb.png` in `app/src/main/res/drawable/`. A logo picked in the app wins
over a bundled one.

Logos are trademarks of each operator. Personal use is fine; for a Play Store
release, get permission or keep the coloured badges.

## How it works

| File | Role |
|------|------|
| `carrier/CarrierLookup.kt` | Prefix table + number normalising (`+855…`, `00855…`, `0…`) |
| `call/CallerIdScreeningService.kt` | Receives incoming calls, blocks the ones the spam rules flag |
| `call/CallerOverlay.kt` | The floating card: ringing / in-call / after-call (drag up/down to move) |
| `call/CallSession.kt` | Follows each call (ringing → answered → ended) for auto-reply and the after-call card |
| `call/CallActions.kt` | SMS sending, declining calls, auto-reply |
| `spam/SpamRules.kt` | Offline spam rules and block-list matching (unit-tested) |
| `spam/BlockStore.kt` | Block list, spam reports, blocked-call log |
| `notes/CallNotes.kt` | Call notes and live-text transcripts |
| `ui/CallNoteActivity.kt`, `ui/LiveTextActivity.kt` | Panels that open over the call screen |
| `call/CallWatcherService.kt` | Keeps the app alive while the card is visible |
| `call/IncomingCallReceiver.kt` | Backup detector (system PHONE_STATE broadcast) so the card shows even if Android skips the screening service |
| `ui/RecentsScreen.kt` | Call history with each caller's network |
| `ui/MainActivity.kt` | Tabs, settings checklist, number checker, contacts list, logo picker |

## Release builds & signing

GitHub Actions builds an optimised **release** APK (R8 code + resource shrinking,
Compose startup profiles). To sign every build with the same key, so new
versions install over old ones, add two repository secrets under
*Settings → Secrets and variables → Actions*:

| Secret | Value |
|--------|-------|
| `CARRIERID_KEYSTORE_B64` | the keystore file, base64-encoded |
| `CARRIERID_KEYSTORE_PASSWORD` | its password (key alias `carrierid`) |

Never commit the keystore: this repository is public. Without the secrets the
build still works but uses a temporary key.

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
