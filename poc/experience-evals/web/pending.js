'use strict';

// Retain an idempotency key through lost responses and reloads, never auth credentials.
globalThis.LabPending = class LabPending {
  constructor(storage, allocate = () => crypto.randomUUID()) {
    this.storage = storage;
    this.allocate = allocate;
    try { this.items = JSON.parse(storage.getItem('tallyvane-pending')) || {}; }
    catch (_) { this.items = {}; }
  }
  save() { try { this.storage.setItem('tallyvane-pending', JSON.stringify(this.items)); } catch (_) {} }
  prepare(body) {
    const safe = {};
    for (const key of ['session_id', 'provider', 'model', 'text', 'context']) safe[key] = body[key];
    const fingerprint = JSON.stringify(safe);
    const previous = this.items[body.session_id];
    if (previous?.fingerprint === fingerprint) return previous.id;
    const id = this.allocate();
    this.items[body.session_id] = {fingerprint, id};
    this.save();
    return id;
  }
  clear(sessionId) { delete this.items[sessionId]; this.save(); }
};
