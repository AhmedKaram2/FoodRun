import { money } from './client';

export default function OfflineReceipts({ receipts, onBack, onClear, language = 'en' }) {
  const t = (en, ar) => language === 'ar' ? ar : en;
  return <main className="app-shell" dir={language === 'ar' ? 'rtl' : 'ltr'}>
    <button className="back" onClick={onBack}>{t('← Back', 'رجوع ←')}</button>
    <h1>{t('Downloaded receipts', 'الإيصالات المحفوظة')}</h1>
    <p>{t('Your last downloaded receipts on this device. Amounts may have changed since the last sync. Connect to the room before making a payment.', 'آخر الإيصالات المحفوظة على هذا الجهاز. قد تتغير المبالغ بعد آخر مزامنة. اتصل بالغرفة قبل الدفع.')}</p>
    <p className="muted">{t('Up to 200 personal receipts. Signing out removes them from this browser.', 'حتى ٢٠٠ إيصال شخصي. تسجيل الخروج يحذفها من هذا المتصفح.')}</p>
    {!!receipts.length && <button className="secondary" onClick={onClear}>{t('Remove downloaded receipts', 'حذف الإيصالات المحفوظة')}</button>}
    {!receipts.length && <p className="card">{t('No downloaded receipts yet. Open an order while connected to save your receipt.', 'لا توجد إيصالات محفوظة. افتح طلباً أثناء الاتصال لحفظ إيصالك.')}</p>}
    <div className="grid two">{receipts.map(record => <article className="card" key={record.id}>
      <h2>{record.restaurant}</h2><p>{record.roomName} · #{record.orderNumber}</p>
      <p className="muted">{t('Last downloaded', 'آخر حفظ')}: {new Date(record.savedAt).toLocaleString(language === 'ar' ? 'ar-AE' : 'en-AE')}</p>
      {record.receipt.lines.map((line, index) => <p key={index}>{line.quantity} × {line.description} · {money(line.amount, record.receipt.currency)}{line.notes && <small> · {line.notes}</small>}</p>)}
      <hr /><p>{t('Total', 'الإجمالي')}: {money(record.receipt.total, record.receipt.currency)}</p>
      <p>{t('Confirmed paid', 'المدفوع المؤكد')}: {money(record.receipt.paid, record.receipt.currency)}</p>
      <b>{t('Balance at last sync', 'الرصيد عند آخر مزامنة')}: {money(record.receipt.balance, record.receipt.currency)}</b>
    </article>)}</div>
  </main>;
}
