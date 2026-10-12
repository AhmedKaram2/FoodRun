import type { Action, Card, Field, Section, Snapshot } from "./types";
import { tx } from "./theme";

export const isRoom = (page: string) => page === "ROOM";

// Group by stable card IDs so language changes and live updates keep the same tabs.
export function roomCardTab(card: Card): number {
  if (/^(menu:|menu-empty$|cart:|cart-submitted$|half-item:|reuse-favorite:|reuse-past:|price:|early-order$|pricing-help$|estimate$)/.test(card.id)) return 1;
  if (/^(wallet|transfer:|receipt:|quote:|account$|restaurant-balance$|my-payment-status$|settlement-)/.test(card.id)) return 2;
  if (/^(member:|invite:|pending-join$)/.test(card.id)) return 3;
  return 0;
}
export function roomSections(snapshot: Snapshot): Section[] {
  const cards = [...snapshot.topCards, ...snapshot.sections.flatMap(s => s.cards)];
  const rtl = snapshot.state.rtl;
  return [
    tx(rtl, "Overview", "ملخص"),
    tx(rtl, "My food", "طلبي"),
    tx(rtl, "Payments", "المدفوعات"),
    `${tx(rtl, "Members", "الأعضاء")} (${snapshot.state.joinedCount ?? cards.filter(c => c.id.startsWith("member:")).length})`,
  ].map((title, index) => ({ title, cards: cards.filter(c => roomCardTab(c) === index), collapsed: false }));
}
export function roomActionTab(button: Action): number {
  if (/^(OPEN_ORDER_PRICES|OPEN_CUSTOM_ITEM|SUBMIT_CART|USE_OPEN_ORDER|REUSE_ORDER|CONFIRM_REORDER)$/.test(button.action)) return 1;
  if (/^(OPEN_ACCOUNT|SHARE_ACCOUNT|DECLARE_TRANSFER|DECLARE_REFUND|PAY_WITH_WALLET|RECORD_PAYMENT|HANDOVER|FULFILL|PAY_RESTAURANT|CONFIRM_QUOTE)$/.test(button.action)) return 2;
  if (/^(OPEN_PEOPLE|APPROVE|APPROVE_LATE_JOIN|SHARE_ROOM)$/.test(button.action)) return 3;
  return 0;
}
export function roomFieldTab(field: Field): number {
  if (/^MENU_/.test(field.key)) return 1;
  return /^(TRANSFER_AMOUNT_MODE|AMOUNT|REFERENCE|PAYMENT_|ACCOUNT_|AANI)/.test(field.key) ? 2 : 0;
}

export const roomDetailCard=(card:Card)=>/^(last-chosen|selection-refused|declined-duty|viewing-order)$/.test(card.id);
export const roomDetailAction=(button:Action)=>/^(SHARE_ROOM|OPEN_RECEIPTS|OPEN_HISTORY|SAVE_ROOM_RESTAURANT|EDIT_ROOM_RESTAURANT|OPEN_WHEEL_PROTECTION|OPEN_BLOCK_REQUEST|PARTICIPATE|CANCEL|SET_FEES|REOPEN)$/.test(button.action);
export function roomPrimaryAction(snapshot:Snapshot):Action|null {
  const primary=snapshot.primaryAction;
  if(!primary || primary.action==="SUBMIT_CART")return null;
  // Preserve the server's readiness and action; only clarify the visible label.
  return primary.action==="PREPARE_SPIN" ? {...primary,title:tx(snapshot.state.rtl,"Start wheel","ابدأ العجلة")} : primary;
}
