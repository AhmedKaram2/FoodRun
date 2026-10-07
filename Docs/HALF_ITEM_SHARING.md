# Half-item sharing

Add food to your cart and press **Half** (**نصف**). Every approved room member sees the offer while ordering is open. Another ordering member can press **Take the other half**. The first acceptance wins.

The cart keeps one physical item. Before acceptance, the requester owns and pays for the whole item. After acceptance, both people's carts and receipts show **½**, with half of the food price assigned to each. The requester pays any indivisible minor-unit remainder. Delivery, service, discounts and tax follow the normal receipt rules.

The selected ordering person's restaurant copy and WhatsApp text still contain one whole item with its original notes, size and extras. A pending offer becomes a full item for the requester when ordering closes. Offers cannot be accepted during review or after placement.

Before review, the requester can keep the whole item; the recipient can release their half. An offered item must have its offer cancelled before its quantity, notes or customizations can change. Price changes by the organizer or selected payer recalculate both shares. Starting the next order clears all offers.

## Protocol

New clients set `halfItemDetails: true` on HTTP commands and WebSocket snapshots. Older clients receive their supported schema and whole restaurant quantities.

- `REQUEST_HALF_ITEM`: `text` is the requester's cart-line ID. If the line has multiple items, one item is separated into its own line and offered.
- `ACCEPT_HALF_ITEM`: `text` is the offer ID.
- `CANCEL_HALF_ITEM`: `text` is the offer ID. Requesters end the offer; recipients release their half.

All mutations require the current room revision and order number, approved ordering membership and an open ordering deadline. Offers are stored in `room.halfItemOffers`. Receipt lines use `halfShare: true` for display and `restaurantQuantity` for restaurant aggregation: 1 for the requester and 0 for the recipient. Financial amounts on both lines sum to the full item price.

Room snapshots broadcast offers while keeping unrelated private carts hidden. Notification inbox events are `half_item_available` and `half_item_accepted`; configured push devices use the existing notification delivery service. Notification actions open the room for acceptance.

## Validation

Server tests cover visibility, competing acceptances, replay, persistence, odd prices, multiple-item quantities, delivery/tax allocation, cancellation, permissions, protected edits, placement fallback, next orders, custom-item pricing and legacy replies. Shared native integration covers visible controls, both half carts and whole restaurant copy. Web tests and local browser fixtures cover Arabic/English controls, split prices, copy quantities and full-item fallback at mobile and desktop widths.

Browser fixtures capture commands locally; they do not send restaurant orders or payments. Local tests do not prove real-device push delivery or authenticated production ordering.
