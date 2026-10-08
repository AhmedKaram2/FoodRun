import { useEffect, useRef, useState } from "react";
import type { Action, Card, Snapshot } from "./types";
import { dispatch, update } from "./native";
import { tx } from "./theme";

export type FlowKind = "room" | "restaurant" | "payment" | "menu";
export type FlowStep = {
  title: string;
  hint: string;
  fields: string[];
  optional: string[];
  cards: (card: Card) => boolean;
  actions: string[];
};
export const allFields = (s: Snapshot) => [...s.mainFields, ...s.extraFields];
export const allCards = (s: Snapshot) => [...s.topCards, ...s.sections.flatMap(section => section.cards)];
export const allActions = (s: Snapshot) => [s.primaryAction, ...s.inlineButtons, ...s.utilityButtons].filter((a): a is Action => !!a);

export function flowKind(s: Snapshot): FlowKind | null {
  const fields = allFields(s);
  if (s.state.page === "SETUP" && allActions(s).some(a => a.action === "CREATE_ROOM")) return "room";
  if (s.state.page === "RESTAURANT" && allActions(s).some(a => a.action === "SAVE_RESTAURANT")) return "restaurant";
  if (s.state.page === "PAYMENT_ROOM" && fields.some(f => f.key === "PAYMENT_TOTAL")) return "payment";
  if (s.state.page === "MENU_ENTITY" && fields.length > 4) return "menu";
  return null;
}

export function flowSteps(kind: FlowKind, rtl: boolean): FlowStep[] {
  const step = (en: string, ar: string, hint: string, hintAr: string, fields: string[], optional: string[] = [], cards: FlowStep["cards"] = () => false, actions: string[] = []): FlowStep =>
    ({ title: tx(rtl, en, ar), hint: tx(rtl, hint, hintAr), fields, optional, cards, actions });
  const review = step("Ready to go?", "جاهز؟", "Check your choices. You can edit any step.", "راجع اختياراتك، ويمكنك تعديل أي خطوة.", []);
  switch (kind) {
    case "room": return [
      step("Choose the food", "نبدأ بالمطعم", "Pick a saved restaurant, add one, or let your friends vote.", "اختر مطعماً محفوظاً، أضف مطعماً، أو اترك الاختيار للأصدقاء.", ["RESTAURANT_POLL"]),
      step("Bring your people", "ادعُ أصحابك", "Name your room and invite a favourite group. The timer is optional.", "سمِّ الغرفة وادعُ مجموعة أصدقاء. مهلة الانضمام اختيارية.", ["ROOM_NAME", "FRIEND_GROUP_CHOICE", "JOIN_TIMER", "JOIN_TIMER_MINUTES"], ["NAME", "EXPECTED_NAMES", "SELECTION_STYLE"]),
      step("Delivery & fees", "التوصيل والرسوم", "Choose delivery or pickup. Extra charges are optional.", "اختر التوصيل أو الاستلام. الرسوم الإضافية اختيارية.", ["DELIVERY", "DESTINATION"], ["DELIVERY_FEE", "PROPORTIONAL", "SERVICE_FEE", "DISCOUNT"], c => c.id === "automatic-delivery"),
      review,
    ];
    case "restaurant": return [
      step("The essentials", "البيانات الأساسية", "A name and branch are enough to get started.", "ابدأ باسم المطعم والفرع.", ["RESTAURANT_NAME", "BRANCH", "OPEN_ORDERING"], ["RESTAURANT_NAME_AR", "BRANCH_AR", "CUISINE", "CUISINE_AR", "RESTAURANT_NOTES", "MEAL_BREAKFAST", "MEAL_LUNCH", "MEAL_DINNER"]),
      step("Where is it?", "أين المطعم؟", "Add the emirate, area and address for this branch.", "أضف الإمارة والمنطقة وعنوان الفرع.", ["EMIRATE", "AREA", "ADDRESS"], ["EMIRATE_AR", "AREA_AR"]),
      step("How to reach them", "التواصل مع المطعم", "Phone and WhatsApp help the ordering person contact the restaurant.", "الهاتف وواتساب يساعدان الشخص المختار على التواصل مع المطعم.", ["PHONE", "RESTAURANT_WHATSAPP"]),
      step("Menu & prices", "القائمة والأسعار", "Choose the currency and add food. You can set extra charges here.", "اختر العملة وأضف الأصناف. يمكنك تحديد الرسوم الإضافية هنا.", ["CURRENCY"], ["DELIVERY_FEE", "SERVICE_FEE", "MINIMUM_ORDER", "TAX_RATE"], c => c.id === "tax-treatment", ["MENU_OPEN"]),
      review,
    ];
    case "payment": return [
      step("The receipt", "الإيصال", "Name the room and enter the total in the correct currency.", "سمِّ الغرفة وأدخل الإجمالي بالعملة الصحيحة.", ["PAYMENT_ROOM_NAME", "PAYMENT_CURRENCY", "PAYMENT_TOTAL"], ["PAYMENT_RESTAURANT"]),
      step("Split with friends", "قسِّم مع أصحابك", "Choose people and set each share. Shares must match the total.", "اختر الأشخاص وحدد حصة كل شخص. يجب أن يساوي مجموع الحصص الإجمالي.", [], [], c => c.id === "payment-shares-total"),
      step("Add the details", "أضف التفاصيل", "Describe the order. A receipt photo is optional.", "اكتب تفاصيل الطلب. صورة الإيصال اختيارية.", ["PAYMENT_DETAILS"], ["RECEIPT_PHOTO"]),
      review,
    ];
    case "menu": return [
      step("Name & category", "الاسم والقسم", "Give the menu entry a name and choose its category.", "سمِّ الصنف واختر قسمه.", ["MENU_ENTITY_NAME", "MENU_ENTITY_CATEGORY"], ["MENU_ENTITY_AR", "MENU_ENTITY_DESCRIPTION", "MENU_ENTITY_DESCRIPTION_AR"]),
      step("Prices & extras", "الأسعار والإضافات", "Set the price, availability and any sizes or extras.", "حدد السعر والتوفر والأحجام أو الإضافات.", ["MENU_ENTITY_PRICE", "MENU_ENTITY_AVAILABLE", "MENU_ENTITY_MIN", "MENU_ENTITY_MAX", "MENU_ENTITY_SORT"], [], () => true, ["MENU_EDIT"]),
      review,
    ];
  }
}

