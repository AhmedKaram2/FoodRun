export const WHEEL_PALETTE = [
  '#f49a79', '#f5cb69', '#bad4ad', '#b8cbeb', '#cebae4',
  '#f2b4bd', '#eab88a', '#cada85', '#8dcbc3', '#e7c896',
];

export function spinRotation(spin, now) {
  const winnerIndex = spin.memberIds.indexOf(spin.winnerId);
  const target = ((-winnerIndex * 360 / spin.memberIds.length) % 360 + 360) % 360;
  const progress = Math.min(1, Math.max(0, (now - spin.startAt) / spin.duration));
  return (spin.turns * 360 + target) * (1 - (1 - progress) ** 4);
}

export function polarPoint(angle, radius, center = 200) {
  const radians = angle * Math.PI / 180;
  return [center + radius * Math.cos(radians), center + radius * Math.sin(radians)];
}

export function wheelSlicePath(index, count, radius = 172, center = 200) {
  const slice = 360 / count;
  const middle = -90 + index * slice;
  const [startX, startY] = polarPoint(middle - slice / 2, radius, center);
  const [endX, endY] = polarPoint(middle + slice / 2, radius, center);
  return `M ${center} ${center} L ${startX} ${startY} A ${radius} ${radius} 0 ${slice > 180 ? 1 : 0} 1 ${endX} ${endY} Z`;
}

export function wheelLabel(name = '') {
  const characters = Array.from(name.trim());
  return characters.length > 14 ? `${characters.slice(0, 13).join('')}…` : characters.join('');
}
