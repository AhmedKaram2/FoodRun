import { useEffect, useState } from 'react';
import { getLanguage, t } from './i18n.js';
import ScrollLoad from './ScrollLoad.jsx';
const tx = (en, ar) => getLanguage().startsWith('ar') ? (t(en) === en ? ar : t(en)) : en;
export const matchesSearch = (value, query) => !query.trim() || JSON.stringify(value).toLocaleLowerCase().includes(query.trim().toLocaleLowerCase());
export default function PagedList({ items, children, pageSize = 8, resetKey = '', empty }) {
  const [limit, setLimit] = useState(pageSize);
  useEffect(() => setLimit(pageSize), [resetKey,pageSize]);
  return <>{items.length ? items.slice(0,limit).map(children) : empty || <p className="muted">{tx('No matching results.', 'لا توجد نتائج مطابقة.')}</p>}{items.length > limit && <ScrollLoad loadKey={`${resetKey}:${limit}`} onLoad={() => setLimit(value => value+pageSize)}>{tx('Scroll for more', 'مرّر لعرض المزيد')} · {Math.min(limit,items.length)} / {items.length}</ScrollLoad>}</>;
}
