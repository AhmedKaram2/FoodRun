# FoodRun email delivery

FoodRun sends email from **foodruncollection@gmail.com** when the chosen payer uses **Send payment reminder**, when a group owner invites a new email address to join Intrvioo, or when a room creator selects a friend group to notify. Android, iOS and web use the same server commands and durable queue. Other room/order/payment status changes stay in the app and push notifications.

## Friend group invitations

Room creation lists your favourite groups and all groups you joined. Any current member can select a joined group to announce a new room; owners retain group editing rights. Creating a room with a selected group queues an individual email to every address currently in that group. The message includes the creator, room name, restaurant, pickup/delivery details, room code, optional join deadline and a link to join. Registered members also see an invitation in the app. A member-created room also notifies the group owner, using their saved account email when available. The message identifies the actual room creator. Receiving an invitation does not join a room automatically. Creating a room without a selected group sends no group email.

Owners can rename groups and add or remove members. Added users can view their joined groups and leave. Leaving or being removed stops future group invitations and revokes the ability to announce to that group. Pending announcements from a creator who is no longer a member are cancelled before delivery; it does not remove existing room memberships, orders or wallet records. The worker also discards a room invitation if its join window has closed. Replaying a create command after reconnect or restart does not enqueue another invitation to the same address.

## Collection reminder button

The chosen payer can use **Send payment reminder** beside an unpaid member in the room wallet or home/profile wallet. Android and iOS use the shared KMP action; the web app uses the same server command. The button is available after restaurant payment for a placed, fulfilled or automatically archived order with a positive balance and no payment claim awaiting confirmation. The payer cannot remind themselves. Room owners who are not the chosen payer cannot send reminders.

The server uses the recipient's verified address when available. If there is no saved address, the app opens a recipient-email popup and the chosen payer can enter an address for this reminder. The server validates the address and stores it privately for that message; it does not change the member's profile or override an existing verified address. The server calculates the amount from the bill. Each message includes the member's name, remaining amount and currency, collector's name, and both English and Egyptian Arabic text. The wording uses a restrained collection-notice tone with a light FoodRun Collections Committee joke.

There is a durable 24-hour cooldown per person per order. Replaying the same command does not create another message. The command endpoint attempts Gmail delivery immediately and returns **Email reminder sent** only after Gmail accepts it. Temporary failures show **Sending email reminder…** and the apps check delivery status while the worker retries. Permanent failures show an error and release the cooldown so the payer can try again. Before each attempt, the server recalculates the remaining amount and skips reminders if the balance is settled, a payment is awaiting confirmation, the collector changed, the order changed/closed, or the member lost access. Gmail acceptance and inbox delivery require separate verification.

The payer can also use **Record payment received** beside an unpaid person to settle the full balance or record a partial payment without waiting for a member declaration. This works in food rooms after restaurant payment, before or after food arrival, and in payment rooms. It records a confirmed transfer and updates both wallets. Existing pending claims must be confirmed or rejected first.

## Recipient addresses

On Firebase sign-in the server saves the email returned by Firebase account lookup only when it is verified. Client-supplied profile email fields cannot override this delivery address. Addresses are private `email-contact:<uid>` records in the existing durable database, separate from public profiles, room snapshots and people lists. A manually entered reminder address is stored only in the private email job and removed when delivery finishes or expires. Signing out revokes the session but preserves the verified delivery address. Removing a FoodRun account deletes its delivery address; blocked/removed users and revoked room memberships are checked before delivery.

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

Registered recipients also receive an in-app notification and a push to their registered Android, iOS, or browser devices. Group creators included in the recipient list receive a room-ready notification; other members receive a join action. Unknown email recipients receive the email invitation until they register.

Profile preferences independently control email and push across all devices. Delivery workers check preferences again before sending queued jobs. Disabling either channel keeps the in-app inbox available. A payment reminder sent to an email-opted-out recipient returns `REMINDER_NOTIFIED`, preserves the existing cooldown, and sends an in-app update. Older clients omit these preference fields through capability projection.

- A single-recipient message is queued with the originating room transaction. Provider requests happen outside the room lock. Gmail/network errors do not roll back orders or payments.
- The worker reserves each attempt durably and limits attempts in a rolling 24-hour window. It retries temporary errors up to six attempts and expires undelivered messages after 24 hours. Duplicate room commands do not create duplicate email jobs. A timeout after Gmail accepted a message can still cause a duplicate on retry: Gmail does not provide an exactly-once send key.
- Disabled Gmail API or permission errors fail immediately rather than leaving a reminder waiting for retries. Enable Gmail API in the project that owns the sender's OAuth client; valid OAuth credentials alone do not enable the API.
- The default 100-attempt limit leaves room under personal Gmail limits; messages sent manually from the same account also count toward Google's limits. Gmail may reject mail below this cap. [Gmail limits](https://support.google.com/mail/answer/22839)
- Delivery receipts store only a job ID, timestamp and `sent`/`failed` result and are retained for 30 days. `sent` means Gmail accepted the message, not that it reached the recipient's inbox. Logs never include addresses, message content or credentials.
- Messages contain notification text and a link to the app. Group room invitations include the room join code. Session tokens, receipt photos and bank/payment credentials are not included. Actions require opening the app and signing in.
- Render Free can sleep while idle, so pending retries resume when the service wakes. This setup does not promise immediate email while the server is asleep.

After deployment, use controlled test accounts to select a friend group when creating a room and verify each recipient's invitation and join link. Also complete an order, then press **Send payment reminder** for an unpaid balance and verify its inbox delivery. Creating a room without a selected group and selecting, placing, arriving and confirming payment do not create email. Inbox delivery is a separate live check: local tests use fake identities and fake Gmail responses and deliver no email to real users.
