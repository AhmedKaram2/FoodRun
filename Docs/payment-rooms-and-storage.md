# Payment rooms

The web home page offers **Payment room** for a bill the creator already paid. Select existing discoverable users, enter order details and the final receipt total, optionally attach a receipt photo, then split equally or enter individual shares. Zero shares and partial payments already received are supported. All selected members immediately see their private share in the existing wallet.

The payer can correct shares, replace the receipt photo and record further payments received. Members can declare transfers using the existing confirmation flow. Corrections preserve prior payments, so overpayments become refund balances. Completing the room requires all balances and pending transfers to be settled. Creation is atomic and idempotent; other users cannot join by guessing its room code. Tax, fees and discounts are included in the entered final shares.

The new payment-room metadata requires a current client. Normal food rooms omit this metadata and retain their existing wire format. Mobile binaries have not been distributed with this web feature.

# Production storage blocker identified on 2026-09-21

Live Render service `srv-dan8bgmk1f9s73fqn680` is on the Free instance plan and the workspace disk API returned no disks. The repository's `render.yaml` specifies a paid instance and disk, but those settings were never applied to the live service. Room memberships, carts, transfers and wallets are stored in the encrypted SQLite database; ephemeral filesystem data is lost at redeployment. A healthy HTTP endpoint does not prove data persistence.

Do not push an auto-deploying branch or redeploy until durable storage is configured and current data is preserved. Run `python3 Scripts/verify-render-storage.py srv-dan8bgmk1f9s73fqn680` before production publication. This is a read-only check and fails closed on the current configuration.

The proposed infrastructure is the existing `render.yaml`: paid 0.5c-512mb compute and a 1 GB disk mounted at `/var/lib/foodrun`. The published base prices checked on 2026-09-21 are $7/month compute plus $0.25/month disk, excluding usage/taxes. This introduces recurring charges and requires the owner's approval. Adding a disk triggers deployment; do not assume it migrates files from the ephemeral instance.

Recovery is separate from prevention. Preserve any available live state, complete SQLite backup plus matching `storage.key`, browser downloaded receipts and optional Firebase room backups before changing infrastructure. Do not recreate balances from incomplete receipts or restore unverified user identities. The Firebase CLI account available during this investigation returned HTTP 403 for project `devassess-c8833`; backup availability and profile recovery are not verified. Free-plan SSH access is unavailable. No production storage change or restoration has been performed.

Sources: https://render.com/docs/free, https://render.com/docs/disks, https://render.com/pricing
