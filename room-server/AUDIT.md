# Group order server audit

Verified 15 September 2026. Scope: `order-domain`, `order-contract`, `room-server`.

## Validation

```sh
./gradlew :room-server:test :order-domain:jvmTest :order-contract:jvmTest :room-server:installDist --console=plain
```

Result: **BUILD SUCCESSFUL**. JUnit XML reports **66 tests, 0 failures, 0 errors, 0 skipped**:

| Suite | Tests |
| --- | ---: |
| Order domain / money / selection | 19 |
| Restaurant import | 7 |
| Protocol serialization | 4 |
| Durable room service | 31 |
| HTTP and WebSocket integration | 5 |

The distributable hub is generated under `room-server/build/install/room-server`.

## Correctness fixes

- Rooms, join codes and approved memberships persist across orders and hub restarts. There is no expiry or automatic purge. Daily participation is separate from membership; a member can skip a meal without leaving the room.
- A spin starts only after every participating orderer acknowledges preparation and remains connected. The winner is selected and persisted once. Repeated commands and hub restarts cannot redraw an accepted spin.
- Money calculations use integer minor units, exact allocation with deterministic remainder handling, currency precision, and bounded totals. Empty carts receive zero fees; a payer choosing no food can still order and pay for the group.
- A provisional discount cannot prevent the first small cart from being entered. Final review rejects a discount larger than the completed food total.
- Partial payments, rejected transfer claims, approved bill changes, overpayment balances, and recipient-confirmed refunds remain explicit. Restoring a bill adjustment to zero still requires approval of the revised bill.
- An order cannot be archived until all balances and pending transfers are resolved, so starting another meal never makes old debt unmanageable.
- Late ordering participants require consent from the selected payer and approval from the organizer. A flag supplied by the organizer cannot impersonate payer consent.
- A room menu can be amended before restaurant placement, using the same restaurant identity and currency. Existing invalid selections must be removed explicitly; valid updates preserve cart lines while resetting submitted carts and quote confirmations. Placed menus are immutable.

## Privacy, persistence and limits

- Pending join requests receive a restricted room projection. Guests and nonparticipating members cannot read current accounts, individual receipts, or cart details. Only the payer receives the combined food order. Organizer status alone does not grant that access.
- Archived receipts are filtered by participation and historical payer role. Later joiners cannot read earlier receipts. History is paginated, never deleted on a timer, and replies stay within the native 4 MiB response budget.
- Mutations validate the largest owner/payer projection before committing. The current-room payload has a 2 MiB budget minus 16 KiB, reserving space for archived receipt pages; excessive content fails with a readable error.
- JSON imports and API requests have byte and depth bounds and reject duplicate keys, including escaped duplicate property names. Menu IDs, option IDs, references, quantities and prices are validated.
- Commands and state commit in one SQLite transaction. Stored room bodies, receipts and replay replies are encrypted using AES-GCM. A missing storage key is an explicit recovery error, never silently replaced.
- Database migration version 2 adds an indexed phase column. Maintenance reads only spinning rooms. Presence heartbeats update memory instead of rewriting the full encrypted room every second.
- Server request errors return an actionable failure; cancellation propagates. Logs default to WARN and omit request bodies, tokens and account contents.

## Test coverage and limits

The service tests include a complete meal and next meal in the same room, a simulated ten-year membership lifetime, restart during a spin, simultaneous retries, stale revisions, independent cart updates, 30 orderers plus 10 guests, excess-capacity refusal, missing/disconnected acknowledgements, late joins, deadlines, malformed menus, private projections, refunds, history pagination, transaction rollback and encrypted storage recovery checks.

Ktor tests verify HTTP-to-WebSocket propagation between separate clients, authentication failure, malformed/deep/oversized requests, unknown protocol versions and durable command retry responses.

Native simulator/emulator UI, certificate pairing, operating-system permissions and real Wi-Fi behavior are validated separately by the mobile application test pass. The hub is designed for one trusted local computer; it is not an internet deployment or automated banking/payment service.
