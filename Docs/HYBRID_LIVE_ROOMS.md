# Hybrid live rooms

Food Run supports two live-room modes with the same REST command and WebSocket event protocol.

| Mode | Best for | Live traffic | Availability |
| --- | --- | --- | --- |
| Nearby hub | People on the same Wi-Fi or office network | Stays on the LAN | While the nearby computer is running |
| Internet API | People joining from different networks | Uses the deployed Food Run server | From any internet connection |

The app remembers the endpoint where each room was created. Nearby and internet rooms can coexist on one device. A room is not silently moved between endpoints because each server is authoritative for its own membership tokens and live state.

## Cloud-use controls

Nearby rooms send commands and live events directly over the local network. Firebase stores the shared identity/profile and receives asynchronous backup records. WebSocket snapshots are event driven: the server sends the current state when a client connects and whenever the room changes. Protocol ping/pong frames keep idle connections healthy without repeatedly transferring full room, history, and receipt-photo payloads. Reconnecting always sends a fresh authoritative snapshot. Presence is refreshed every five seconds while a client is connected.

Live room subscriptions follow the order lifecycle. Orders automatically archive 24 hours after creation, with a fresh window for each new order. Automatic archiving preserves unpaid balances, refunds and pending transfers. These automatically archived rooms retain a live payment subscription in clients opting into `autoArchiveDetails` until settlement completes; food editing and selection stop. Other `ARCHIVED` and `CANCELLED` rooms retain membership and history access through cached HTTP snapshots. `FULFILLED` remains live for settlement. Home updates advertise phase, order number and pending-payment state so a new order or payment can reconnect the relevant room without reconnecting unrelated rooms. A sleeping server catches up when it wakes.

The server omits this new metadata for older strict mobile decoders. It closes completed-room sockets only for clients declaring support, avoiding a reconnect loop in older installations. Those installations receive the home-payload deduplication improvement immediately but need an app update for subscription pruning. Global room events no longer resend an identical home payload and restaurant catalog to every connected user.

## Deploy the internet API

Build the website and server image:

```bash
cd webApp
npm ci
npm run build
cd ..
./gradlew :room-server:test :room-server:installDist
docker build -t foodrun-api .
```

The repository also includes `render.yaml`. Its production configuration runs one server with `FOODRUN_STORAGE=firestore`, Firebase project `devassess-c8833`, and namespace `foodrun-production`. Enter the existing Intrvioo Firebase client API key and the server service-account JSON through the host's secret configuration. Firestore holds the authoritative records; the encrypted SQLite database under `FOODRUN_DATA` is a local query cache in this mode. Nearby hubs default to SQLite and require a persistent data directory with its matching encryption key.

Administration uses the normal Firebase sign-in. Only the verified `1ahmedkaram1@gmail.com` account sees **Admin panel** on the web home screen. The API verifies that identity with Firebase for every admin request and denies disabled accounts. Shared admin passwords and `/admin/login` are no longer supported; old `FOODRUN_ADMIN_USERNAME` and `FOODRUN_ADMIN_PASSWORD` environment variables can be removed. The administrator must have verified their email (Google sign-in provides a verified Google identity).

The normal shared restaurant editor also accepts that verified administrator. Contributors retain ownership when someone else edits their restaurant. A room owner can publish a new restaurant and edit the restaurant or poll choices attached to their own room, using their linked room and account sessions.

Before a food room starts spinning, Ahmed's verified administrator account can long press the room name and enter `5457` to choose an eligible person for the next spin. This entry is hidden on web, Android, and iOS. Save the person, close the option, and start the wheel as usual: the normal animation stops on that person for everyone. Selecting **Random selection** clears the override. The API rechecks Ahmed's identity and room membership on each request; the passcode alone does not grant access. Unlocking lasts ten minutes for that account session and order. The selection survives server restarts but applies once, and returns to random if the selected person becomes ineligible or leaves. It is kept in private server records rather than shared room snapshots or audit entries, and is cleared when a spin starts, a payer is selected directly, or the order is cancelled or replaced.

Run the container behind a managed HTTPS reverse proxy or container platform. Keep `FOODRUN_DATA` private. Persist it when using SQLite storage; do not mount one data directory into multiple server processes.

