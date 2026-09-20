# Hybrid live rooms

Food Run supports two live-room modes with the same REST command and WebSocket event protocol.

| Mode | Best for | Live traffic | Availability |
| --- | --- | --- | --- |
| Nearby hub | People on the same Wi-Fi or office network | Stays on the LAN | While the nearby computer is running |
| Internet API | People joining from different networks | Uses the deployed Food Run server | From any internet connection |

The app remembers the endpoint where each room was created. Nearby and internet rooms can coexist on one device. A room is not silently moved between endpoints because each server is authoritative for its own membership tokens and live state.

## Cloud-use controls

Nearby rooms send commands and live events directly over the local network. Firebase stores the shared identity/profile and receives asynchronous backup records. WebSocket snapshots are event driven: the server sends state when the room changes, plus a 30-second recovery heartbeat. Presence is refreshed every five seconds while a client is connected.

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

The repository also includes `render.yaml`. In Render, create a Blueprint from this repository and enter the existing Intrvioo web API key for the prompted `FOODRUN_FIREBASE_API_KEY`. The blueprint provisions one Frankfurt web-service instance and a persistent 1 GB disk. A paid Render instance is required for the disk; do not remove it because the server stores the authoritative room and order state in SQLite.

The same Blueprint asks for `FOODRUN_ADMIN_PASSWORD`. Store the admin password as a Render secret and set `FOODRUN_ADMIN_USERNAME` to the intended administrator. The password is read only by the API process; the browser sends it to `/admin/login` over HTTPS and receives an expiring server-side session token.

Run the container behind a managed HTTPS reverse proxy or container platform. Persist `FOODRUN_DATA`; it contains room state, memberships, and encrypted account data.

```text
FOODRUN_TLS_MODE=proxy
FOODRUN_PUBLIC_URL=https://foodrun-api.example.com
FOODRUN_DATA=/data
FOODRUN_FIREBASE_API_KEY=<existing Intrvioo web API key>
FOODRUN_FIREBASE_PROJECT_ID=<existing Intrvioo project id>
FOODRUN_WEB_ORIGINS=https://foodrun.example.com
```

On Render, the server automatically reads `PORT` and `RENDER_EXTERNAL_URL`, so those two values do not need to be copied into the blueprint. Other providers can set `FOODRUN_PORT` and `FOODRUN_PUBLIC_URL` explicitly.

Set `VITE_FOODRUN_API_URL=https://foodrun-api.example.com` when building the website. The public proxy must support WebSocket upgrades for `/events`.

The API surface used by Android, iOS, and web is:

- `POST /command` for identity, room, selection, order, price, payment, and settlement commands.
- `GET /events` for live event-driven room and home updates.
- `GET /health` for platform health checks.

The public URL must use a publicly trusted TLS certificate. Nearby hubs keep their generated certificate and fingerprint pairing flow.
