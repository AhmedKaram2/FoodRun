# Food Run web

This is the browser client for Food Run. It reuses the existing Intrvioo Firebase Authentication project and stores the Food Run profile in `users/{uid}.foodRunProfile`, leaving interview documents and the other user fields intact.

## Run locally

1. Copy `.env.example` to `.env` and keep the existing Intrvioo Firebase values.
2. Set `VITE_FOODRUN_API_URL` to the deployed Food Run API for internet rooms. `VITE_FOODRUN_HUB_URL` can point to a nearby hub during local development.
3. Run `npm install` and `npm run dev`.

The web client connects to `POST /command` and `GET /events` on the selected Food Run server. A nearby hub keeps live updates on the LAN; a public HTTPS API lets users join from any network.

New rooms default to delivery. The website and restaurant order list default to Arabic, with English available and saved language choices respected. After selection, the room creator or selected person approves new join requests. The Payment section has inline Edit and Add controls for up to 10 receiving methods; members choose a shared method when marking payments sent.

Before enabling cloud room backup, deploy the included `firestore.rules` to the existing Firebase project. The added `foodrunHubs` rule only allows the owning authenticated user to read and write their backup records.