```text
FOODRUN_TLS_MODE=proxy
FOODRUN_PUBLIC_URL=https://foodrun-api.example.com
FOODRUN_DATA=/data
FOODRUN_PORT=8080
FOODRUN_STORAGE=firestore
FOODRUN_FIRESTORE_NAMESPACE=foodrun-production
FOODRUN_FIRESTORE_CREDENTIALS=<server service-account JSON, provided privately>
FOODRUN_FIREBASE_API_KEY=<existing Intrvioo web API key>
FOODRUN_FIREBASE_PROJECT_ID=devassess-c8833
FOODRUN_WEB_ORIGINS=https://foodrun.example.com
```

On Render, the server automatically reads `PORT` and `RENDER_EXTERNAL_URL`, so those two values do not need to be copied into the blueprint. Other providers can set `FOODRUN_PORT` and `FOODRUN_PUBLIC_URL` explicitly.

Set `VITE_FOODRUN_API_URL=https://foodrun-api.example.com` when building the website. For the combined Docker image, use `docker build --build-arg VITE_FOODRUN_API_URL=https://foodrun-api.example.com -t foodrun-api .`. These public URL build arguments affect the bundled website; setting them only on the running container does not change an already built website. The public proxy must support WebSocket upgrades for `/events`.

## Handoff to another server

- Repository: <https://github.com/AhmedKaram2/FoodRun>, branch `main`.
- Current website: <https://intrvioo.com>.
- Current API: <https://foodrun-api-q6b9.onrender.com>.
- Firebase Authentication and Firestore: [devassess-c8833](https://console.firebase.google.com/project/devassess-c8833/overview).
- Production records: `foodrunServers/foodrun-production/foodrun_rows`; namespace metadata: `foodrunServers/foodrun-production`.
- Client profile documents: `users/{uid}.foodRunProfile`. Retain other fields and the existing interview data.

To move the production API while retaining users, rooms, wallet balances, and device registrations, keep the Firebase project and initialized namespace above. Grant your teammate project access and share the service-account JSON through a private secret channel; credentials are not part of the repository. The push sender uses `FOODRUN_PUSH_CREDENTIALS` if supplied, otherwise `FOODRUN_FIRESTORE_CREDENTIALS`. Its service account needs Firestore access and FCM send permission. iOS also requires APNs credentials configured on the matching Firebase iOS app.

The store supports **one active writer per namespace**. Starting another server against `foodrun-production` claims ownership and fences the existing server. Prepare the image and configuration first, stop the old API during the agreed cutover, and then start one new instance. Leave `FOODRUN_FIRESTORE_BOOTSTRAP` unset for an existing store. For an independent staging server, use a separate Firebase project or namespace with its own explicit initialization; do not connect staging to the production namespace.

Validate the new `/health` endpoint returns HTTP 200 and `X-FoodRun-Storage: firestore`, then check authenticated room access, `/events` upgrades, wallet state, and notification registration. Configure the new website origin in `FOODRUN_WEB_ORIGINS` and Firebase Authentication authorized domains. Rebuild the website with the new API URL; native default URLs are configured separately. Existing installations remember server URLs for their accounts and rooms, so arrange the client/server transition as part of the cutover.

The API surface used by Android, iOS, and web is:

- `POST /command` for identity, room, selection, order, price, payment, and settlement commands.
- `GET /events` with a WebSocket upgrade (`wss://` on public HTTPS) for live room and home updates.
- `GET /health` for platform health checks.
- `POST /notifications` for device registration, inbox reads, and notification actions.
- `GET /catalog` and `POST /catalog/restaurant` for restaurant browsing and authorized contributions.
- `GET /config` for public site configuration.
- `POST /auth/native/{action}` for native authentication flows.
- `/admin/*` for verified administrator operations; see the route definitions in `room-server/src/main/kotlin/com/karim/foodrun/server/Main.kt`.

The request contracts and command kinds are in `order-contract/src/commonMain`; platform clients share these contracts. Supply identity/room tokens through the documented request fields. Administrator routes require Firebase bearer authentication.

The public URL must use a publicly trusted TLS certificate. Nearby hubs keep their generated certificate and fingerprint pairing flow.
