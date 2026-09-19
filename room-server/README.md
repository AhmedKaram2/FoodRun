# Food Run local hub 1.1

Run this hub on one Mac or Windows PC so Android and iOS users can share rooms over the same local network. The computer must stay awake while people use live features. Internet access is not required once Java, the hub and the mobile apps are installed.

## Start on a Mac

1. Install a **Java 17 or newer runtime** if needed. In Terminal, `java -version` must report 17 or newer. A JDK also works.
2. Extract `FoodRun-Hub-1.1.zip` and open Terminal in the extracted `FoodRun-Hub-1.1` folder.
3. Run:

```sh
java -version
export FOODRUN_DATA="$HOME/.foodrun-hub"
./bin/room-server
```

If the executable bit was not preserved during extraction, run `chmod +x ./bin/room-server` once.

Keep the Terminal window open. Stop the hub with **Control-C**. To run it again, use the same command and the same `FOODRUN_DATA` directory. Updating the extracted application folder does not reset rooms stored in that separate directory.

## Start on Windows

1. Install a **Java 17 or newer runtime** and make `java` available on `PATH`, or set `JAVA_HOME` to the Java installation folder.
2. Extract `FoodRun-Hub-1.1.zip`.
3. Open PowerShell in the extracted `FoodRun-Hub-1.1` folder and run:

```powershell
java -version
$env:FOODRUN_DATA = "$env:LOCALAPPDATA\FoodRunHub"
.\bin\room-server.bat
```

Keep PowerShell open. Stop with **Control-C**, then confirm termination if Windows asks. Use the same `FOODRUN_DATA` directory on every launch. The Java application and Windows launcher are included; this release's live operating-system validation was performed on macOS.

## Connect the phones

The hub prints:

- Its HTTPS address, normally `https://192.168.x.x:8443`.
- A `foodrun://pair?...` pairing link.
- Its certificate fingerprint.
- The location of `pairing.png`, a QR image for the phones.

Open `pairing.png` on the computer. In Food Run, discover the hub or scan/paste its pairing information and verify the displayed fingerprint against the computer. Then create a room or enter its six-digit join code. The organizer approves new members. A room code identifies the room; it is separate from trusting the hub certificate.

Both phones and the computer need a reachable local network. Permit the Java/hub process to receive **TCP port 8443** on the trusted private network. Bonjour discovery uses **UDP 5353**; scanning the QR or entering the address can replace discovery. Guest-network device isolation, some VPNs and a blocked firewall can prevent actual connections even with the correct QR code.

If the printed address belongs to the wrong adapter, select the computer's Wi-Fi/LAN IPv4 address explicitly before starting. For example:

```sh
export FOODRUN_HOST="192.168.1.20"
export FOODRUN_PORT="8443"
./bin/room-server
```

```powershell
$env:FOODRUN_HOST = "192.168.1.20"
$env:FOODRUN_PORT = "8443"
.\bin\room-server.bat
```

Use your computer's actual address. `127.0.0.1` is for that computer itself, not physical phones. A DHCP reservation for the hub computer makes its address stable. If its address changes, use its current pairing information. Do not replace the saved certificate/key merely to change an address.

## Accounts and internet rooms

Registration, profiles, registered-person invitations, and Firebase backup use the existing Intrvioo Firebase project. Configure the hub without putting credentials in source control:

```sh
export FOODRUN_FIREBASE_API_KEY="<Intrvioo web API key>"
export FOODRUN_FIREBASE_PROJECT_ID="<Intrvioo project id>"
./bin/room-server
```

The API key identifies the Firebase client project; Firebase user tokens and Firestore rules authorize profile and backup access. Deploy the `webApp/firestore.rules` update before enabling hub backup.

For rooms reachable from any network, deploy the Docker image behind a public HTTPS reverse proxy and set `FOODRUN_TLS_MODE=proxy`, `FOODRUN_DATA`, and `FOODRUN_WEB_ORIGINS`. Set `FOODRUN_PUBLIC_URL` and `FOODRUN_PORT` on a generic host; Render supplies equivalent `RENDER_EXTERNAL_URL` and `PORT` values automatically. Keep `FOODRUN_DATA` on a persistent volume. The repository's `render.yaml` is ready to provision this layout. See the [hybrid live-room guide](../Docs/HYBRID_LIVE_ROOMS.md) for the full configuration.

## Permanent rooms and daily orders

