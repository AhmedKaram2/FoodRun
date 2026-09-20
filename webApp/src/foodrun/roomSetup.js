// A delivery instruction keeps optional addresses compatible with existing hubs.
export function deliveryDestination(delivery, address = '') {
  return delivery ? address.trim() || 'The selected orderer will arrange delivery with the restaurant.' : '';
}
