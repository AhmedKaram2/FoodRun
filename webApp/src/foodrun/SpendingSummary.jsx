import React from 'react';
import { t } from './i18n.js';
import { money } from './client.js';

const meals = { breakfast: 'Breakfast', lunch: 'Lunch', dinner: 'Dinner', other: 'Other' };
export default function SpendingSummary({ summary }) {
  return <section data-mobile-section="spending" data-mobile-label={t('Spending')} className="card spending-summary">
    <h3>{t('My spending')}</h3>
    <p className="muted">{t('Confirmed payments for your own food. Wallet top-ups and money covering others are excluded.')}</p>
    {!summary ? <p>{t('Refresh to load spending statistics.')}</p> : <>
      <div className="spending-periods">{summary.periods.map(period => <article className="spending-period" key={`${period.period}:${period.currency}`}>
        <h4>{t(period.period === 'week' ? 'This week' : 'This month')}</h4>
        <small dir="ltr">{period.startDate} – {period.endDate}</small>
        <p className="spending-total">{money(period.total, period.currency)}</p>
        <p className="muted">{t('Net spending')}</p>
        {period.meals.map(meal => <div className="spending-meal" key={meal.meal}>
          <span>{t(meals[meal.meal] || 'Other')}</span><strong>{money(meal.total, period.currency)}</strong>
          <meter aria-label={t(meals[meal.meal] || 'Other')} min="0" max={Math.max(1, ...period.meals.map(row => Math.max(0,row.total)))} value={Math.max(0,meal.total)} />
        </div>)}
        <p className="muted">{t('Paid')} · {money(period.paid, period.currency)}<br />{t('Refunds')} · {money(period.refunds, period.currency)}</p>
      </article>)}</div>
      <small>{t('Week: Monday–Sunday. Meals follow the room name; unlabeled meals appear under Other.')}<br />{t('Confirmed payments, by recorded payment date.')} · {summary.timeZone}</small>
    </>}
  </section>;
}
