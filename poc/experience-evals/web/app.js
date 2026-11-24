'use strict';

const $ = id => document.getElementById(id);
const tables = ['companies', 'projects', 'tasks', 'actions'];
const contextFields = ['company_id', 'project_id', 'task_id', 'action_id'];
const kinds = ['Компания', 'Проект', 'Задача', 'Действие', 'Результат'];
const colors = ['#47765b', '#6b8b9d', '#a19b71', '#ad87a9', '#c5a360'];
const statuses = {not_stated:'Не указан', achieved:'Достигнут', not_achieved:'Не достигнут', in_progress:'В процессе', rejected:'Отклонён'};
let boot, session, busy = false, selected = null, eventIndex = -1, tab = 'changes', zoom = 0.68, graphSize = [1200, 400];
let openSessions = [];
const pending = new LabPending(sessionStorage);

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
function notice(text = '', error = false) {
  $('notice').textContent = text;
  $('notice').hidden = !text;
  $('notice').className = 'notice' + (error ? ' error' : '');
}
async function api(path, body) {
  const response = await fetch(path, body === undefined ? {cache:'no-store'} : {
    method:'POST', headers:{'Content-Type':'application/json', 'X-Lab-Token':boot.token}, body:JSON.stringify(body)
  });
  const data = await response.json();
  if (!response.ok) { const error = new Error(data.error || `HTTP ${response.status}`); error.authoritative = true; throw error; }
  return data;
}
function setBusy(value) {
  busy = value;
  document.querySelectorAll('button, select, #model').forEach(node => { node.disabled = value; });
  $('message').disabled = value;
  $('send').textContent = value ? 'Обработка…' : (isDemo() ? 'Следующий шаг ↗' : 'Отправить ↗');
}
function isDemo() { return $('provider').value === 'offline-demo'; }
function providerUI() {
  const demo = isDemo();
  const p = boot.providers.find(item => item.name === $('provider').value);
  $('model').value = p ? p.model : 'Готовые ответы сценария';
  $('model').readOnly = demo;
  $('demo-hint').hidden = !demo;
  $('mode-badge').textContent = demo ? 'Демо · без LLM' : 'Live API';
  $('mode-badge').className = 'pill ' + (demo ? 'amber' : 'live');
  $('provider-note').textContent = demo ? 'Только готовые реплики. Это проверка механики, а не качества модели.' :
    `${p.protocol} · ${p.base_url}. ` + (p.key_configured ? 'Ключ задан в providers.local.yaml.' : 'Ключ не задан. Укажите api_key в providers.local.yaml, если API требует авторизацию.');
  refreshComposer();
}
function refreshComposer() {
  if (!session) return;
  if (isDemo()) {
    $('message').value = session.next_demo?.text || '';
    $('message').readOnly = true;
    $('message').placeholder = 'Сценарий завершён. Выберите новый или перейдите на API для своего текста.';
    $('send').disabled = busy || !session.next_demo;
    $('send').textContent = busy ? 'Обработка…' : 'Следующий шаг ↗';
    $('composer-note').textContent = session.next_demo ? `Шаг ${session.step + 1} · готовая реплика` : 'Сценарий завершён';
  } else {
    $('message').readOnly = false;
    $('message').placeholder = 'Расскажите об опыте или уточните предыдущую запись…';
    $('send').disabled = busy;
    $('send').textContent = busy ? 'Обработка…' : 'Отправить ↗';
    $('composer-note').textContent = 'Ctrl / ⌘ + Enter';
  }
  refreshContext();
}
function rememberSession() {
  if (!openSessions.some(s => s.id === session.id)) openSessions.push({id:session.id, label:`Сессия ${openSessions.length+1}`});
  $('sessions').replaceChildren(...openSessions.map(s => new Option(s.label, s.id)));
  $('sessions').value = session.id;
  sessionStorage.setItem('tallyvane-lab', JSON.stringify({ids:openSessions, current:session.id}));
}
function acceptSession(data) {
  session = data;
  selected = null;
  eventIndex = session.events.length - 1;
  rememberSession();
  render();
}
function render() {
  $('revision').textContent = `Ревизия ${session.state.revision}`;
  $('scenario').value = session.scenario || '';
  scenarioNote();
  renderChat(); renderGraph(); renderEntity(); renderDiagnostics(); refreshComposer();
}
function scenarioNote() {
  const s = boot.scenarios.find(s => s.id === $('scenario').value);
  $('scenario-note').textContent = s ? `${s.turns} реплик · ${s.description}` : 'Начните с любого рассказа. Для свободного ввода нужен API.';
}
function label(entity) { return entity.name || entity.title || entity.description || ''; }
function activeContext() {
  if (isDemo()) return session.context || {};
  if (!selected) return {};
  let {table, id} = selected;
  const result = {};
  for (let level = tables.indexOf(table); level >= 0; level--) {
    const entity = session.state[tables[level]][id];
    if (!entity) return {};
    result[contextFields[level]] = id;
    id = entity[contextFields[level-1]];
  }
  return result;
}
function refreshContext() {
  const ctx = activeContext();
  const names = tables.map((t, i) => session.state[t][ctx[contextFields[i]]]).filter(Boolean).map(label);
  $('context-clear').textContent = names.length ? names.join(' / ') : 'вся история';
  $('context-clear').title = isDemo() ? 'В демо контекст задан сценарием' : 'Нажмите, чтобы сбросить контекст';
}
function money(cost, execution) { return cost == null ? 'стоимость неизвестна' : execution === 'offline_oracle' ? '$0 · демо' : `$${cost.toFixed(6)}`; }
function renderChat() {
  const chat = $('chat'); chat.replaceChildren();
  if (!session.events.length) {
    const welcome = el('div','welcome');
    welcome.append(el('strong','',isDemo() ? 'Посмотрите, как меняются данные' : 'Расскажите о своей работе'),
      el('p','',isDemo() ? 'Готовый сценарий покажет добавление фактов и исправления. Каждый шаг можно раскрыть и проверить.' :
      'Начните с компании, проекта и задачи. Затем добавляйте действия, технологии и результаты. Можно писать: «ошибся, на самом деле…».'));
    chat.append(welcome);
  }
  session.events.forEach((event,index) => {
    const card = el('div','message-card');
    card.append(el('div','message-label',`ВЫ / ${String(index+1).padStart(2,'0')}`), el('div','user-text',event.message.text));
    const button = el('button','event-button' + (index === eventIndex ? ' active' : ''));
    const top = el('div','event-top');
    const dot = el('span','status-dot');
    if (event.status !== 'applied') dot.style.background = '#b06458';
    top.append(dot,el('span','',event.status === 'applied' ? `Применено операций: ${event.proposal?.changes.length || 0}` : 'Изменения не применены'));
    button.append(top);
    if (event.error) button.append(el('div','error-text',event.error));
    for (const unresolved of event.proposal?.unresolved_references || []) {
      button.append(el('div','unresolved',`Нужно уточнить: «${unresolved.text}» · кандидатов: ${unresolved.candidate_ids.length}`));
    }
    const meta = el('div','event-meta');
    meta.append(el('span','',event.execution === 'offline_oracle' ? 'SCRIPTED / NO LLM' : `${event.provider} / ${event.model}`));
    if (event.latency_ms != null && event.execution !== 'offline_oracle') meta.append(el('span','',`${(event.latency_ms/1000).toFixed(2)} s`));
    meta.append(el('span','',money(event.cost_usd,event.execution)));
    button.append(meta);
    button.addEventListener('click',() => { eventIndex = index; renderChat(); renderDiagnostics(); renderGraph(); });
    card.append(button); chat.append(card);
  });
  chat.scrollTop = chat.scrollHeight;
}
function svg(tag, attrs, text) {
  const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [key,value] of Object.entries(attrs)) node.setAttribute(key,String(value));
  if (text !== undefined) node.textContent = text;
  return node;
}
function wrap(text, width=24, max=2) {
  const words = String(text).split(/\s+/); const lines=[]; let current='';
  for (const word of words) {
    if ((current+' '+word).trim().length > width && current) { lines.push(current); current=word; }
    else current=(current+' '+word).trim();
  }
  if (current) lines.push(current);
  if (lines.length > max) return [...lines.slice(0,max-1), lines[max-1].slice(0,width-1)+'…'];
  return lines.map(s => s.length > width+6 ? s.slice(0,width+3)+'…' : s);
}
function renderGraph() {
  const state = session.state, nodes=[], edges=[];
  const event = session.events[eventIndex];
  const changed = new Set((event?.diff || []).map(d => d.path.split('/')[2]));
  const root = {children:[]};
  function add(entity, level, parent) {
    const table = tables[level];
    const node={entity,level,table,id:entity.id,children:[],text:label(entity)};
    parent.children.push(node); nodes.push(node);
    if (level < 3) Object.values(state[tables[level+1]]).filter(e => e[contextFields[level]] === entity.id).forEach(e => add(e,level+1,node));
    else {
      const r=entity.result;
      const child={entity:r,level:4,table:'actions',id:entity.id,children:[],text:r.description || statuses[r.status] || r.status};
      node.children.push(child); nodes.push(child);
    }
    return node;
  }
  Object.values(state.companies).forEach(e => add(e,0,root));
  let cursor=25;
  function layout(node) {
    if (!node.children.length) { node.y=cursor; cursor+=116; }
    else { node.children.forEach(layout); node.y=(node.children[0].y+node.children.at(-1).y)/2; }
    node.x=24+node.level*242;
    node.children.forEach(child => edges.push([node,child]));
  }
  root.children.forEach(layout);
  const graph=$('graph'); graph.replaceChildren();
  $('graph-empty').hidden=!!nodes.length;
  $('node-count').textContent=nodes.length;
  if (!nodes.length) { graph.setAttribute('width','0'); graph.setAttribute('height','0'); return; }
  graphSize=[1240,Math.max(cursor,340)]; graph.setAttribute('viewBox',`0 0 ${graphSize[0]} ${graphSize[1]}`);
  for (const [p,c] of edges) graph.append(svg('path',{d:`M ${p.x+210} ${p.y+45} C ${p.x+232} ${p.y+45},${c.x-22} ${c.y+45},${c.x} ${c.y+45}`,fill:'none',stroke:'#d8e1d2','stroke-width':1.5}));
  for (const node of nodes) {
    const active=selected?.id === node.id && (selected?.result === (node.level===4));
    const group=svg('g',{class:'graph-node',transform:`translate(${node.x},${node.y})`,tabindex:0,role:'button','aria-label':`${kinds[node.level]}: ${node.text}`});
    group.append(svg('rect',{width:210,height:90,rx:8,fill:node.entity.retracted ? '#f6f3f0':'#fff',stroke:active ? '#396649' : changed.has(node.id) ? '#d6ae54':'#dfe6d8','stroke-width':active ? 2:1,'stroke-dasharray':node.level===4 && node.entity.status==='not_stated' ? '4 3':'none'}));
    group.append(svg('circle',{cx:14,cy:17,r:3,fill:colors[node.level]}),svg('text',{x:24,y:20,'font-size':9,fill:'#8b9882'},kinds[node.level].toUpperCase()));
    wrap(node.text).forEach((line,i) => group.append(svg('text',{x:13,y:41+i*15,'font-size':11,fill:'#3d523c','font-weight':500},line)));
    const subtitle=node.level===3 ? (node.entity.technologies.join(' · ') || 'Технологии не указаны') : node.level===4 ?
      (node.entity.metrics.length ? `${node.entity.metrics.length} показателей · ${statuses[node.entity.status]}` : statuses[node.entity.status]) : `${node.children.length} ${['','проектов','задач','действий'][node.level+1] || ''}`;
    group.append(svg('text',{x:13,y:77,'font-size':8,fill:'#95a08d'},String(subtitle).slice(0,37)));
    if (changed.has(node.id)) group.append(svg('circle',{cx:196,cy:15,r:3,fill:'#d4aa50'}));
    const choose=() => { selected={table:node.table,id:node.id,result:node.level===4}; renderGraph(); renderEntity(); refreshContext(); };
    group.addEventListener('click',choose); group.addEventListener('keydown',e => { if (e.key==='Enter'||e.key===' ') { e.preventDefault(); choose(); } });
    graph.append(group);
  }
  applyZoom();
}
function applyZoom() {
  $('graph').setAttribute('width',Math.round(graphSize[0]*zoom)); $('graph').setAttribute('height',Math.round(graphSize[1]*zoom));
}
function renderEntity() {
  const host=$('entity'); host.replaceChildren();
  if (!selected) { host.append(el('div','entity-placeholder','Выберите узел графа. Здесь будут его ID, поля и исходные цитаты.')); return; }
  const entity=session.state[selected.table][selected.id];
  if (!entity) { selected=null; renderEntity(); return; }
  const value=selected.result ? entity.result : entity;
  const head=el('div','entity-header'); head.append(el('h3','',selected.result ? 'Результат действия' : label(entity)));
  const badge=el('span','tag',isDemo() ? 'Просмотр' : 'Контекст ввода'); head.append(badge); host.append(head,el('div','entity-id',entity.id));
  const names={reason:'Причина',technologies:'Технологии',approaches:'Подходы',requirements:'Требования',responsibility:'Ответственность',deadline:'Сроки',status:'Статус',description:'Описание',metrics:'Показатели',lesson:'Вывод',retracted:'Отозвано'};
  const dl=el('dl','entity-grid');
  for (const [key,v] of Object.entries(value)) {
    if (['id','name','title','company_id','project_id','task_id','result'].includes(key)) continue;
    dl.append(el('dt','',names[key]||key));
    const dd=el('dd');
    if (Array.isArray(v) && v.every(x=>typeof x==='string')) v.length ? v.forEach(x=>dd.append(el('span','tag',x))) : dd.append(el('span','muted','Не указано'));
    else dd.textContent=v==null ? 'Не указано' : key==='status' ? (statuses[v]||v) : typeof v==='object' ? JSON.stringify(v) : String(v);
    dl.append(dd);
  }
  host.append(dl);
  const quotes=new Set();
  for (const history of session.state.history) {
    for (const change of history.proposal.changes) {
      if (change.action_id===entity.id || change.target_id===entity.id || change.task_id===entity.id || history.allocated_ids[change.ref]===entity.id) quotes.add(change.evidence.quote);
    }
  }
  for (const quote of quotes) host.append(el('div','quote',quote));
}
function jsonBlock(value) { return el('pre','',typeof value==='string' ? value : JSON.stringify(value,null,2)); }
function disclosure(label,value) { const d=el('details'); d.append(el('summary','',label),jsonBlock(value)); return d; }
function renderDiagnostics() {
  const host=$('diagnostics'); host.replaceChildren();
  const event=session.events[eventIndex];
  $('step-label').textContent=event ? `Шаг ${eventIndex+1} / ${session.events.length} · ${event.status}` : 'Отправьте первое сообщение';
  $('diff-count').textContent=event?.diff.length||0;
  if (tab==='state') { host.append(jsonBlock(session.state)); return; }
  if (!event) { host.append(el('p','hint','После отправки здесь появятся запрос, ответ модели и точные изменения данных.')); return; }
  if (tab==='request') {
    host.append(el('p','hint','Логический запрос к модели. Адаптер оборачивает его в формат выбранного API. Ключ авторизации здесь не отображается.'),
      jsonBlock(event.input),disclosure('Системный промпт',event.prompt),disclosure('JSON Schema ответа',event.output_schema));
  } else if (tab==='response') {
    if (event.error) host.append(el('p','error-text',event.error));
    host.append(el('p','hint',`Время: ${event.latency_ms==null ? 'неизвестно' : (event.latency_ms/1000).toFixed(2)+' s'} · ${money(event.cost_usd,event.execution)} · токены: ${event.usage ? JSON.stringify(event.usage) : 'нет данных'}`));
    host.append(jsonBlock(event.response_text ?? event.raw_response ?? 'Ответ не получен'));
    if (event.raw_response) host.append(disclosure('Полный ответ API',event.raw_response));
  } else {
    if (!event.diff.length) host.append(el('p','hint',event.status==='applied' ? 'Поля графа не изменились.' : 'Ответ отклонён. Данные графа остались прежними.'));
    else {
      const table=el('table','diff-table'), head=el('thead'), tr=el('tr');
      ['ПОЛЕ / ID','БЫЛО','СТАЛО'].forEach(x=>tr.append(el('th','',x))); head.append(tr); table.append(head);
      const body=el('tbody');
      for (const d of event.diff) { const row=el('tr'); row.append(el('td','',d.path),el('td','',d.before==null?'∅':JSON.stringify(d.before,null,2)),el('td','',d.after==null?'∅':JSON.stringify(d.after,null,2))); body.append(row); }
      table.append(body); host.append(table);
    }
    if (event.proposal) host.append(disclosure('Предложенные операции и цитаты',event.proposal));
    if (event.applied_proposal && JSON.stringify(event.applied_proposal)!==JSON.stringify(event.proposal))
      host.append(disclosure('Применённые операции после нормализации терминов',event.applied_proposal));
    host.append(disclosure('Состояние сразу после этого шага',event.state_after));
  }
}
async function guarded(fn) {
  if (busy) return;
  notice(); setBusy(true);
  try { await fn(); } catch(error) { notice(error.message,true); }
  finally { setBusy(false); refreshComposer(); }
}
$('composer').addEventListener('submit',event => {
  event.preventDefault();
  const text=$('message').value;
  if (!text.trim()) return;
  guarded(async()=>{
    const body={session_id:session.id,provider:$('provider').value,text,
      context:activeContext()};
    if (!isDemo()) body.model=$('model').value;
    body.message_id=isDemo()?session.next_demo.id:pending.prepare(body);
    let next;
    try { next=await api('/api/turn',body); pending.clear(session.id); }
    catch(error) {
      // 409 may mean the first attempt is still running. Keep its ID for a later retry.
      if (error.authoritative && !error.message.includes('already running')) pending.clear(session.id);
      throw error;
    }
    session=next; eventIndex=session.events.length-1;
    if (!isDemo()) $('message').value='';
    render();
    if (session.events.at(-1)?.status !== 'applied') notice('Сообщение сохранено, но изменения не применены. Подробности в ответе API.',true);
  });
});
$('message').addEventListener('keydown',e=>{if (e.key==='Enter'&&(e.ctrlKey||e.metaKey)) {e.preventDefault(); $('composer').requestSubmit();}});
$('provider').addEventListener('change',()=>{ $('message').value=''; providerUI(); });
$('scenario').addEventListener('change',scenarioNote);
$('new-session').addEventListener('click',()=>guarded(async()=>{
  const scenario=$('scenario').value||null;
  if (!scenario && isDemo()) { notice('Выберите сценарий для демо или живой API для пустой истории.'); return; }
  acceptSession(await api('/api/sessions',{scenario}));
}));
$('fork').addEventListener('click',()=>guarded(async()=>{acceptSession(await api('/api/fork',{session_id:session.id})); notice('Создана независимая копия. Выберите другую модель и отправьте следующую реплику.');}));
$('sessions').addEventListener('change',()=>guarded(async()=>{acceptSession(await api('/api/view',{session_id:$('sessions').value}));}));
$('context-clear').addEventListener('click',()=>{selected=null;renderGraph();renderEntity();refreshContext();});
$('export').addEventListener('click',()=>{
  const blob=new Blob([JSON.stringify({format:'tallyvane-experience-lab-v1',exported_at:new Date().toISOString(),...session},null,2)],{type:'application/json'});
  const url=URL.createObjectURL(blob),a=el('a'); a.href=url;a.download=`tallyvane-session-${session.id.slice(0,8)}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
});
document.querySelectorAll('[data-tab]').forEach(button=>button.addEventListener('click',()=>{
  tab=button.dataset.tab;document.querySelectorAll('[data-tab]').forEach(b=>b.setAttribute('aria-selected',String(b===button)));renderDiagnostics();
}));
$('zoom-in').addEventListener('click',()=>{zoom=Math.min(1.6,zoom+.1);applyZoom();});
$('zoom-out').addEventListener('click',()=>{zoom=Math.max(.3,zoom-.1);applyZoom();});
$('zoom-fit').addEventListener('click',()=>{zoom=Math.max(.3,Math.min(1,($('graph-stage').clientWidth-20)/graphSize[0]));applyZoom();});
async function init() {
  boot=await api('/api/bootstrap');
  $('provider').append(new Option('Демо без API-ключа','offline-demo'));
  boot.providers.forEach(p=>$('provider').append(new Option(p.name,p.name)));
  boot.scenarios.forEach(s=>$('scenario').append(new Option(s.id,s.id)));
  let recovered=false;
  try {
    const saved=JSON.parse(sessionStorage.getItem('tallyvane-lab'));
    if (saved?.current) { const value=await api('/api/view',{session_id:saved.current});openSessions=saved.ids;acceptSession(value);recovered=true; }
  } catch (_) { sessionStorage.removeItem('tallyvane-lab'); }
  if (!recovered) acceptSession(await api('/api/sessions',{scenario:'new_action_then_corrections'}));
  providerUI();
}
init().catch(error=>notice(`Не удалось подключиться к стенду: ${error.message}`,true));
