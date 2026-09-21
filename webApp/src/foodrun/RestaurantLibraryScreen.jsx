import { t } from './i18n.js';
import MenuEditor, { MenuMoneyInput } from './MenuEditor';
import { useState } from 'react';
import { money, amount } from './client';
import { Page, useRestaurantLibrary, storeRestaurants, blankRestaurant, normalizeRestaurant, clone, uid, minorInput, parseRestaurantExport, restaurantExport, downloadText, copyText } from './FoodRunApp';

export default function RestaurantLibraryScreen({ onBack, language = 'en' }) {
  const [restaurants, setRestaurants] = useRestaurantLibrary();
  const [draft, setDraft] = useState(blankRestaurant);
  const [message, setMessage] = useState('');
  const saveList = next => { setRestaurants(next); storeRestaurants(next); };
  const set = (key, value) => setDraft(old => ({ ...old, [key]: value }));
  const setContact = (key, value) => setDraft(old => ({ ...old, contact: { ...old.contact, [key]: value || null } }));
  const setPricing = (key, value) => setDraft(old => ({ ...old, pricing: { ...old.pricing, [key]: value } }));
  const toggleMeal = value => setDraft(old => ({ ...old, mealTypes: old.mealTypes.includes(value) ? old.mealTypes.filter(item => item !== value) : [...old.mealTypes, value] }));
  const save = event => {
    event.preventDefault(); setMessage('');
    try {
      const normalized = normalizeRestaurant(draft);
      const next = [...restaurants.filter(item => item.id !== normalized.id), normalized].sort((a, b) => a.name.localeCompare(b.name));
      saveList(next); setDraft(clone(normalized)); setMessage(t("Restaurant and menu saved on this device."));
    } catch (error) { setMessage(error.message); }
  };
  const importFile = async file => {
    if (!file) return;
    try { const imported = parseRestaurantExport(await file.text()); setDraft(imported); setMessage(t("Menu imported. Review it, then save.")); }
    catch (error) { setMessage(error.message); }
  };
  return <Page title={t("Restaurants & menus")} subtitle={t("Save restaurant details and prices once, or import the same Food Run schema used by the mobile apps.")} onBack={onBack}>
    <div className="library-layout">
      <aside className="card library-list">
        <div className="section-title compact"><div><p className="eyebrow">{t("SAVED ON THIS DEVICE")}</p><h3>{restaurants.length} {language === 'ar' ? 'مطاعم' : t("restaurants")}</h3></div><button className="icon-button" type="button" onClick={() => { setDraft(blankRestaurant()); setMessage(''); }}>＋</button></div>
        {restaurants.length === 0 && <p className="muted">{t("Add your first restaurant or import a menu from Food Run mobile.")}</p>}
        {restaurants.map(restaurant => <button type="button" className={`restaurant-row ${draft.id === restaurant.id ? 'active' : ''}`} key={restaurant.id} onClick={() => { setDraft(clone(restaurant)); setMessage(''); }}><span><b>{restaurant.name}</b><small>{restaurant.menu.items.length} {t("menu items ·")} {restaurant.currency}</small></span><strong>›</strong></button>)}
        <label className="upload wide">{t("Import Food Run JSON")}<input type="file" accept="application/json,.json" onChange={event => importFile(event.target.files?.[0])} /></label>
      </aside>
      <form className="stack" onInvalid={event => { let node = event.target.parentElement; while (node) { if (node.tagName === 'DETAILS') node.open = true; node = node.parentElement; } }} onSubmit={save}>
        <section className="card editor-card stack">
          <div className="section-title compact"><div><p className="eyebrow">{t("RESTAURANT")}</p><h2>{draft.name || t("New restaurant")}</h2></div>{restaurants.some(item => item.id === draft.id) && <button type="button" className="link danger" onClick={() => { saveList(restaurants.filter(item => item.id !== draft.id)); setDraft(blankRestaurant()); }}>{t("Delete")}</button>}</div>
          <div className="form-grid three"><label>{t("Restaurant name")}<input value={draft.name} onChange={e => set('name', e.target.value)} required /></label><label>{t("Arabic name")}<input dir="rtl" value={draft.nameAr || ''} onChange={e => set('nameAr', e.target.value)} /></label><label>{t("Branch")}<input value={draft.branchName} onChange={e => set('branchName', e.target.value)} /></label><label>{t("Currency")}<input value="AED · UAE Dirham" readOnly /></label></div>
          <div className="form-grid three"><label>{language === 'ar' ? 'الإمارة' : t("Emirate")}<input value={draft.emirate || ''} onChange={e => set('emirate', e.target.value)} placeholder={t("Dubai")} /></label><label>{language === 'ar' ? 'الإمارة بالعربية' : t("Arabic emirate")}<input dir="rtl" value={draft.emirateAr || ''} onChange={e => set('emirateAr', e.target.value)} placeholder="دبي" /></label><label>{language === 'ar' ? 'المنطقة' : t("Area")}<input value={draft.area || ''} onChange={e => set('area', e.target.value)} placeholder={t("Al Barsha")} /></label><label>{language === 'ar' ? 'المنطقة بالعربية' : t("Arabic area")}<input dir="rtl" value={draft.areaAr || ''} onChange={e => set('areaAr', e.target.value)} placeholder="البرشاء" /></label><label>{language === 'ar' ? 'نوع المطبخ' : t("Cuisine")}<input value={draft.cuisine || ''} onChange={e => set('cuisine', e.target.value)} placeholder={t("Arabic")} /></label></div>
          <div className="meal-editor"><b>{language === 'ar' ? 'الوجبات' : t("Meals")}</b>{[['breakfast', language === 'ar' ? 'فطور' : t("Breakfast")], ['lunch', language === 'ar' ? 'غداء' : t("Lunch")], ['dinner', language === 'ar' ? 'عشاء' : t("Dinner")]].map(([value, label]) => <label className="check" key={value}><input type="checkbox" checked={draft.mealTypes.includes(value)} onChange={() => toggleMeal(value)} />{label}</label>)}</div>
          <div className="form-grid three"><label>{t("UAE phone")}<input value={draft.contact.phoneE164 || ''} onChange={e => setContact('phoneE164', e.target.value)} placeholder="050 123 4567" /></label><label>{t("UAE WhatsApp")}<input value={draft.contact.whatsappE164 || ''} onChange={e => setContact('whatsappE164', e.target.value)} placeholder="050 123 4567" /></label><label>{t("Address")}<input value={draft.contact.address || ''} onChange={e => setContact('address', e.target.value)} /></label></div>
          <div className="form-grid three"><label>{t("Tax treatment")}<select value={draft.pricing.taxTreatment} onChange={e => setPricing('taxTreatment', e.target.value)}><option value="included">{t("Included")}</option><option value="added">{t("Added to bill")}</option><option value="unspecified">{t("Confirm later")}</option></select></label>{draft.pricing.taxTreatment === 'added' && <label>{t("Tax rate %")}<input type="number" min="0" max="100" step="0.01" value={(draft.pricing.taxRateBasisPoints || 0) / 100} onChange={e => setPricing('taxRateBasisPoints', Math.round(Number(e.target.value) * 100))} /></label>}<label>{t("Minimum order")}<MenuMoneyInput value={draft.pricing.minimumOrderMinor} onChange={value => setPricing('minimumOrderMinor', value)} /></label></div>
          <div className="form-grid three"><label>{t("Default delivery fee")}<MenuMoneyInput value={draft.pricing.defaultDeliveryFeeMinor} onChange={value => setPricing('defaultDeliveryFeeMinor', value)} /></label><label>{t("Default service fee")}<MenuMoneyInput value={draft.pricing.defaultServiceFeeMinor} onChange={value => setPricing('defaultServiceFeeMinor', value)} /></label><label className="check field-check"><input type="checkbox" checked={draft.openOrdering} onChange={e => set('openOrdering', e.target.checked)} />{t("Allow custom items")}</label></div>
        </section>
        <MenuEditor menu={draft.menu} language={language} onChange={menu => setDraft(old => ({ ...old, menu }))} />
        {message && <p className={message.includes('saved') || message.includes('imported') ? 'success-message' : 'form-message'} role="status">{message}</p>}
        <div className="editor-actions"><button type="button" className="secondary" onClick={() => downloadText(`${(draft.name || t("restaurant")).replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.foodrun.json`, restaurantExport(draft))}>{t("Export JSON")}</button><button type="button" className="secondary" onClick={() => copyText(restaurantExport(draft)).then(() => setMessage(t("Restaurant JSON copied.")))}>{t("Copy JSON")}</button><button className="primary">{t("Save restaurant")}</button></div>
      </form>
    </div>
  </Page>;
}
