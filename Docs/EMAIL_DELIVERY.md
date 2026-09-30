# FoodRun email delivery

FoodRun can send transactional room invitations and existing order/payment notifications from **foodruncollection@gmail.com**. The backend handles Android, iOS and web events using the same queue. This change does not add a bulk-mail composer or send marketing messages.

## Collection reminder button

The chosen payer can use **Send payment reminder** beside an unpaid member in the room wallet or home/profile wallet. Android and iOS use the shared KMP action; the web app uses the same server command. The button is available only after restaurant payment, for an active order with a positive balance and no payment claim awaiting confirmation. The payer cannot remind themselves. Room owners who are not the chosen payer cannot send reminders.

The server resolves the recipient's verified address and calculates the amount from the bill; clients cannot supply an arbitrary email address or amount. Each message includes the member's name, remaining amount and currency, collector's name, and both English and Egyptian Arabic text. The wording uses a restrained collection-notice tone with a light FoodRun Collections Committee joke.

There is a durable 24-hour cooldown per person per order. Replaying the same command does not create another message. Before each delivery attempt, the worker recalculates the remaining amount and skips reminders if the balance is settled, a payment is awaiting confirmation, the collector changed, the order changed/closed, or the member lost access. The UI says **Email reminder queued**, not delivered. Gmail acceptance and inbox delivery require separate verification.

## Recipient addresses

On Firebase sign-in the server saves the email returned by Firebase account lookup only when it is verified. Client-supplied email fields cannot override the delivery address. Addresses are private `email-contact:<uid>` records in the existing durable database, separate from public profiles, room snapshots and people lists. Signing out revokes the session but preserves the delivery address. Removing a FoodRun account deletes its delivery address; blocked/removed users and revoked room memberships are checked before delivery.

Previously registered users acquire this record on their **next sign-in** after deployment. This does not import all users from the shared Intrvioo Firebase project. No additional Gmail permissions are requested from FoodRun users.

### Require a fresh FoodRun login

Set `FOODRUN_AUTH_VALID_AFTER` on Render to a fixed Unix timestamp in **seconds** and deploy. Before serving requests, the server deletes existing account and room sessions in bounded durable batches, then records completion so ordinary restarts do not repeat the reset. Memberships, profiles, orders, bills, and command deduplication records remain intact. Signing in creates replacement room tokens for the same memberships.

Keep this value configured: verified Firebase tokens must have `auth_time` at or after the cutoff; refreshing an older token does not bypass the requirement. This restriction applies only to FoodRun, without revoking access to other applications in the shared Firebase project. The website signs out when it receives `REAUTH_REQUIRED`; older mobile clients can use Sign out, then sign in again. A future reset uses a newer cutoff.

Saved profile phone numbers are repaired on sign-in when recognizable as UAE local numbers. If a saved number cannot be normalized, sign-in succeeds with an empty phone so the user can correct it in their profile. Profile saves still validate the number.

For website releases, include the existing `zai` and `ziina` Netlify functions explicitly. They belong to the Intrvioo deployment and are not stored in this repository. Deploying only `dist` without the functions removes those endpoints; verify them on a preview before promoting it.

## Why Gmail API

[Render Free blocks SMTP ports 25, 465 and 587](https://render.com/docs/free). Delivery uses the [Gmail API over HTTPS](https://developers.google.com/workspace/gmail/api/guides/sending) and an OAuth refresh token for the sender. A Gmail password or app password cannot enable this integration. The existing Firebase service account cannot impersonate a personal Gmail mailbox.

## Authorize the sender once

1. In Google Cloud create a separate email-sender project (or a separate OAuth client in a project you control), enable **Gmail API**, and configure Google Auth Platform branding/audience. Keep this sender setup separate from the existing Firebase sign-in clients.
2. Create an OAuth client of type **Desktop app**. Download its JSON to `.local/gmail-oauth-client.json` in the repository. `.local/` is ignored by Git; keep this file private.

   An existing **Web application** client also works: register **`http://127.0.0.1:8765/`** under its **Authorized redirect URIs** (not JavaScript origins), save, then use its downloaded JSON with the helper. The helper uses this fixed port for Web clients; `--web-port` changes it if needed, but the registered URI must match exactly, including the trailing slash.
3. For initial testing, add `foodruncollection@gmail.com` as a test user. For unattended use, complete the applicable Google publishing requirements and switch the OAuth app to **In production** before generating the final token. Tokens for external apps in **Testing expire after seven days** with Gmail scopes. [Google token lifecycle](https://developers.google.com/identity/protocols/oauth2#expiration) and [verification requirements/exceptions](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification) apply.
4. Run from the repository root:

   ```sh
   python3 Scripts/authorize-gmail.py
   ```

   Sign in as **foodruncollection@gmail.com** and grant send permission. The helper uses a local callback, state validation and PKCE, verifies the exact sender, and writes `.local/gmail.env` with owner-only permissions. It requests `gmail.send` plus basic email identity, not inbox-reading access. It never sends an email or prints tokens. Use `--output .local/gmail-new.env` when reauthorizing so existing credentials are preserved.

5. Import `.local/gmail.env` into the **foodrun-api server's Render environment settings**. It contains `FOODRUN_GMAIL_CLIENT_ID`, `FOODRUN_GMAIL_CLIENT_SECRET`, and `FOODRUN_GMAIL_REFRESH_TOKEN`. Never put these values in `VITE_*`, mobile configuration, source control or chat.
6. Set the following server variables and deploy the updated server:

   | Variable | Value |
   | --- | --- |
   | `FOODRUN_EMAIL_ENABLED` | `true` |
   | `FOODRUN_EMAIL_DAILY_LIMIT` | `100` initially; supported range 1–450 |
   | `FOODRUN_EMAIL_APP_URL` | `https://intrvioo.com` |
   | `FOODRUN_EMAIL_API_URL` | `https://foodrun-api-q6b9.onrender.com` |

   Sending is disabled when `FOODRUN_EMAIL_ENABLED` is absent or not `true`. Explicitly enabling it with incomplete credentials fails configuration validation. To pause email, set it to `false` and redeploy. New events while disabled are not queued for later bulk delivery.

## Delivery behavior and validation

- A single-recipient message is queued with the originating room transaction. Provider requests happen outside the room lock. Gmail/network errors do not roll back orders or payments.
- The worker reserves each attempt durably and limits attempts in a rolling 24-hour window. It retries temporary errors up to six attempts and expires undelivered messages after 24 hours. Duplicate room commands do not create duplicate email jobs. A timeout after Gmail accepted a message can still cause a duplicate on retry: Gmail does not provide an exactly-once send key.
- The default 100-attempt limit leaves room under personal Gmail limits; messages sent manually from the same account also count toward Google's limits. Gmail may reject mail below this cap. [Gmail limits](https://support.google.com/mail/answer/22839)
- Delivery receipts store only a job ID, timestamp and `sent`/`failed` result and are retained for 30 days. `sent` means Gmail accepted the message, not that it reached the recipient's inbox. Logs never include addresses, message content or credentials.
- Messages contain notification text and a link to the app. Session tokens, join codes, receipt photos and bank/payment credentials are not included. Actions require opening the app and signing in.
- Render Free can sleep while idle, so pending retries resume when the service wakes. This setup does not promise immediate email while the server is asleep.

After deployment, sign in again with two controlled test accounts, invite one to a test room, and verify the email in its inbox. Repeat a controlled order/payment event and confirm its recipient and language. This is a separate live check: local tests use fake identities and fake Gmail responses and deliver no email to real users.
