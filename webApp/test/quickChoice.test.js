import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyQuickChoice,addQuickName,groupParticipants,quickChoiceGroups} from '../src/foodrun/quickChoice.js';
test('group selection uses stable user identities, includes the creator, and never mutates the group',()=>{
  const membership={ownerId:'owner',ownerName:'Owner',group:{id:'g',name:'Friends',members:[{userId:'a',name:'Alice'},{userId:'a',name:'Alice new name'},{email:'B@EXAMPLE.test',name:'Bob'}]}};
  const before=JSON.stringify(membership),people=groupParticipants(membership);
  assert.equal(people.length,3);assert.ok(people.every(person=>person.active));assert.equal(people[1].id,'user:a');assert.equal(people[2].id,'email:b@example.test');
  assert.equal(JSON.stringify(membership),before);
  const exclude=people.map(person=>({...person,active:person.id!=='user:a'}));assert.equal(exclude.filter(person=>person.active).length,2);assert.equal(membership.group.members.length,3);
});
test('custom names allow every participant to be removed and empty wheels stay empty',()=>{
  const state=addQuickName(emptyQuickChoice(),'  Omar   Ali ','id');assert.equal(state.people[0].name,'Omar Ali');
  assert.throws(()=>addQuickName(state,'omar ali','other'),/already included/);
  assert.throws(()=>addQuickName(state,'   ','other'),/Enter a name/);
  assert.equal(state.people.filter(person=>person.id!=='name:id').length,0);
  assert.equal(emptyQuickChoice().purpose,'');assert.equal(emptyQuickChoice().style,'names');
});
test('owned and joined groups are selectable even when not favourites and duplicate membership is removed',()=>{
  const group={id:'g',name:'Team',favourite:false,members:[]};
  assert.equal(quickChoiceGroups({profile:{userId:'owner',name:'Owner'},friendGroups:[group],joinedFriendGroups:[{group,ownerId:'owner',ownerName:'Owner'}]}).length,1);
});
