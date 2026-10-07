export const friendGroupKey = value => `${value.ownerId}:${value.group.id}`;
export function roomFriendGroups(home) {
  const values = [...(home.friendGroups || []).filter(group => group.favourite).map(group => ({group, ownerId: home.profile.userId, ownerName: home.profile.name})), ...(home.joinedFriendGroups || [])];
  return [...new Map(values.map(value => [friendGroupKey(value), value])).values()];
}