// Only navigation is local. Fields and final mutations still use the shared controller.
export function stepError(kind: FlowKind, index: number, s: Snapshot): string {
  const fields = allFields(s), rtl = s.state.rtl;
  const value = (key: string) => fields.find(f => f.key === key)?.value.trim() || "";
  const present = (key: string) => fields.some(f => f.key === key);
  const required = (key: string) => present(key) && !value(key);
  if (kind === "room") {
    if (index === 0) {
      if (value("RESTAURANT_POLL") === "true") {
        const count = Number(allCards(s).find(c => c.id === "poll-choices")?.title.match(/\d+/)?.[0] || 0);
        if (count < 2) return tx(rtl, "Choose at least two restaurants for the poll.", "اختر مطعمين على الأقل للتصويت.");
      } else if (present("RESTAURANT_NAME")) return tx(rtl, "Choose a restaurant or use an open menu first.", "اختر مطعماً أو استخدم قائمة مفتوحة أولاً.");
    }
    if (index === 1) {
      if (required("NAME")) return tx(rtl, "Enter your name in More options.", "أدخل اسمك في الخيارات الإضافية.");
      if (value("JOIN_TIMER") === "true" && (!/^\d+$/.test(value("JOIN_TIMER_MINUTES")) || Number(value("JOIN_TIMER_MINUTES")) < 1 || Number(value("JOIN_TIMER_MINUTES")) > 1440)) return tx(rtl, "Choose a join time from 1 to 1440 minutes.", "اختر مهلة انضمام من ١ إلى ١٤٤٠ دقيقة.");
    }
  }
  if (kind === "restaurant" && index === 0 && required("RESTAURANT_NAME")) return tx(rtl, "Enter the restaurant name.", "أدخل اسم المطعم.");
  if (kind === "payment") {
    if (index === 0 && (!/^[0-9]{1,9}(\.[0-9]{1,3})?$/.test(value("PAYMENT_TOTAL")) || Number(value("PAYMENT_TOTAL")) <= 0)) return tx(rtl, "Enter a receipt total greater than zero.", "أدخل إجمالي إيصال أكبر من صفر.");
    if (index === 1) {
      const cards = allCards(s);
      const assigned = cards.filter(c => c.id.startsWith("share:") && (c.detail || c.buttons.some(a => a.action === "REMOVE_PAYMENT_SHARE")));
      if (assigned.length < 2) return tx(rtl, "Add your share and at least one friend.", "أضف حصتك وحصة صديق واحد على الأقل.");
      const total = cards.find(c => c.id === "payment-shares-total")?.detail.split(" ").pop();
      if (total && Math.round(Number(total) * 1000) !== Math.round(Number(value("PAYMENT_TOTAL")) * 1000)) return tx(rtl, "Adjust the shares to match the receipt total.", "عدِّل الحصص لتساوي إجمالي الإيصال.");
    }
  }
  if (kind === "menu" && index === 0 && (required("MENU_ENTITY_NAME") || required("MENU_ENTITY_CATEGORY"))) return tx(rtl, "Enter a name and choose a category.", "أدخل الاسم واختر القسم.");
  return "";
}