**Rooms, join codes and memberships have no daily expiry or automatic expiry timer.** A saved member session resumes the same room after an app or hub restart. Keep the hub data directory and the phone's app data to preserve that access.

The organizer starts **Next order** inside the existing room after the previous meal is cancelled or fully completed and settled. The room keeps its identity, approved members and past receipts. Members choose to participate or skip the new meal; skipping does not remove their membership. The creator participates by default when starting the next order.

A meal cannot be archived while a reimbursement/refund balance or unconfirmed transfer remains. Resolve those balances first, then archive and start the next order. There is no automatic financial settlement.

Removing a member explicitly revokes that membership. Clearing a phone's app data, reinstalling the app, losing its secure keys, deleting hub data, or switching to a different hub data directory can require recovery/rejoining. These are separate from room expiry.

## Offline receipts

Open the current room and receipts while connected so they are saved to the phone. The payer should also open the combined food order and restaurant contact before leaving Wi-Fi.

Downloaded receipts and cached order information remain available offline after restarting the app. Their last synchronization time and revision matter: they cannot show new payments or bill changes while disconnected. Live spin, ordering and payment updates resume when the phone can reach the room's nearby hub or public internet API.

## Data, backups and upgrades

Without `FOODRUN_DATA`, Java uses `.foodrun-hub` inside its user home directory. The explicit examples above are preferable for remembering the chosen location. Use one stable directory and one hub process for that directory.

The directory contains encrypted room records, member sessions and retained command/receipt history. Important files include:

| File | Purpose |
| --- | --- |
| `rooms.sqlite` | Database of rooms, membership, orders and command records |
| `rooms.sqlite-wal`, `rooms.sqlite-shm`, if present | SQLite companion files; include them when copying the directory |
| `storage.key` | Key needed to decrypt stored room/receipt data |
| `hub.p12` | Hub TLS certificate and private key; phones trust this identity |
| `tls.password` | Password needed to open `hub.p12` |
| `pairing.txt`, `pairing.png` | Current connection information |

**Back up the entire data directory together, including the keys and certificate.** A database backup without `storage.key` cannot restore the encrypted data. Preserve `hub.p12` and `tls.password` together so restored phones recognize the same trusted hub. Anyone who obtains the complete backup can read its contents, so keep it in storage you control with appropriate access protection.

To back up safely:

1. Stop the hub with Control-C and wait for it to exit.
2. Copy the entire chosen `FOODRUN_DATA` directory to your backup location, keeping the complete set of files from that moment.
3. Start the hub using the original directory.

To restore, stop the hub, restore the complete matching directory, then start with `FOODRUN_DATA` pointing to it. Do not mix a database from one backup with keys from another. Restoring an older backup also restores its older order/payment state; reconcile any activity after that backup before continuing.

For an upgrade, back up first, stop the old hub, extract the new application version, and start it using the existing data directory. Database migrations run on startup. Keep your backup before attempting a downgrade; an older binary may not support a newer database.

Restaurants, saved receiving accounts and downloaded receipts on phones use encrypted device storage and are excluded from routine cloud backup. Export restaurant JSON or share receipts from the app when you need separate copies. Never include private hub data when sharing the application zip.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Java not found or too old | Install/use Java 17+ and check `java -version` and `JAVA_HOME`. |
| Port already in use | Stop the other hub instance, or choose another `FOODRUN_PORT` and pair with that port. |
| Hub discovered but unreachable | Check the printed LAN address, computer sleep, TCP firewall access, VPN and guest-network isolation. |
| Hub not discovered | Use its QR/pairing link or manual address; check local-network permission in the phone's settings. |
| Fingerprint mismatch | Verify the hub's printed fingerprint in person. Check that the original data directory/certificate is still being used before pairing again. |
| Room seems missing after restart | Confirm `FOODRUN_DATA` points to the original directory and that the phone is connecting to the same hub. |
| Cannot archive a meal | Resolve every payment/refund balance and pending transfer; a reason alone does not bypass settlement. |
| Missing/invalid storage key | Restore the matching complete backup; do not generate a replacement key for an existing database. |
| Receipt is stale offline | Reconnect to the local hub, open the room, and allow it to synchronize. |

## Build from source

From the Food Run repository root:

```sh
./gradlew :room-server:test :order-domain:jvmTest :order-contract:jvmTest :room-server:installDist
```

Run `room-server/build/install/room-server/bin/room-server` on macOS/Linux, or the neighboring `.bat` launcher on Windows. See `AUDIT.md` for the audited business rules and server test coverage.
