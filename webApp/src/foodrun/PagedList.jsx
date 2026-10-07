import { useEffect, useState } from 'react';
import { getLanguage } from './i18n.js';
const tx = (en, ar) => getLanguage() === 'ar' ? ar : en;
export const matchesSearch = (value, query) => !query.trim() || JSON.stringify(value).toLocaleLowerCase().includes(query.trim().toLocaleLowerCase());
export default function PagedList({ items, children, pageSize = 8, resetKey = '', empty }) {
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [resetKey]);
  const pages = Math.ceil(items.length / pageSize), current = Math.min(page, Math.max(0, pages - 1));
  return <>{items.length ? items.slice(current * pageSize, (current + 1) * pageSize).map(children) : empty || <p className="muted">{tx('No matching results.', 'لا توجد نتائج مطابقة.')}</p>}{pages > 1 && <nav className="list-pagination" aria-label={tx('List pages', 'صفحات القائمة')}><button type="button" className="secondary" disabled={!current} onClick={() => setPage(current - 1)}>{tx('Previous', 'السابق')}</button><span role="status">{current + 1} / {pages} · {items.length} {tx('results', 'نتيجة')}</span><button type="button" className="secondary" disabled={current + 1 >= pages} onClick={() => setPage(current + 1)}>{tx('Next', 'التالي')}</button></nav>}</>;
}
