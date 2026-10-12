// Display the deadline only; the server changes the payment status.
export function paymentConfirmationDeadline(transfer) {
  const created = Number(transfer?.createdAt);
  if (String(transfer?.status).toLowerCase() !== 'declared' || transfer.refund || String(transfer.id).startsWith('wallet-') || !Number.isSafeInteger(created) || created <= 0) return null;
  return created + 12 * 60 * 60 * 1000;
}
