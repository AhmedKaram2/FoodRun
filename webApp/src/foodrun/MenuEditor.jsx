import { useEffect, useState } from 'react';
import { amount, money } from './client';
import { removeOptionGroup } from './menuValidation';

export function MenuMoneyInput({ value, onChange, label }) {
  const [text, setText] = useState((value / 100).toFixed(2));
  useEffect(() => { setText(current => { try { if (amount(current) === value) return current; } catch { /* External value replaced the draft. */ } return (value / 100).toFixed(2); }); }, [value]);
  return <input aria-label={label} inputMode="decimal" required value={text} onChange={event => {
    setText(event.target.value);
    try { const parsed = amount(event.target.value); event.target.setCustomValidity(''); onChange(parsed); }
    catch (error) { event.target.setCustomValidity(error.message); }
  }} />;
}
export default function MenuEditor({ menu, onChange, language = 'en' }) {
  const t = (en, ar) => language === 'ar' ? ar : en;
  const id = () => crypto.randomUUID();
  const editItem = (itemId, fields) => onChange({ ...menu, items: menu.items.map(item => item.id === itemId ? { ...item, ...fields } : item) });
  const editGroup = (groupId, fields) => onChange({ ...menu, optionGroups: menu.optionGroups.map(group => group.id === groupId ? { ...group, ...fields } : group) });
  const bilingualFields = (value, change) => <div className="form-grid two"><label>{t('English name', 'الاسم بالإنجليزية')}<input value={value.name} required maxLength="160" onChange={e => change({ name: e.target.value })} /></label><label>{t('Arabic name', 'الاسم بالعربية')}<input dir="rtl" value={value.nameAr || ''} maxLength="160" onChange={e => change({ nameAr: e.target.value })} /></label></div>;
  return <section className="card stack menu-builder">
    <h2>{t('Menu, sizes and extras', 'القائمة والأحجام والإضافات')}</h2>
    <p className="muted">{t('Prices are in AED. Sizes replace the base price; extras are added to it.', 'الأسعار بالدرهم. سعر الحجم يحل محل السعر الأساسي وتضاف إليه أسعار الإضافات.')}</p>
    {menu.categories.map(category => <details className="menu-category-editor" key={category.id}>
      <summary>{language === 'ar' && category.nameAr ? category.nameAr : category.name || t('New category', 'قسم جديد')} · {menu.items.filter(item => item.categoryId === category.id).length}</summary>
      {bilingualFields(category, fields => onChange({ ...menu, categories: menu.categories.map(value => value.id === category.id ? { ...value, ...fields } : value) }))}
      <button type="button" className="link danger" onClick={() => onChange({ ...menu, categories: menu.categories.filter(value => value.id !== category.id), items: menu.items.filter(item => item.categoryId !== category.id) })}>{t('Remove category and its items', 'حذف القسم وأصنافه')}</button>
      {menu.items.filter(item => item.categoryId === category.id).map(item => <details className="menu-item-editor" key={item.id}>
        <summary>{language === 'ar' && item.nameAr ? item.nameAr : item.name || t('New item', 'صنف جديد')} · {money(item.basePriceMinor)}</summary>
        {bilingualFields(item, fields => editItem(item.id, fields))}
        <div className="form-grid two"><label>{t('Description', 'الوصف')}<textarea value={item.description || ''} maxLength="2000" onChange={e => editItem(item.id, { description: e.target.value })} /></label><label>{t('Arabic description', 'الوصف بالعربية')}<textarea dir="rtl" value={item.descriptionAr || ''} maxLength="2000" onChange={e => editItem(item.id, { descriptionAr: e.target.value })} /></label></div>
        <label>{t('Base price', 'السعر الأساسي')}<MenuMoneyInput label={t('Base price', 'السعر الأساسي')} value={item.basePriceMinor} onChange={basePriceMinor => editItem(item.id, { basePriceMinor })} /></label>
        <label className="check"><input type="checkbox" checked={item.available} onChange={e => editItem(item.id, { available: e.target.checked })} />{t('Available to order', 'متاح للطلب')}</label>
        <h3>{t('Sizes', 'الأحجام')}</h3>
        {item.variants.map(variant => <div className="menu-variant" key={variant.id}>
          {bilingualFields(variant, fields => editItem(item.id, { variants: item.variants.map(value => value.id === variant.id ? { ...value, ...fields } : value) }))}
          <label>{t('Size price', 'سعر الحجم')}<MenuMoneyInput label={t('Size price', 'سعر الحجم')} value={variant.priceMinor} onChange={priceMinor => editItem(item.id, { variants: item.variants.map(value => value.id === variant.id ? { ...value, priceMinor } : value) })} /></label>
          <button type="button" className="link danger" onClick={() => editItem(item.id, { variants: item.variants.filter(value => value.id !== variant.id) })}>{t('Remove size', 'حذف الحجم')}</button>
        </div>)}
        <button type="button" className="secondary" onClick={() => editItem(item.id, { variants: [...item.variants, { id: id(), name: '', nameAr: '', priceMinor: item.basePriceMinor }] })}>{t('Add size', 'إضافة حجم')}</button>
        <h3>{t('Extras offered with this item', 'الإضافات المتاحة لهذا الصنف')}</h3>
        {menu.optionGroups.map(group => <label className="check" key={group.id}><input type="checkbox" checked={item.optionGroupIds.includes(group.id)} onChange={e => editItem(item.id, { optionGroupIds: e.target.checked ? [...item.optionGroupIds, group.id] : item.optionGroupIds.filter(value => value !== group.id) })} />{language === 'ar' && group.nameAr ? group.nameAr : group.name || t('New extras group', 'مجموعة إضافات جديدة')}</label>)}
        <button type="button" className="link danger" onClick={() => onChange({ ...menu, items: menu.items.filter(value => value.id !== item.id) })}>{t('Remove item', 'حذف الصنف')}</button>
      </details>)}
      <button type="button" className="secondary" onClick={() => onChange({ ...menu, items: [...menu.items, { id: id(), categoryId: category.id, name: '', nameAr: '', description: '', descriptionAr: '', basePriceMinor: 0, available: true, variants: [], optionGroupIds: [] }] })}>{t('Add item', 'إضافة صنف')}</button>
    </details>)}
    <button type="button" className="secondary" onClick={() => onChange({ ...menu, categories: [...menu.categories, { id: id(), name: '', nameAr: '', sortOrder: menu.categories.length }] })}>{t('Add category', 'إضافة قسم')}</button>
    <h3>{t('Shared extras groups', 'مجموعات الإضافات المشتركة')}</h3>
    {menu.optionGroups.map(group => <details className="menu-item-editor" key={group.id}>
      <summary>{language === 'ar' && group.nameAr ? group.nameAr : group.name || t('New extras group', 'مجموعة إضافات جديدة')}</summary>
      {bilingualFields(group, fields => editGroup(group.id, fields))}
      <div className="form-grid two"><label>{t('Minimum choices (0 = optional)', 'أقل عدد اختيارات (٠ = اختياري)')}<input type="number" min="0" max="30" value={group.minSelections} onChange={e => editGroup(group.id, { minSelections: e.target.valueAsNumber })} required /></label><label>{t('Maximum choices', 'أقصى عدد اختيارات')}<input type="number" min="0" max="30" value={group.maxSelections} onChange={e => editGroup(group.id, { maxSelections: e.target.valueAsNumber })} required /></label></div>
      {group.options.map(option => <div className="menu-variant" key={option.id}>
        {bilingualFields(option, fields => editGroup(group.id, { options: group.options.map(value => value.id === option.id ? { ...value, ...fields } : value) }))}
        <label>{t('Extra price', 'سعر الإضافة')}<MenuMoneyInput label={t('Extra price', 'سعر الإضافة')} value={option.priceDeltaMinor} onChange={priceDeltaMinor => editGroup(group.id, { options: group.options.map(value => value.id === option.id ? { ...value, priceDeltaMinor } : value) })} /></label>
        <button type="button" className="link danger" onClick={() => editGroup(group.id, { options: group.options.filter(value => value.id !== option.id) })}>{t('Remove extra', 'حذف الإضافة')}</button>
      </div>)}
      <button type="button" className="secondary" onClick={() => editGroup(group.id, { options: [...group.options, { id: id(), name: '', nameAr: '', priceDeltaMinor: 0 }] })}>{t('Add extra', 'إضافة خيار')}</button>
      <button type="button" className="link danger" onClick={() => onChange(removeOptionGroup(menu, group.id))}>{t('Remove group from all items', 'حذف المجموعة من كل الأصناف')}</button>
    </details>)}
    <button type="button" className="secondary" onClick={() => onChange({ ...menu, optionGroups: [...menu.optionGroups, { id: id(), name: '', nameAr: '', minSelections: 0, maxSelections: 1, options: [{ id: id(), name: '', nameAr: '', priceDeltaMinor: 0 }] }] })}>{t('Add extras group', 'إضافة مجموعة إضافات')}</button>
  </section>;
}
