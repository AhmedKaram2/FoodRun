# Paid wheel options

Approved participating members can request **Please don’t pick me** before selection in a food room. The room owner approves the request first. The member then pays the owner outside FoodRun and marks the payment sent; only the receiving owner can confirm the fixed amount and activate the option.

| Option | Fee | Effect for the current order |
| --- | --- | --- |
| Exclude me from selection | AED 10 | Remove the member from wheel and direct selection candidates. |
| Reduce my chance by 50% | AED 5 | Halve the member's actual normalized wheel probability, relative to the usual weights among eligible, nonexcluded candidates. |

The member remains part of the meal and pays their normal food share. The fee is stored separately from food receipts and is not processed by a payment gateway. Owners cannot approve their own paid requests. Request IDs, order numbers and room revisions prevent duplicate or stale approval and payment changes.

Approval reserves a feasible selection: at least one eligible member must remain with their normal chance. Approved payments must be confirmed or declined before selection starts. Active reductions require the shared random wheel; direct selection and hidden forced-winner controls cannot bypass them. Confirmed exclusions also apply to direct selection. Existing last-chosen weighting remains the baseline.

The next order clears these options; the archived order keeps its payment records. Payment references are visible only to the requester and owner. Updated clients opt into `wheelProtectionDetails`; older clients receive compatible snapshots and omit weights they cannot decode.

The website and shared Android/iOS flow support requesting, approval, payment declaration, receipt confirmation, rejection and cancellation before payment. Domain tests verify exact probabilities, while server and native flow tests check authorization, fixed amounts, replay, privacy and order rollover.
