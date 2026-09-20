// Visibility is a convenience. The server checks Firebase identity on every admin request.
export function canAccessAdmin(user) {
  return user?.emailVerified === true && user?.email?.toLowerCase() === '1ahmedkaram1@gmail.com';
}
