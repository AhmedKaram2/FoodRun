export const emptyQuickChoice = () => ({people:[],purpose:'',style:'names',groupId:'',audience:'participants'});
export function quickChoiceGroups(home) {
  return [...new Map([...(home?.friendGroups || []).map(group => ({group,ownerId:home.profile.userId,ownerName:home.profile.name})),...(home?.joinedFriendGroups || [])].map(group => [`${group.ownerId}:${group.group.id}`,group])).values()];
}
export function groupParticipants(membership) {
  const contacts = [{userId:membership.ownerId,name:membership.ownerName},...membership.group.members];
  const people = contacts.map(contact => {
    const email = String(contact.email || '').trim().toLowerCase();
    return {id:contact.userId ? `user:${contact.userId}` : email ? `email:${email}` : '',name:String(contact.name || email).trim(),active:true};
  }).filter(person => person.id && person.name);
  return [...new Map(people.map(person => [person.id,person])).values()];
}
export function addQuickName(state, rawName, id) {
  const name = rawName.trim().replace(/\s+/g,' ');
  if(!name) throw Error('Enter a name.');
  if(name.length > 80) throw Error('Keep the name to 80 characters or fewer.');
  if(state.people.some(person => person.name.toLocaleLowerCase() === name.toLocaleLowerCase())) throw Error('This name is already included.');
  return {...state,people:[...state.people,{id:`name:${id}`,name,active:true}]};
}