export function useGuidedFlow(s: Snapshot | null) {
  const [steps, setSteps] = useState<Record<FlowKind, number>>({room: 0, restaurant: 0, payment: 0, menu: 0});
  const [pickingRestaurant, setPickingRestaurant] = useState(false);
  const roomDraft = useRef<Snapshot | null>(null);
  const previousPage = useRef<string | undefined>(undefined);
  const account = s?.accountId, page = s?.state.page;
  useEffect(() => {
    setSteps({room: 0, restaurant: 0, payment: 0, menu: 0});
    setPickingRestaurant(false);
    roomDraft.current = null;
  }, [account]);
  useEffect(() => {
    if (!s) return;
    const previous = previousPage.current;
    previousPage.current = page;
    if (page === "LIBRARY" && previous === "SETUP" && roomDraft.current?.accountId === account) {
      setPickingRestaurant(true);
      allFields(s).filter(f => ["RESTAURANT_SEARCH", "RESTAURANT_EMIRATE", "RESTAURANT_AREA", "RESTAURANT_MEAL"].includes(f.key) && f.value).forEach(f => update(f.key, ""));
    }
    if (flowKind(s) === "room") { roomDraft.current = s; setPickingRestaurant(false); }
    if (!["CONNECT", "SETUP", "LIBRARY", "RESTAURANT", "MENU_EDITOR", "MENU_ENTITY"].includes(page!)) {
      roomDraft.current = null;
      setPickingRestaurant(false);
      setSteps(old => ({...old, room: 0}));
    }
    if (!["RESTAURANT", "MENU_EDITOR", "MENU_ENTITY"].includes(page!)) setSteps(old => ({...old, restaurant: 0}));
    if (!["PAYMENT_ROOM", "PAYMENT_SHARE"].includes(page!)) setSteps(old => ({...old, payment: 0}));
    if (page !== "MENU_ENTITY") setSteps(old => ({...old, menu: 0}));
  }, [page, account]);
  // Every draft change is already persisted by the native controller; keep its newest view behind the sheet.
  useEffect(() => { if (s && flowKind(s) === "room") roomDraft.current = s; }, [s]);
  const picker = page === "LIBRARY" && (pickingRestaurant || s?.primaryAction?.action === "CONFIRM_POLL_RESTAURANTS") && roomDraft.current?.accountId === account;
  const screen = picker ? roomDraft.current : s;
  const kind = screen ? flowKind(screen) : null;
  const step = kind ? steps[kind] : 0;
  const setStep = (index: number) => { if (kind) setSteps(old => ({...old, [kind]: index})); };
  const run = (a: Action) => {
    if (kind === "room" && ["OPEN_LIBRARY", "OPEN_POLL_RESTAURANTS"].includes(a.action)) {
      setPickingRestaurant(true);
    }
    dispatch(a.action, a.value);
  };
  const back = () => {
    if (!kind) return false;
    if (s?.state.busy) return true;
    if (picker || step === 0) dispatch("BACK"); else setStep(step - 1);
    return true;
  };
  return {kind, screen, step, setStep, run, back, picker: picker ? s : null};
}
