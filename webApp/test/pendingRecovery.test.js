import test from 'node:test';
import assert from 'node:assert/strict';
import {pendingRecovery} from '../src/foodrun/pendingRecovery.js';
const flush=()=>new Promise(resolve=>setImmediate(resolve));
test('lost acknowledgement is resolved with status reads and the mutation is not replayed',async()=>{
 const calls=[],completed=[];let next;
 const stop=pendingRecovery({id:'saved-command',current:()=>true,query:async id=>{calls.push(id);return {code:calls.length===1?'COMMAND_UNKNOWN':'COMMAND_FOUND',room:{revision:8}};},complete:reply=>completed.push(reply),schedule:fn=>{next=fn;},cancel:()=>{}});
 await flush();assert.equal(completed.length,0);await next();assert.equal(completed.length,1);assert.deepEqual(calls,['saved-command','saved-command']);stop();
});
test('late status from another account or replaced request cannot clear the current draft',async()=>{
 let resolve,current='old';const completed=[];
 const stop=pendingRecovery({id:'old',current:id=>current===id,query:()=>new Promise(done=>{resolve=done;}),complete:reply=>completed.push(reply)});
 current='new';resolve({code:'COMMAND_FOUND'});await flush();assert.equal(completed.length,0);stop();
});
test('network failure keeps the saved request and schedules only a status check',async()=>{
 let count=0,next;const completed=[];
 const stop=pendingRecovery({id:'saved',current:()=>true,query:async()=>{count++;throw Error('offline');},complete:reply=>completed.push(reply),schedule:fn=>{next=fn;},cancel:()=>{}});
 await flush();assert.equal(count,1);assert.equal(typeof next,'function');assert.equal(completed.length,0);stop();
});
