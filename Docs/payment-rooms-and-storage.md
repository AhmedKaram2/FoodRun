# Payment rooms

The web home page offers **Payment room** for a bill the creator already paid. Select existing discoverable users, enter order details and the final receipt total, optionally attach a receipt photo, then split equally or enter individual shares. Zero shares and partial payments already received are supported. All selected members immediately see their private share in the existing wallet.

The payer can correct shares, replace the receipt photo and record further payments received. Members can declare transfers using the existing confirmation flow. Corrections preserve prior payments, so overpayments become refund balances. Completing the room requires all balances and pending transfers to be settled. Creation is atomic and idempotent; other users cannot join by guessing its room code. Tax, fees and discounts are included in the entered final shares.

The new payment-room metadata requires a current client. Normal food rooms omit this metadata and retain their existing wire format. Mobile binaries have not been distributed with this web feature.

# Firestore storage on the free server

The Free Render instance uses `FOODRUN_STORAGE=firestore`. Firestore's default Standard database is authoritative; encrypted SQLite in `FOODRUN_DATA` is a disposable query cache. Rooms, history, sessions, memberships, restrictions, settings and command retry responses are saved atomically before acknowledgment. Startup restores the cache before opening HTTP. Desktop hubs retain SQLite by default.

Changed rows and a revision document are committed under `foodrunServers/{namespace}`. Startup claims a revision so an old server cannot overwrite a new server's data. Ambiguous responses are checked using a unique commit ID. Unconfirmed writes invalidate the cache and return a retryable unavailable response; health reports 503 until a restart reloads durable data. Failed cloud writes never fall back to ephemeral-only storage.

Use a dedicated service account in the secret `FOODRUN_FIRESTORE_CREDENTIALS`, matching `FOODRUN_FIREBASE_PROJECT_ID`. Keep `FOODRUN_FIRESTORE_NAMESPACE=foodrun-production` stable. Missing storage fails startup unless `FOODRUN_FIRESTORE_BOOTSTRAP=true` explicitly permits initialization; disable that flag after bootstrap. Never roll back production to a SQLite-only build. Preserve the Firestore adapter when rolling forward with a fix.

Polling, presence and idle maintenance use the cache without Firestore operations. Records are compressed and chunked below the document limit. Exclude `foodrun_rows.payload` from indexes. FoodRun caps combined mutations at 12,000/day (Pacific time), compressed data plus estimated overhead at 600 MiB, and stored documents at 15,000. Individual atomic changes are capped at 450 operations and 7 MB. These reserve allowance for Intrvioo but are not a project-wide guarantee; startup reads documents again. Keep Firebase billing disabled so exceeding shared free quotas pauses requests instead of generating charges. Monitor usage in Firebase.

Existing `users/{uid}.foodRunProfile` and interview fields remain intact. Production rules deny browser/mobile access to the server namespace; the backend uses IAM. Do not replace production rules wholesale with the older repository rules, because production has additional Intrvioo rules.

Run `python3 Scripts/verify-render-storage.py srv-dan8bgmk1f9s73fqn680` before publication. It checks configuration without printing secrets. After deployment verify `/health` and `X-FoodRun-Storage: firestore`, then verify recovery after a restart. API and Netlify deployment are separate.

## Recovery boundary

On 2026-09-21 the Free Render service had no disk; deployments could lose the old SQLite database and key. The feature release was published at the owner's request before this migration. Firebase access then confirmed 17 saved FoodRun profiles and no legacy room backup subcollections. The owner confirmed no new entries needed preservation before cutover. Migration prevents future loss; it cannot reconstruct missing rooms, memberships or financial balances from profile documents. Never manufacture balances from incomplete receipts.

## Verification

Start a local Firestore emulator with project `demo-foodrun`, then run `FIRESTORE_EMULATOR_HOST=127.0.0.1:<port> ./gradlew :room-server:test`. Tests cover full local disk loss, meal and payment-room wallets, account sessions and memberships, duplicate payment retries, chunked receipts, old-writer fencing, quota refusal, deletion and nested rollback. Fault tests cover failure before and after the cloud commit. Production configuration rejects an emulator host.

Sources: https://firebase.google.com/docs/firestore/quotas, https://firebase.google.com/docs/firestore/use-rest-api, https://render.com/docs/free
