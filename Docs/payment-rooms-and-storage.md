# Payment rooms

The web home page offers **Payment room** for a bill the creator already paid. Select existing discoverable users, enter order details and the final receipt total, optionally attach a receipt photo, then split equally or enter individual shares. Zero shares and partial payments already received are supported. All selected members immediately see their private share in the existing wallet.

The payer can correct shares, replace the receipt photo and record further payments received. Members can declare transfers using the existing confirmation flow. Corrections preserve prior payments, so overpayments become refund balances. Completing the room requires all balances and pending transfers to be settled. Creation is atomic and idempotent; other users cannot join by guessing its room code. Tax, fees and discounts are included in the entered final shares.

The new payment-room metadata requires a current client. Normal food rooms omit this metadata and retain their existing wire format. Mobile binaries have not been distributed with this web feature.

# Firestore storage on the free server

The Free Render instance uses `FOODRUN_STORAGE=firestore`. Firestore's default Standard database is authoritative; encrypted SQLite in `FOODRUN_DATA` is a disposable query cache. Rooms, history, sessions, memberships, restrictions, settings and command retry responses are saved atomically before acknowledgment. Startup restores the cache before opening HTTP. Desktop hubs retain SQLite by default.

Changed rows and a revision document are committed under `foodrunServers/{namespace}`. Startup claims a revision so an old server cannot overwrite a new server's data. Ambiguous responses are checked using a unique commit ID. Unconfirmed writes invalidate the cache and return a retryable unavailable response; health reports 503 until a restart reloads durable data. Failed cloud writes never fall back to ephemeral-only storage.

Startup claims the writer using only the metadata document, then restores bounded pages outside the transaction. Transaction retries no longer repeat a full collection scan. Stores with at least 128 rows build a compressed restore cache under `foodrun_restore`, with 64 buckets and at most 128 document parts / 32 MiB. Once published, subsequent startups read its manifest and named parts instead of every canonical row. The existing format-1 `foodrun_rows` remain authoritative and continue to receive every change. The restore cache and its revision are updated atomically with those rows; an older writer removes the cache manifest on its next commit, and the next startup safely rebuilds it. Partial unpublished cache writes are never used. Missing advertised parts fail closed.

All restore-cache writes count against the existing daily write budget. If an ordinary change cannot fit the additional cache writes or transaction size, it saves only the canonical rows and invalidates the optional cache. No payment or account write is dropped to preserve the cache. Reused cache slots avoid creating a new collection generation on every restart.

Use a dedicated service account in the secret `FOODRUN_FIRESTORE_CREDENTIALS`, matching `FOODRUN_FIREBASE_PROJECT_ID`. Keep `FOODRUN_FIRESTORE_NAMESPACE=foodrun-production` stable. Missing storage fails startup unless `FOODRUN_FIRESTORE_BOOTSTRAP=true` explicitly permits initialization; disable that flag after bootstrap. Never roll back production to a SQLite-only build. Preserve the Firestore adapter when rolling forward with a fix.

Presence and idle maintenance use the cache without polling Firestore. One metadata listener checks writer ownership; a deployment takeover immediately disables stale cached reads and makes old subscriptions reconnect. Commits still verify ownership and revision atomically. Browser subscriptions use small application heartbeats and reopen on network recovery or foreground return. Records are compressed and chunked below the document limit. Exclude `foodrun_rows.payload` from indexes. FoodRun caps combined mutations at 12,000/day (Pacific time), compressed data plus estimated overhead at 600 MiB, and stored documents at 15,000. Individual atomic changes are capped at 450 operations and 7 MB. These reserve allowance for Intrvioo but are not a project-wide guarantee; startup reads documents again. Keep Firebase billing disabled so exceeding shared free quotas pauses requests instead of generating charges. Monitor usage in Firebase.

Firestore's free daily read allowance resets around midnight Pacific time. A quota-exhausted project cannot be recovered by repeatedly restarting the server; the first restore still needs available quota before it can publish the compact cache. Keep the existing namespace and data while waiting for the reset. The web shows Home for a restored signed-in account while its server connection is pending, with account actions disabled until current data arrives.

Existing `users/{uid}.foodRunProfile` and interview fields remain intact. Production rules deny browser/mobile access to the server namespace; the backend uses IAM. Do not replace production rules wholesale with the older repository rules, because production has additional Intrvioo rules.

Run `python3 Scripts/verify-render-storage.py srv-dan8bgmk1f9s73fqn680` before publication. It checks configuration without printing secrets. After deployment verify `/health` and `X-FoodRun-Storage: firestore`, then verify recovery after a restart. API and Netlify deployment are separate.

## Recovery boundary

On 2026-09-21 the Free Render service had no disk; deployments could lose the old SQLite database and key. The feature release was published at the owner's request before this migration. Firebase access then confirmed 17 saved FoodRun profiles and no legacy room backup subcollections. The owner confirmed no new entries needed preservation before cutover. Migration prevents future loss; it cannot reconstruct missing rooms, memberships or financial balances from profile documents. Never manufacture balances from incomplete receipts.

## Verification

Start a local Firestore emulator with project `demo-foodrun`, then run `FIRESTORE_EMULATOR_HOST=127.0.0.1:<port> ./gradlew :room-server:test`. Tests cover full local disk loss, meal and payment-room wallets, account sessions and memberships, duplicate payment retries, chunked receipts, old-writer fencing, quota refusal, deletion and nested rollback. Fault tests cover failure before and after the cloud commit. Production configuration rejects an emulator host.

Sources: https://firebase.google.com/docs/firestore/quotas, https://firebase.google.com/docs/firestore/use-rest-api, https://render.com/docs/free
