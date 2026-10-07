import { useEffect, useState } from 'react';
import { getLanguage, t } from './i18n.js';
const tx = (en, ar) => getLanguage()==='ar' ? ar : en;
export default function NotificationPreferences({data}) {
  const current = data.home.notificationPreferences || {pushEnabled:true,emailEnabled:true};
  const [form,setForm]=useState(current), [dirty,setDirty]=useState(false);
  useEffect(()=>{if(!dirty)setForm(current);},[current.pushEnabled,current.emailEnabled,data.home.profile.userId,dirty]);
  return <section className="card stack notification-preferences" data-mobile-section="notifications" data-mobile-label={tx('Notifications','الإشعارات')}><h2>{tx('Notification preferences','تفضيلات الإشعارات')}</h2>
    <label className="check"><input type="checkbox" checked={form.pushEnabled} onChange={event=>{setDirty(true);setForm({...form,pushEnabled:event.target.checked});}} />{tx('App and website push notifications','الإشعارات الفورية للتطبيق والموقع')}</label>
    <label className="check"><input type="checkbox" checked={form.emailEnabled} onChange={event=>{setDirty(true);setForm({...form,emailEnabled:event.target.checked});}} />{tx('Receive email invitations and reminders','استلام الدعوات والتذكيرات بالبريد الإلكتروني')}</label>
    <p className="field-help">{tx('These preferences apply across your devices. Your activity remains available in the app.','تُطبَّق هذه التفضيلات على جميع أجهزتك. تظل أنشطتك متاحة داخل التطبيق.')}</p>
    <button className="primary" disabled={data.busy || !dirty} onClick={async()=>{const reply=await data.send('SET_NOTIFICATION_PREFERENCES',{notificationPreferences:form});if(reply){setDirty(false);data.setNotice(tx('Notification preferences saved.','تم حفظ تفضيلات الإشعارات.'));}}}>{tx('Save notification preferences','حفظ تفضيلات الإشعارات')}</button>
    {form.pushEnabled && data.enablePush && <button className="secondary" onClick={data.enablePush}>{tx('Allow notifications on this device','السماح بالإشعارات على هذا الجهاز')}</button>}
  </section>;
}
