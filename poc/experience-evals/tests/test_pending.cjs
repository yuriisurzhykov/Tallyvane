const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const scriptPath = path.join(__dirname, '../web/pending.js');

test('lost response and page reload reuse message ID without retaining API key', () => {
  assert.ok(fs.existsSync(scriptPath), 'pending request helper must exist');
  const storage = {value:null, getItem(){return this.value;}, setItem(_key,value){this.value=value;}};
  const ctx = {globalThis:{}};
  vm.runInNewContext(fs.readFileSync(scriptPath,'utf8'),ctx);
  const Pending=ctx.globalThis.LabPending;
  let allocated=0;
  const pending=new Pending(storage,()=>`id-${++allocated}`);
  const body={session_id:'s1',provider:'p1',model:'m',text:'hello',context:{},api_key:'never-store-me'};
  const id=pending.prepare(body);
  // Server may have committed the first attempt: no authoritative response arrived.
  assert.equal(pending.prepare(body),id);
  const restored=new Pending(storage,()=>`id-${++allocated}`);
  assert.equal(restored.prepare(body),id);
  assert.ok(!storage.value.includes('never-store-me'));
  restored.clear('s1');
  assert.notEqual(restored.prepare(body),id);
  assert.notEqual(restored.prepare({...body,text:'different'}),id);
});
