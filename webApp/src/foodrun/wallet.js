export function myWalletBalances(wallet, userId, currency) {
  return (wallet?.balances || []).filter(value => value.customerId === userId && (!currency || value.currency === currency));
}
export function walletAvailable(wallet, userId, currency) {
  return myWalletBalances(wallet, userId, currency).reduce((total, value) => total + value.available, 0);
}
export function walletPaymentGroups(wallet, userId) {
  const groups = new Map();
  for (const payment of wallet?.payments || []) {
    if (payment.holderId !== userId || payment.status !== 'OWING') continue;
    const key = `${payment.recipientId}:${payment.currency}`;
    const group = groups.get(key) || { key, recipientId: payment.recipientId, recipientName: payment.recipientName, currency: payment.currency, amount: 0, payments: [] };
    group.amount += payment.amount; group.payments.push(payment); groups.set(key, group);
  }
  return [...groups.values()];
}
export function walletTotals(balances) {
  return Object.entries(balances.reduce((totals, balance) => ({ ...totals, [balance.currency]: (totals[balance.currency] || 0) + balance.available }), {}));
}
