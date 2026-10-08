import PagedList, { matchesSearch } from './PagedList.jsx';
import { t } from './i18n.js';
import MenuEditor, { MenuMoneyInput } from './MenuEditor';
import { useState } from 'react';
import { CURRENCIES } from './client.js';
import { useFeedback } from './useFeedback.js';
import FeedbackBanner from './FeedbackBanner.jsx';
import { Page, useRestaurantLibrary, storeRestaurants, blankRestaurant, changeRestaurantCurrency, normalizeRestaurant, clone, parseRestaurantExport, restaurantExport, downloadText, copyText } from './FoodRunApp';

const MANAGED_KEY = 'foodrun-server-catalog-v1';
function managedRestaurantIds() { try { return JSON.parse(localStorage.getItem(MANAGED_KEY) || '[]'); } catch { return []; } }

export default function RestaurantLibraryScreen({ onBack, language = 'en', data, room }) {
  const [search,setSearch] = useState('');
  const [restaurants, setRestaurants] = useRestaurantLibrary();
  const [draft, setDraft] = useState(blankRestaurant);
  const { feedback, setError: setMessage, setNotice, dismissFeedback } = useFeedback();
  const clearMessage = () => { setMessage(''); setNotice(''); };
  const [busy, setBusy] = useState(false);
  const [managedIds, setManagedIds] = useState(managedRestaurantIds);
  const shared = managedIds.includes(draft.id);
  const saveList = next => { setRestaurants(next); storeRestaurants(next); };
  const set = (key, value) => setDraft(old => key === 'currency' ? changeRestaurantCurrency(old, value) : ({ ...old, [key]: value }));
  const setContact = (key, value) => setDraft(old => ({ ...old, contact: { ...old.contact, [key]: value || null } }));
  const setPricing = (key, value) => setDraft(old => ({ ...old, pricing: { ...old.pricing, [key]: value } }));
  const toggleMeal = value => setDraft(old => ({ ...old, mealTypes: old.mealTypes.includes(value) ? old.mealTypes.filter(item => item !== value) : [...old.mealTypes, value] }));
  const normalizedDraft = (requireItems = true) => {
    const normalized = normalizeRestaurant(draft);
    if (requireItems && !normalized.menu.items.length) throw Error(t('Add at least one menu item and price before publishing.'));
    return normalized;
  };
  const saveLocal = () => {
    clearMessage();
    try {
      const normalized = normalizedDraft(false);
      const next = [...restaurants.filter(item => item.id !== normalized.id), normalized].sort((a, b) => a.name.localeCompare(b.name));
      saveList(next); setDraft(clone(normalized)); setNotice(t("Restaurant and menu saved on this device."));
    } catch (error) { setMessage(error.message); }
  };
  const save = async event => {
    event.preventDefault(); clearMessage();
    if (!data?.hub || !data?.identityToken) { saveLocal(); return; }
    setBusy(true);
    try {
      const normalized = normalizedDraft();
      const response = await fetch(`${data.hub}/catalog/restaurant`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ identityToken: data.identityToken, roomId: room?.id || '', roomToken: room ? data.sessions?.[room.id]?.token || '' : '', restaurant: normalized }),
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw Error(result.error || t('Restaurant could not be saved.'));
      const nextIds = [...new Set([...managedIds, ...(result.restaurants || []).map(value => value.id), normalized.id])];
      localStorage.setItem(MANAGED_KEY, JSON.stringify(nextIds)); setManagedIds(nextIds);
      const next = [...restaurants.filter(item => item.id !== normalized.id), normalized].sort((a, b) => a.name.localeCompare(b.name));
      saveList(next); setDraft(clone(normalized));
      setNotice(t('Restaurant, items, and prices are now available to everyone.'));
    } catch (error) { setMessage(error.message); }
    finally { setBusy(false); }
  };
  const addSharedItem = async (item, category) => {
    clearMessage(); setBusy(true);
    try {
      const contribution = { ...draft, menu: { items: [item], categories: [category], optionGroups: [] } };
      const reply = await data.send('ADD_MENU_ITEMS', { restaurant: contribution });
      if (!reply?.ok) { setMessage(t('Item could not be saved. Your item details are kept; check the connection and try again.')); return false; }
      const saved = reply.home?.restaurants?.find(value => value.id === draft.id);
      if (!saved) throw Error(t('Restaurant could not be saved.'));
      saveList([...restaurants.filter(value => value.id !== saved.id), saved]);
      // Keep other pending edits while accepting the authoritative menu additions.
      setDraft(old => ({ ...old, menu: { ...old.menu, categories: [...old.menu.categories, ...saved.menu.categories.filter(value => !old.menu.categories.some(existing => existing.id === value.id))], items: [...old.menu.items.filter(value => value.id !== item.id), ...saved.menu.items.filter(value => !old.menu.items.some(existing => existing.id === value.id))] } }));
      setNotice(t('Item saved to the shared menu and available in new orders.'));
      return true;
    } catch (error) { setMessage(error.message); return false; }
    finally { setBusy(false); }
  };
  const importFile = async file => {
    if (!file) return;
    try { const imported = parseRestaurantExport(await file.text()); setDraft(imported); setNotice(t("Menu imported. Review it, then save.")); }
    catch (error) { setMessage(error.message); }
  };
  return <Page title={t("Restaurants & menus")} subtitle={room ? t("Add a restaurant for this order and everyone using Intrvioo.") : t("Add restaurant details, menu items, and prices to the shared Intrvioo list.")} onBack={onBack}>
    <section className="card restaurant-editor-intro"><div><p className="eyebrow">{room ? t("ORDER OWNER") : t('SHARED RESTAURANT LIST')}</p><h2>{t('Restaurant → items → publish')}</h2><p>{t('For an existing restaurant, enter an item name and price, then press Add & save item. New restaurants need to be published once.')}</p></div><button type="button" data-mobile-target="editor" className="primary" onClick={() => { setDraft(blankRestaurant()); clearMessage(); }}>{t('＋ Add new restaurant')}</button></section>
    <div className="library-layout">
      <aside data-mobile-section="list" data-mobile-label={t("Restaurants & menus")} className="card library-list">
        <div className="section-title compact"><div><p className="eyebrow">{t("AVAILABLE RESTAURANTS")}</p><h3>{restaurants.length} {language === 'ar' ? 'مطاعم' : t("restaurants")}</h3></div><button aria-label={t('Add restaurant')} className="icon-button" type="button" onClick={() => { setDraft(blankRestaurant()); clearMessage(); }}>＋</button></div>
        {restaurants.length === 0 && <p className="muted">{t("Add your first restaurant or import a menu from Intrvioo mobile.")}</p>}
        <label>{t("Search")}<input type="search" value={search} onChange={event => setSearch(event.target.value)} /></label><PagedList items={restaurants.filter(value => matchesSearch([value.name,value.nameAr,value.area,value.emirate,value.cuisine],search))} resetKey={search}>{restaurant => <button type="button" data-mobile-target="editor" className={`restaurant-row ${draft.id === restaurant.id ? 'active' : ''}`} key={restaurant.id} onClick={() => { setDraft(clone(restaurant)); clearMessage(); }}><span><b>{restaurant.name}</b><small>{restaurant.menu.items.length} {t("menu items ·")} {restaurant.currency}</small></span><strong>›</strong></button>}</PagedList>
        <label className="upload wide">{t("Import Intrvioo JSON")}<input type="file" accept="application/json,.json" onChange={event => importFile(event.target.files?.[0])} /></label>
      </aside>
      <form data-mobile-section="editor" data-mobile-label={t("Edit restaurant")} className="stack" onInvalid={event => { let node = event.target.parentElement; while (node) { if (node.tagName === 'DETAILS') node.open = true; node = node.parentElement; } }} onSubmit={save}>
        <section className="card editor-card stack">
          <div className="section-title compact"><div><p className="eyebrow">{t("STEP 1 · RESTAURANT")}</p><h2>{draft.name || t("New restaurant")}</h2>{shared && <small className="shared-badge">✓ {t('Shared')}</small>}</div>{restaurants.some(item => item.id === draft.id) && !shared && <button type="button" className="link danger" onClick={() => { saveList(restaurants.filter(item => item.id !== draft.id)); setDraft(blankRestaurant()); }}>{t("Delete")}</button>}</div>
          <div className="form-grid three"><label>{t("Restaurant name")}<input value={draft.name} onChange={e => set('name', e.target.value)} required /></label><label>{t("Arabic name")}<input dir="rtl" value={draft.nameAr || ''} onChange={e => set('nameAr', e.target.value)} /></label><label>{t("Branch")}<input value={draft.branchName} onChange={e => set('branchName', e.target.value)} /></label><label>{t("Currency")}<select value={draft.currency} onChange={e => set('currency', e.target.value)}>{CURRENCIES.map(currency => <option key={currency}>{currency}</option>)}</select></label></div>
          <div className="form-grid three"><label>{language === 'ar' ? 'الإمارة' : t("Emirate")}<input value={draft.emirate || ''} onChange={e => set('emirate', e.target.value)} placeholder={t("Dubai")} /></label><label>{language === 'ar' ? 'الإمارة بالعربية' : t("Arabic emirate")}<input dir="rtl" value={draft.emirateAr || ''} onChange={e => set('emirateAr', e.target.value)} placeholder="دبي" /></label><label>{language === 'ar' ? 'المنطقة' : t("Area")}<input value={draft.area || ''} onChange={e => set('area', e.target.value)} placeholder={t("Al Barsha")} /></label><label>{language === 'ar' ? 'المنطقة بالعربية' : t("Arabic area")}<input dir="rtl" value={draft.areaAr || ''} onChange={e => set('areaAr', e.target.value)} placeholder="البرشاء" /></label><label>{language === 'ar' ? 'نوع المطبخ' : t("Cuisine")}<input value={draft.cuisine || ''} onChange={e => set('cuisine', e.target.value)} placeholder={t("Arabic")} /></label></div>
          <div className="meal-editor"><b>{language === 'ar' ? 'الوجبات' : t("Meals")}</b>{[['breakfast', language === 'ar' ? 'فطور' : t("Breakfast")], ['lunch', language === 'ar' ? 'غداء' : t("Lunch")], ['dinner', language === 'ar' ? 'عشاء' : t("Dinner")]].map(([value, label]) => <label className="check" key={value}><input type="checkbox" checked={draft.mealTypes.includes(value)} onChange={() => toggleMeal(value)} />{label}</label>)}</div>
          <div className="form-grid three"><label>{t("Phone with country code")}<input value={draft.contact.phoneE164 || ''} onChange={e => setContact('phoneE164', e.target.value)} placeholder="+20 10 1234 5678" /></label><label>{t("WhatsApp with country code")}<input value={draft.contact.whatsappE164 || ''} onChange={e => setContact('whatsappE164', e.target.value)} placeholder="+971 50 123 4567" /></label><label>{t("Address")}<input value={draft.contact.address || ''} onChange={e => setContact('address', e.target.value)} /></label></div>
          <div className="form-grid three"><label>{t("Tax treatment")}<select value={draft.pricing.taxTreatment} onChange={e => setPricing('taxTreatment', e.target.value)}><option value="included">{t("Included")}</option><option value="added">{t("Added to bill")}</option><option value="unspecified">{t("Confirm later")}</option></select></label>{draft.pricing.taxTreatment === 'added' && <label>{t("Tax rate %")}<input type="number" min="0" max="100" step="0.01" value={(draft.pricing.taxRateBasisPoints || 0) / 100} onChange={e => setPricing('taxRateBasisPoints', Math.round(Number(e.target.value) * 100))} /></label>}<label>{t("Minimum order")}<MenuMoneyInput currency={draft.currency} value={draft.pricing.minimumOrderMinor} onChange={value => setPricing('minimumOrderMinor', value)} /></label></div>
          <div className="form-grid three"><label>{t("Default delivery fee")}<MenuMoneyInput currency={draft.currency} value={draft.pricing.defaultDeliveryFeeMinor} onChange={value => setPricing('defaultDeliveryFeeMinor', value)} /></label><label>{t("Default service fee")}<MenuMoneyInput currency={draft.currency} value={draft.pricing.defaultServiceFeeMinor} onChange={value => setPricing('defaultServiceFeeMinor', value)} /></label><label className="check field-check"><input type="checkbox" checked={draft.openOrdering} onChange={e => set('openOrdering', e.target.checked)} />{t("Allow custom items")}</label></div>
        </section>
        <MenuEditor menu={draft.menu} language={language} currency={draft.currency} busy={busy || data?.busy} onAddItem={shared && data?.identityToken ? addSharedItem : undefined} onChange={menu => setDraft(old => ({ ...old, menu }))} />
        {feedback && <FeedbackBanner key={feedback.id} feedback={feedback} onDismiss={dismissFeedback} />}
        <div className="editor-actions"><button type="button" className="secondary" onClick={saveLocal}>{t("Save on this device")}</button><button type="button" className="secondary" onClick={() => downloadText(`${(draft.name || t("restaurant")).replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.foodrun.json`, restaurantExport(draft))}>{t("Export JSON")}</button><button type="button" className="secondary" onClick={() => copyText(restaurantExport(draft)).then(() => setNotice(t("Restaurant JSON copied."))).catch(error => setMessage(error.message))}>{t("Copy JSON")}</button><button className="primary" disabled={busy}>{busy ? t('Publishing…') : shared ? t('Save changes for everyone') : t('Publish for everyone')}</button></div>
      </form>
    </div>
  </Page>;
}
