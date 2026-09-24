export const WHEEL_PALETTE = [
  '#f49a79', '#f5cb69', '#bad4ad', '#b8cbeb', '#cebae4',
  '#f2b4bd', '#eab88a', '#cada85', '#8dcbc3', '#e7c896',
];

export function spinRotation(spin, now) {
  const winnerIndex = spin.memberIds.indexOf(spin.winnerId);
  const target = ((-wheelSlice(winnerIndex, spin.memberIds.length, spin.weights).center) % 360 + 360) % 360;
  const progress = Math.min(1, Math.max(0, (now - spin.startAt) / spin.duration));
  return (spin.turns * 360 + target) * (1 - (1 - progress) ** 4);
}

export function polarPoint(angle, radius, center = 200) {
  const radians = angle * Math.PI / 180;
  return [center + radius * Math.cos(radians), center + radius * Math.sin(radians)];
}

export function wheelSlice(index, count, weights = []) {
  const values = weights.length === count ? weights : Array(count).fill(1);
  const total = values.reduce((sum, value) => sum + value, 0);
  const before = values.slice(0, index).reduce((sum, value) => sum + value, 0);
  return { sweep: values[index] * 360 / total, center: (before + values[index] / 2 - values[0] / 2) * 360 / total };
}

export function wheelSlicePath(index, count, radius = 172, center = 200, weights = []) {
  const geometry = wheelSlice(index, count, weights);
  const slice = geometry.sweep;
  const middle = -90 + geometry.center;
  const [startX, startY] = polarPoint(middle - slice / 2, radius, center);
  const [endX, endY] = polarPoint(middle + slice / 2, radius, center);
  return `M ${center} ${center} L ${startX} ${startY} A ${radius} ${radius} 0 ${slice > 180 ? 1 : 0} 1 ${endX} ${endY} Z`;
}

export function wheelLabel(name = '') {
  const characters = Array.from(name.trim());
  return characters.length > 14 ? `${characters.slice(0, 13).join('')}…` : characters.join('');
}

export function runningNameIndex(spin, now) {
  if (now >= spin.startAt + spin.duration) return spin.memberIds.indexOf(spin.winnerId);
  const progress = Math.max(0, Math.min(1, (now - spin.startAt) / spin.duration));
  const steps = (spin.turns || 7) * spin.memberIds.length + spin.memberIds.indexOf(spin.winnerId);
  return Math.floor(steps * (1 - (1 - progress) ** 3)) % spin.memberIds.length;
}
