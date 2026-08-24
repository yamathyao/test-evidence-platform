(() => {
  const topology = window.TraceTopology;
  const listQuery = window.ConsoleListQuery;
  const optionsLoader = window.ConsoleOptionsLoader;
  const blackboxCountdown = window.BlackboxCountdown;
  const state = {
    profiles: [], cases: [], runs: [], currentRun: null, currentTrace: null,
    profileOptions: [], caseOptions: [], traceLayout: null, selectedSpanId: null, traceView: 'topology', pollTimer: null,
    activeBlackboxRunId: null, blackboxPollTimer: null,
    payloadRequest: null, payloadCache: new Map(), payloadViews: new Map(), unavailablePayloads: new Set(), blackboxCountdownStop: null,
    lists: { profiles: listQuery.createListQuery(), cases: listQuery.createListQuery(), runs: listQuery.createListQuery() }
  };
  const byId = (id) => document.querySelector(`#${id}`);
  const notice = byId('notice');
  const text = (value) => value == null ? '' : String(value);

  async function api(path, options = {}) {
    const response = await fetch(path, { cache: 'no-store', headers: { 'Content-Type': 'application/json' }, ...options });
    if (!response.ok) throw new Error(`请求失败 (${response.status})`);
    return response.status === 204 ? null : response.json();
  }

  function setNotice(message, error = false) {
    notice.textContent = message;
    notice.style.color = error ? 'var(--danger)' : 'var(--muted)';
  }

  function startBlackboxWarmup() {
    if (state.blackboxCountdownStop) state.blackboxCountdownStop();
    state.blackboxCountdownStop = blackboxCountdown.startCountdown(6, (remaining) => {
      const captureState = byId('blackbox-capture-state');
      if (remaining === 0) {
        captureState.textContent = '采集中';
        state.blackboxCountdownStop = null;
        setNotice('规则已下发，可以回到产品页面操作。');
        return;
      }
      captureState.textContent = `采集中 · 规则下发中（${remaining}s）`;
      setNotice(`黑盒运行已启动，等待规则下发：${remaining} 秒。`);
    }, (callback, delay) => setTimeout(callback, delay), (timerId) => clearTimeout(timerId));
  }

  function renderBlackboxCapture(run) {
    const panel = byId('blackbox-capture-panel');
    const active = run && (run.status === 'RUNNING' || run.status === 'DRAINING');
    panel.hidden = !active;
    panel.classList.toggle('is-draining', Boolean(run && run.status === 'DRAINING'));
    if (!active) return;
    const collecting = run.status === 'RUNNING';
    byId('blackbox-capture-state').textContent = collecting ? '采集中' : '排空中';
    byId('blackbox-capture-meta').textContent = collecting
      ? `剩余 ${formatSeconds(run.remainingCaptureSeconds)} · 已发现 ${run.rootTraceCount || 0} 条根链路`
      : `已停止规则下发 · 已发现 ${run.rootTraceCount || 0} 条根链路 · 等待在途请求完成`;
    byId('blackbox-stop').hidden = !collecting;
    byId('blackbox-stop').disabled = !collecting;
    byId('run-submit').disabled = true;
    byId('run-submit').textContent = collecting ? '黑盒采集中' : '黑盒排空中';
  }

  function formatSeconds(value) {
    const seconds = Math.max(0, Number(value) || 0);
    return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
  }

  async function monitorBlackboxRun(id) {
    if (state.blackboxPollTimer) clearTimeout(state.blackboxPollTimer);
    state.activeBlackboxRunId = id;
    const poll = async () => {
      try {
        const run = await api(`/api/runs/${id}`);
        if (state.activeBlackboxRunId !== id) return;
        renderBlackboxCapture(run);
        if (run.status === 'RUNNING' || run.status === 'DRAINING') {
          state.blackboxPollTimer = setTimeout(poll, 2000);
          return;
        }
        state.activeBlackboxRunId = null;
        byId('run-submit').disabled = false;
        byId('run-submit').textContent = '启动运行';
        await loadList('runs');
        await showRun(id);
      } catch (error) { setNotice(error.message, true); }
    };
    await poll();
  }

  function element(tag, className, value) {
    const item = document.createElement(tag);
    if (className) item.className = className;
    if (value != null) item.textContent = text(value);
    return item;
  }

  function renderList(target, values, render, loading) {
    target.replaceChildren();
    if (loading) { target.append(element('div', 'empty', '正在加载...')); return; }
    if (!values.length) target.append(element('div', 'empty', '暂无记录'));
    values.forEach((value) => target.append(render(value)));
  }

  function profileRow(profile) {
    const row = element('div', 'row');
    const body = element('div');
    body.append(element('strong', '', profile.name), element('div', 'meta', `v${profile.version}`));
    row.append(body, element('span', 'status', 'PROFILE'));
    return row;
  }

  function caseRow(testCase) {
    const row = element('div', 'row');
    const body = element('div');
    body.append(element('strong', '', testCase.name));
    body.append(element('div', 'meta', `${testCase.triggerType} · ${testCase.timeoutSeconds}s · 原文${testCase.httpPayloadCaptureEnabled ? '开启' : '关闭'}`));
    const action = element('button', 'case-action', testCase.completedAt ? '重新打开' : '完成');
    action.type = 'button';
    action.addEventListener('click', async (event) => {
      event.stopPropagation();
      try { await api(`/api/test-cases/${testCase.id}/${testCase.completedAt ? 'reopen' : 'complete'}`, { method: 'POST' }); await loadList('cases'); await loadOptions(); }
      catch (error) { setNotice(error.message, true); }
    });
    row.append(body, action);
    return row;
  }

  function runRow(run) {
    const row = element('div', 'row');
    const body = element('div');
    body.append(element('strong', '', run.testCaseName));
    body.append(element('div', 'meta', run.failureReason || run.id));
    row.append(body, element('span', `status ${run.status}`, run.status));
    row.addEventListener('click', () => showRun(run.id));
    return row;
  }

  function syncSelectors() {
    const profileSelect = byId('profile-select');
    const filterProfileSelect = byId('case-filter-profile');
    const runSelect = byId('run-case-select');
    const hasProfiles = state.profileOptions.length > 0;
    const profilePlaceholder = option('', hasProfiles ? '请选择采集规则' : '请先创建采集规则');
    profilePlaceholder.disabled = true;
    profilePlaceholder.selected = true;
    profileSelect.replaceChildren(profilePlaceholder);
    profileSelect.disabled = !hasProfiles;
    filterProfileSelect.replaceChildren(option('', '全部规则'));
    runSelect.replaceChildren();
    state.profileOptions.forEach((profile) => {
      const label = `${profile.name} · v${profile.version}`;
      profileSelect.append(option(profile.id, label)); filterProfileSelect.append(option(profile.id, label));
    });
    state.caseOptions.forEach((testCase) => runSelect.append(option(testCase.id, `${testCase.name} · ${testCase.triggerType}`)));
  }

  function option(value, label) {
    const item = document.createElement('option');
    item.value = value;
    item.textContent = label;
    return item;
  }

  const listDefinitions = {
    profiles: { path: '/api/capture-profiles/page', list: 'profile-list', pager: 'profile-pager', render: profileRow },
    cases: { path: '/api/test-cases/page', list: 'case-list', pager: 'case-pager', render: caseRow },
    runs: { path: '/api/runs/page', list: 'run-list', pager: 'run-pager', render: runRow }
  };

  async function loadList(kind) {
    const definition = listDefinitions[kind];
    const query = state.lists[kind];
    query.loading = true; renderList(byId(definition.list), [], definition.render, true); renderPager(kind);
    try {
      query.result = await api(`${definition.path}?${listQuery.toSearchParams(query)}`);
      state[kind] = query.result.content;
      renderList(byId(definition.list), query.result.content, definition.render, false);
    } catch (error) { setNotice(error.message, true); }
    finally { query.loading = false; renderPager(kind); }
  }

  function renderPager(kind) {
    const target = byId(listDefinitions[kind].pager); const query = state.lists[kind]; const result = query.result;
    target.replaceChildren();
    if (!result) return;
    const bar = element('div', 'pager');
    bar.append(element('span', '', `共 ${result.totalElements} 条 · 第 ${result.totalPages ? result.page + 1 : 0}/${result.totalPages} 页`));
    const controls = element('div', 'pager-controls');
    const previous = element('button', 'pager-icon pager-previous'); previous.type = 'button'; previous.title = '上一页'; previous.setAttribute('aria-label', '上一页'); previous.disabled = query.loading || result.page === 0;
    previous.addEventListener('click', () => { query.page -= 1; loadList(kind); });
    const size = document.createElement('select'); [10, 20, 50].forEach((value) => size.append(option(value, `${value} 条/页`))); size.value = String(query.size);
    size.disabled = query.loading; size.addEventListener('change', () => { query.page = 0; query.size = Number(size.value); loadList(kind); });
    const next = element('button', 'pager-icon pager-next'); next.type = 'button'; next.title = '下一页'; next.setAttribute('aria-label', '下一页'); next.disabled = query.loading || result.page + 1 >= result.totalPages;
    next.addEventListener('click', () => { query.page += 1; loadList(kind); }); controls.append(previous, size, next); bar.append(controls); target.append(bar);
  }

  async function loadOptions() {
    const options = await optionsLoader.loadOptions(
      () => api('/api/capture-profiles/options'),
      () => api('/api/test-cases/options'),
      () => new Promise((resolve) => setTimeout(resolve, 250))
    );
    state.profileOptions = options.profiles;
    state.caseOptions = options.cases;
    syncSelectors(); await loadCorrelationFields();
  }

  async function refresh() {
    try {
      await Promise.all([loadList('profiles'), loadList('cases'), loadList('runs')]);
      await loadOptions();
      setNotice('数据已刷新');
    } catch (error) { setNotice(error.message, true); }
  }

  async function showRun(id) {
    try {
      const [run, trace] = await Promise.all([api(`/api/runs/${id}`), api(`/api/runs/${id}/trace`)]);
      state.currentRun = run;
      state.currentTrace = trace;
      state.selectedSpanId = null;
      if (state.payloadRequest) state.payloadRequest.abort();
      state.payloadRequest = null; state.payloadCache.clear(); state.payloadViews.clear(); state.unavailablePayloads.clear();
      byId('result-summary').textContent = `${run.status} · ${run.failureReason || '等待证据或已成功'}`;
      renderCaseSnapshot(run);
      renderAssertions(run.assertionResults);
      renderTrace(trace.roots);
      document.querySelector('[data-view="result"]').click();
      if (state.pollTimer) clearTimeout(state.pollTimer);
      if (run.status === 'RUNNING') state.pollTimer = setTimeout(() => showRun(id), 2000);
    } catch (error) { setNotice(error.message, true); }
  }

  function renderCaseSnapshot(run) {
    const panel = byId('run-case-snapshot');
    panel.replaceChildren();
    if (!run.snapshotAvailable || !run.caseSnapshot) { panel.hidden = true; return; }
    panel.hidden = false;
    panel.append(element('h2', '', '本次运行用例快照'));
    const snapshot = run.caseSnapshot;
    appendDetail(panel, '用例', snapshot.name);
    appendDetail(panel, '触发', `${snapshot.triggerType || '-'} · ${snapshot.timeoutSeconds || '-'}s`);
    appendDetail(panel, '原文采集', snapshot.payloadCaptureEnabled ? '开启' : '关闭');
    if (snapshot.trigger?.method || snapshot.trigger?.url) appendDetail(panel, 'HTTP', [snapshot.trigger.method, snapshot.trigger.url].filter(Boolean).join(' '));
    if (snapshot.assertions?.length) appendDetail(panel, '断言', snapshot.assertions.map((item) => item.type).join('，'));
  }

  function renderAssertions(results) {
    renderList(byId('assertion-list'), results, (result) => {
      const row = element('div', `row ${result.status}`);
      row.append(element('strong', '', result.type), element('span', `status ${result.status}`, result.status));
      if (result.type === 'MYSQL_SCALAR') {
        const detail = element('div', 'meta', [result.expected, result.actual, result.failureReason]
          .filter((value) => value != null).map((value) => typeof value === 'string' ? value : JSON.stringify(value)).join(' · '));
        row.querySelector('strong').after(detail);
      }
      return row;
    });
  }

  function renderTrace(roots) {
    const graph = topology.buildSpanGraph(roots || []);
    state.traceLayout = topology.layoutSpanGraph(graph);
    renderTraceTree(roots || []);
    renderActiveTraceView();
  }

  function renderTraceTree(roots) {
    const tree = byId('trace-tree');
    tree.replaceChildren();
    if (!roots.length) tree.append(element('div', 'empty', '尚未接收到链路证据'));
    roots.forEach((node, index) => {
      const group = element('section', 'root-trace');
      const action = node.httpMethod ? `${node.httpMethod} ${node.target || ''}` : `${node.protocol || '-'} ${node.target || ''}`;
      group.append(element('h3', 'root-trace-title', `Trace ${index + 1} · ${action.trim()}`));
      group.append(traceNode(node, 0));
      tree.append(group);
    });
  }

  function traceNode(node, depth) {
    const item = element('details', 'trace-node');
    item.style.setProperty('--depth', depth);
    item.open = depth < 2;
    const summary = element('summary', 'trace-head');
    summary.dataset.spanId = node.spanId;
    summary.append(element('strong', 'protocol', `${node.protocol} ${node.direction}`));
    summary.append(element('span', '', node.target || ''));
    summary.append(element('span', 'meta', `${node.durationMillis || 0} ms`));
    item.append(summary);
    summary.addEventListener('click', () => selectTraceNodeById(node.spanId));
    summary.addEventListener('keydown', (event) => {
      if (event.key !== ' ') return;
      event.preventDefault();
      selectTraceNodeById(node.spanId);
    });
    const details = [node.httpMethod, node.statusCode, node.errorSummary, node.jdbcOperation, node.sqlTemplate]
      .filter((value) => value != null).join(' · ');
    if (details) item.append(element('div', 'trace-detail', details));
    (node.children || []).forEach((child) => item.append(traceNode(child, depth + 1)));
    return item;
  }

  function renderActiveTraceView() {
    if (!state.currentTrace) return;
    if (state.traceView === 'overview') return renderServiceOverview(state.currentTrace.roots || []);
    if (state.traceView === 'topology') return renderTopology(state.currentTrace.roots || []);
    if (state.traceView === 'tree') return renderTraceTree(state.currentTrace.roots || []);
    byId('topology-warning').hidden = true;
  }

  function setTraceView(view) {
    state.traceView = view;
    document.querySelectorAll('.trace-view').forEach((button) => {
      const active = button.dataset.traceView === view;
      button.classList.toggle('active', active);
      button.setAttribute('aria-selected', String(active));
    });
    byId('trace-tree').hidden = view !== 'tree';
    byId('trace-layout').hidden = view === 'tree';
    if (state.currentTrace) renderActiveTraceView();
  }

  function renderTopology(roots) {
    const graph = topology.limitRenderableGraph(topology.buildSpanGraph(roots), 500);
    const layout = topology.layoutSpanGraph(graph);
    const warning = byId('topology-warning');
    warning.hidden = !graph.truncated;
    warning.textContent = graph.truncated ? '调用拓扑仅展示前 500 个 Span；树形明细保留完整 Evidence。' : '';
    renderSvgGraph(layout);
    const selected = layout.nodes.find((item) => item.id === state.selectedSpanId) || layout.nodes[0] || null;
    selectTraceNode(selected, state.traceLayout || layout);
  }

  function svgElement(tag, className) {
    const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
    if (className) node.setAttribute('class', className);
    return node;
  }

  function svgText(parent, value, x, y, className) {
    const label = svgElement('text', className);
    label.setAttribute('x', x);
    label.setAttribute('y', y);
    label.textContent = text(value);
    parent.append(label);
    return label;
  }

  function truncate(value, length = 22) {
    const output = text(value);
    return output.length > length ? `${output.slice(0, length - 1)}...` : output;
  }

  function graphPoint(node) {
    return { x: node.laneIndex * 260 + 40, y: node.row * 108 + 54 };
  }

  function renderSvgGraph(layout) {
    const canvas = byId('topology-canvas');
    canvas.replaceChildren();
    if (!layout.nodes.length) return canvas.append(element('div', 'empty', '尚未接收到链路证据'));
    const svg = svgElement('svg');
    svg.setAttribute('width', Math.max(layout.width, 520));
    svg.setAttribute('height', Math.max(layout.height + 54, 180));
    svg.setAttribute('viewBox', `0 0 ${Math.max(layout.width, 520)} ${Math.max(layout.height + 54, 180)}`);
    svg.setAttribute('aria-label', 'Span 实例调用拓扑');
    appendArrowMarker(svg, 'topology-arrow');
    layout.lanes.forEach((lane, index) => appendLane(svg, lane, index, Math.max(layout.height + 54, 180)));
    const items = new Map(layout.nodes.map((item) => [item.id, item]));
    layout.edges.forEach((edge) => appendTopologyEdge(svg, items.get(edge.sourceId), items.get(edge.targetId)));
    layout.nodes.forEach((node) => appendTopologyNode(svg, node));
    canvas.append(svg);
  }

  function appendArrowMarker(svg, id) {
    const defs = svgElement('defs');
    const marker = svgElement('marker');
    marker.setAttribute('id', id); marker.setAttribute('markerWidth', '8'); marker.setAttribute('markerHeight', '8');
    marker.setAttribute('refX', '7'); marker.setAttribute('refY', '3'); marker.setAttribute('orient', 'auto');
    const arrow = svgElement('path'); arrow.setAttribute('d', 'M0,0 L0,6 L7,3 z'); arrow.setAttribute('fill', '#5d7467');
    marker.append(arrow); defs.append(marker); svg.append(defs);
  }

  function appendLane(svg, lane, index, height) {
    const box = svgElement('rect', 'topology-lane');
    box.setAttribute('x', index * 260 + 12); box.setAttribute('y', 12);
    box.setAttribute('width', 236); box.setAttribute('height', height - 24);
    svg.append(box); svgText(svg, truncate(lane, 26), index * 260 + 28, 38, 'topology-lane-title');
  }

  function appendTopologyEdge(svg, source, target) {
    if (!source || !target) return;
    const from = graphPoint(source); const to = graphPoint(target);
    const edge = svgElement('path', 'topology-edge');
    edge.dataset.sourceId = source.id; edge.dataset.targetId = target.id;
    edge.setAttribute('d', `M ${from.x + 180} ${from.y + 32} C ${from.x + 218} ${from.y + 32}, ${to.x - 38} ${to.y + 32}, ${to.x} ${to.y + 32}`);
    edge.setAttribute('marker-end', 'url(#topology-arrow)'); svg.append(edge);
  }

  function appendTopologyNode(svg, node) {
    const point = graphPoint(node); const group = svgElement('g', `topology-node${topology.isFailed(node) ? ' failed' : ''}`);
    group.dataset.spanId = node.id; group.setAttribute('tabindex', '0'); group.setAttribute('role', 'button');
    group.setAttribute('aria-label', `${node.instanceLabel}，${node.protocol || '未知协议'}`);
    group.setAttribute('transform', `translate(${point.x} ${point.y})`);
    const rect = svgElement('rect'); rect.setAttribute('width', '180'); rect.setAttribute('height', '64'); rect.setAttribute('rx', '3');
    group.append(rect); svgText(group, truncate(node.instanceLabel), 12, 24, 'topology-node-title');
    svgText(group, truncate(`${node.protocol || ''} ${node.direction || ''}`.trim(), 22), 12, 46, 'topology-node-meta');
    group.addEventListener('click', () => selectTraceNode(node, state.traceLayout));
    group.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); selectTraceNode(node, state.traceLayout); }
    });
    svg.append(group);
  }

  function selectTraceNodeById(id) {
    const layout = state.traceLayout;
    selectTraceNode(layout?.nodes.find((item) => item.id === id) || null, layout);
  }

  function relatedSpanIds(node, layout) {
    if (!node || !layout) return new Set();
    const byId = new Map(layout.nodes.map((item) => [item.id, item]));
    const children = new Map(layout.nodes.map((item) => [item.id, []]));
    layout.edges.forEach((edge) => children.get(edge.sourceId)?.push(edge.targetId));
    const related = new Set([node.id]);
    for (let parent = byId.get(node.id)?.parentId; parent; parent = byId.get(parent)?.parentId) related.add(parent);
    const visit = (id) => (children.get(id) || []).forEach((child) => { related.add(child); visit(child); });
    visit(node.id); return related;
  }

  function selectTraceNode(node, layout) {
    state.selectedSpanId = node ? node.id : null;
    renderNodeDetail(node, layout);
    const related = relatedSpanIds(node, layout);
    document.querySelectorAll('[data-span-id]').forEach((item) => {
      const selected = Boolean(node) && item.dataset.spanId === node.id;
      item.classList.toggle('selected', selected);
      item.classList.toggle('muted', Boolean(node) && !related.has(item.dataset.spanId));
    });
    document.querySelectorAll('.topology-edge').forEach((edge) => {
      const highlighted = related.has(edge.dataset.sourceId) && related.has(edge.dataset.targetId);
      edge.classList.toggle('highlighted', highlighted); edge.classList.toggle('muted', Boolean(node) && !highlighted);
    });
  }

  function renderNodeDetail(node, layout) {
    const panel = byId('trace-node-detail'); panel.replaceChildren();
    if (!node) return panel.append(element('div', 'empty', '选择一个节点查看脱敏 Evidence。'));
    panel.append(element('h2', '', node.instanceLabel));
    appendDetail(panel, '服务', node.serviceName);
    appendDetail(panel, '协议 / 方向', `${node.protocol || '-'} / ${node.direction || '-'}`);
    if (node.httpMethod || node.target) appendDetail(panel, 'HTTP', [node.httpMethod, node.target, node.statusCode].filter((value) => value != null).join(' '));
    appendDetail(panel, '耗时', `${node.durationMillis || 0} ms`);
    if (node.errorSummary) appendDetail(panel, '错误摘要', node.errorSummary);
    if (node.jdbcOperation) appendDetail(panel, 'JDBC 操作', node.jdbcOperation);
    if (node.sqlTemplate) appendDetail(panel, 'SQL 模板', node.sqlTemplate);
    const parent = layout?.nodes.find((item) => item.id === node.parentId);
    if (parent) appendDetail(panel, '父节点', parent.instanceLabel);
    appendDetail(panel, '子节点数', (layout?.edges.filter((edge) => edge.sourceId === node.id).length || 0));
    appendDetail(panel, '同服务实例数', layout?.nodes.filter((item) => item.serviceName === node.serviceName).length || 1);
    if (node.protocol === 'HTTP') renderPayloadDetail(panel, node, layout);
  }

  function renderPayloadDetail(panel, node, layout) {
    const section = element('section', 'payload-detail');
    section.append(element('h3', '', 'HTTP 原文'));
    const actions = element('div', 'payload-actions');
    const content = element('div', 'payload-content');
    const views = state.payloadViews.get(node.id) || { request: false, response: false };
    actions.append(payloadButton('request', '入参', node, layout, views, content),
            payloadButton('response', '出参', node, layout, views, content));
    section.append(actions);
    renderPayloadContent(content, node, views);
    section.append(content); panel.append(section);
  }

  function payloadButton(direction, label, node, layout, views, content) {
    const button = element('button', 'secondary payload-action', views[direction] ? `隐藏${label}` : `查看${label}`);
    button.type = 'button';
    button.addEventListener('click', async () => {
      if (!views[direction]) {
        const loaded = await loadPayloadOnDemand(node);
        if (!loaded) { renderNodeDetail(node, layout); return; }
      }
      views[direction] = !views[direction]; state.payloadViews.set(node.id, views);
      renderNodeDetail(node, layout); content.replaceChildren();
    });
    return button;
  }

  function renderPayloadContent(content, node, views) {
    if (state.unavailablePayloads.has(node.id)) {
      content.append(element('div', 'meta', '本次运行未采集 HTTP 原文，或原文已清理。'));
      return;
    }
    const payload = state.payloadCache.get(node.id);
    if (!payload) {
      content.append(element('div', 'meta', '选择入参或出参后按需加载，正文不会默认展示。'));
      return;
    }
    if (!views.request && !views.response) return;
    if (views.request) appendPayloadBlock(content, '入参', payload.requestContentType, payload.requestStatus,
            payload.requestTruncated, payload.requestBody);
    if (views.response) appendPayloadBlock(content, '出参', payload.responseContentType, payload.responseStatus,
            payload.responseTruncated, payload.responseBody);
  }

  function appendPayloadBlock(content, label, contentType, status, truncated, body) {
    const block = element('details', 'payload-block'); block.open = true;
    block.append(element('summary', '', label));
    block.append(element('div', 'meta', [contentType || '未知类型', status || '无状态', truncated ? '已截断' : ''].filter(Boolean).join(' · ')));
    if (body == null) block.append(element('div', 'meta', '没有可展示的正文。'));
    else { const pre = element('pre', 'payload-body'); pre.textContent = body; block.append(pre); }
    content.append(block);
  }

  async function loadPayloadOnDemand(node) {
    if (state.payloadCache.has(node.id)) return true;
    if (!state.currentRun || state.unavailablePayloads.has(node.id)) return false;
    if (state.payloadRequest) state.payloadRequest.abort();
    state.payloadRequest = new AbortController();
    try {
      const payload = await api(`/api/runs/${state.currentRun.id}/trace/http-payloads/${node.id}`, { signal: state.payloadRequest.signal });
      state.payloadCache.set(node.id, payload); return true;
    } catch (error) {
      if (error.name !== 'AbortError') state.unavailablePayloads.add(node.id);
      return false;
    } finally { state.payloadRequest = null; }
  }

  function appendDetail(panel, label, value) {
    const row = element('div', 'detail-field');
    row.append(element('span', 'detail-label', label), element('div', 'detail-value', value)); panel.append(row);
  }

  function renderServiceOverview(roots) {
    const graph = topology.buildSpanGraph(roots);
    const overview = topology.buildServiceOverview(graph);
    const canvas = byId('topology-canvas'); const warning = byId('topology-warning');
    canvas.replaceChildren(); warning.hidden = true;
    if (!overview.nodes.length) return canvas.append(element('div', 'empty', '尚未接收到链路证据'));
    const width = Math.max(520, overview.nodes.length * 230 + 40);
    const svg = svgElement('svg'); svg.setAttribute('width', width); svg.setAttribute('height', '290');
    svg.setAttribute('viewBox', `0 0 ${width} 290`); svg.setAttribute('aria-label', '服务依赖概览');
    appendArrowMarker(svg, 'overview-arrow');
    const positions = new Map(overview.nodes.map((name, index) => [name, { x: index * 230 + 40, y: 124 }]));
    overview.edges.forEach((edge) => appendOverviewEdge(svg, edge, positions));
    overview.nodes.forEach((name) => appendOverviewNode(svg, name, positions.get(name), graph));
    canvas.append(svg);
  }

  function appendOverviewEdge(svg, edge, positions) {
    const from = positions.get(edge.source); const to = positions.get(edge.target);
    if (!from || !to) return;
    const path = svgElement('path', 'topology-edge overview-edge');
    const label = `${edge.count} 次${edge.failedCount ? `，失败 ${edge.failedCount}` : ''}`;
    if (edge.source === edge.target) {
      path.setAttribute('d', `M ${from.x + 140} ${from.y + 24} C ${from.x + 195} ${from.y + 24}, ${from.x + 195} ${from.y - 42}, ${from.x + 90} ${from.y - 42} C ${from.x + 28} ${from.y - 42}, ${from.x + 28} ${from.y - 8}, ${from.x + 40} ${from.y}`);
      svgText(svg, label, from.x + 58, from.y - 52, 'overview-edge-label');
    } else {
      const direction = from.x < to.x ? 1 : -1;
      const offset = direction * 36;
      path.setAttribute('d', `M ${from.x + (direction > 0 ? 180 : 0)} ${from.y + 32} Q ${(from.x + to.x + 180) / 2} ${from.y + offset}, ${to.x + (direction > 0 ? 0 : 180)} ${to.y + 32}`);
      svgText(svg, label, (from.x + to.x + 180) / 2 - 18, from.y + offset - 6, 'overview-edge-label');
    }
    path.setAttribute('marker-end', 'url(#overview-arrow)'); path.setAttribute('tabindex', '0'); path.setAttribute('role', 'button');
    path.setAttribute('aria-label', `${edge.source} 到 ${edge.target}，${label}`);
    path.addEventListener('click', () => renderOverviewEdgeDetail(edge));
    path.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); renderOverviewEdgeDetail(edge); }
    });
    svg.append(path);
  }

  function appendOverviewNode(svg, name, point, graph) {
    const group = svgElement('g', 'topology-node overview-node');
    group.setAttribute('transform', `translate(${point.x} ${point.y})`); group.setAttribute('tabindex', '0');
    group.setAttribute('role', 'button'); group.setAttribute('aria-label', name);
    const rect = svgElement('rect'); rect.setAttribute('width', '180'); rect.setAttribute('height', '64'); rect.setAttribute('rx', '3');
    group.append(rect); svgText(group, truncate(name), 12, 29, 'topology-node-title');
    svgText(group, `${graph.nodes.filter((item) => topology.laneName(item) === name).length} 个 Span 实例`, 12, 49, 'topology-node-meta');
    group.addEventListener('click', () => renderOverviewServiceDetail(name, graph));
    group.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); renderOverviewServiceDetail(name, graph); }
    });
    svg.append(group);
  }

  function renderOverviewEdgeDetail(edge) {
    const panel = byId('trace-node-detail'); panel.replaceChildren();
    panel.append(element('h2', '', `${edge.source} -> ${edge.target}`));
    appendDetail(panel, '协议 / 方向', `${edge.protocol || '-'} / ${edge.direction || '-'}`);
    appendDetail(panel, '调用数', edge.count); appendDetail(panel, '失败数', edge.failedCount);
    appendDetail(panel, '对应 Span 实例数', edge.spanIds.length);
    panel.append(element('div', 'meta', '切换到调用拓扑可继续定位具体 Span 实例。'));
  }

  function renderOverviewServiceDetail(name, graph) {
    const nodes = graph.nodes.filter((item) => topology.laneName(item) === name);
    const protocolCounts = nodes.reduce((counts, item) => {
      counts.set(item.protocol || '未知协议', (counts.get(item.protocol || '未知协议') || 0) + 1); return counts;
    }, new Map());
    const panel = byId('trace-node-detail'); panel.replaceChildren(); panel.append(element('h2', '', name));
    appendDetail(panel, 'Span 实例总数', nodes.length);
    appendDetail(panel, '协议分布', [...protocolCounts].map(([protocol, count]) => `${protocol}: ${count}`).join('，'));
  }

  function matcherRow() {
    const row = element('div', 'matcher-row');
    const field = document.createElement('input');
    field.name = 'field'; field.required = true; field.maxLength = 120;
    const location = document.createElement('select');
    location.name = 'location';
    ['JSON_BODY', 'HEADER'].forEach((value) => location.append(option(value, value === 'JSON_BODY' ? 'JSON Body' : 'HTTP Header')));
    const fieldLabel = element('label', '', '关联字段'); fieldLabel.append(field);
    const locationLabel = element('label', '', '字段位置'); locationLabel.append(location);
    row.append(fieldLabel, locationLabel);
    return row;
  }

  function headerRow() {
    const row = element('div', 'header-row');
    const name = document.createElement('input'); name.className = 'http-header-name'; name.maxLength = 160;
    const value = document.createElement('input'); value.className = 'http-header-value'; value.maxLength = 1000;
    const nameLabel = element('label', '', '名称'); nameLabel.append(name);
    const valueLabel = element('label', '', '值'); valueLabel.append(value);
    const remove = element('button', 'header-remove', '×');
    remove.type = 'button'; remove.title = '删除 Header'; remove.setAttribute('aria-label', '删除 Header');
    remove.addEventListener('click', () => row.remove());
    row.append(nameLabel, valueLabel, remove);
    return row;
  }

  function mysqlParameterRow() {
    const row = element('div', 'mysql-parameter-row');
    const source = document.createElement('select'); source.className = 'mysql-parameter-source';
    source.append(option('REQUEST_JSON_PATH', '请求 JSONPath'), option('REQUEST_HEADER', '请求 Header'));
    const value = document.createElement('input'); value.className = 'mysql-parameter-value'; value.maxLength = 500;
    const sourceLabel = element('label', '', '来源'); sourceLabel.append(source);
    const valueLabel = element('label', '', '路径或 Header'); valueLabel.append(value);
    const remove = element('button', 'mysql-parameter-remove', '×');
    remove.type = 'button'; remove.title = '删除参数'; remove.setAttribute('aria-label', '删除参数');
    remove.addEventListener('click', () => row.remove());
    source.addEventListener('change', () => { value.placeholder = source.value === 'REQUEST_JSON_PATH' ? '$.orderNo' : 'X-Tenant'; });
    source.dispatchEvent(new Event('change'));
    row.append(sourceLabel, valueLabel, remove);
    return row;
  }

  function toggleCaseFields() {
    const isHttp = byId('trigger-type').value === 'HTTP';
    const timeout = byId('case-timeout-seconds');
    timeout.min = isHttp ? '1' : '60';
    timeout.max = isHttp ? '300' : '3600';
    if (!timeout.value || Number(timeout.value) < Number(timeout.min) || Number(timeout.value) > Number(timeout.max)) {
      timeout.value = isHttp ? '60' : '1800';
    }
    const box = byId('http-config');
    box.hidden = !isHttp;
    box.querySelectorAll('input,select,textarea,button').forEach((item) => { item.disabled = !isHttp; });
    toggleAssertions();
  }

  function toggleAssertions() {
    const isHttp = !byId('http-config').hidden;
    const status = byId('http-status-expected');
    status.disabled = !isHttp || !byId('http-status-enabled').checked;
    status.required = !status.disabled;
    const jsonEnabled = isHttp && byId('http-json-path-enabled').checked;
    ['http-json-path', 'http-json-expected'].forEach((id) => {
      byId(id).disabled = !jsonEnabled;
      byId(id).required = jsonEnabled;
    });
    const mysqlEnabled = isHttp && byId('mysql-scalar-enabled').checked;
    ['mysql-scalar-sql', 'mysql-scalar-expected', 'mysql-scalar-wait-seconds', 'add-mysql-parameter'].forEach((id) => {
      byId(id).disabled = !mysqlEnabled;
    });
    byId('mysql-scalar-parameters').querySelectorAll('input,select,button').forEach((item) => { item.disabled = !mysqlEnabled; });
  }

  function headersFromForm() {
    const headers = {};
    for (const row of document.querySelectorAll('#http-headers .header-row')) {
      const name = row.querySelector('.http-header-name').value.trim();
      const value = row.querySelector('.http-header-value').value;
      if (!name) throw new Error('Header 名称不能为空');
      if (Object.keys(headers).some((key) => key.toLowerCase() === name.toLowerCase())) throw new Error('Header 名称不能重复');
      headers[name] = value;
    }
    return headers;
  }

  function httpConfig() {
    const url = byId('http-url').value.trim();
    let parsed;
    try { parsed = new URL(url); } catch (_) { throw new Error('URL 必须使用 HTTP 或 HTTPS'); }
    if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('URL 必须使用 HTTP 或 HTTPS');
    const body = byId('http-body').value.trim();
    if (body) { try { JSON.parse(body); } catch (_) { throw new Error('JSON 请求体格式无效'); } }
    const headers = headersFromForm();
    if (body && !Object.keys(headers).some((key) => key.toLowerCase() === 'content-type')) headers['Content-Type'] = 'application/json';
    return { method: byId('http-method').value, url, headers, body };
  }

  function httpAssertions() {
    const assertions = [];
    if (byId('http-status-enabled').checked) {
      const expected = Number(byId('http-status-expected').value);
      if (!Number.isInteger(expected) || expected < 100 || expected > 599) throw new Error('HTTP 状态码必须在 100 到 599 之间');
      assertions.push({ sequenceNo: 1, type: 'HTTP_STATUS', definition: { expected } });
    }
    if (byId('http-json-path-enabled').checked) {
      const jsonPath = byId('http-json-path').value.trim();
      if (!jsonPath) throw new Error('JSONPath 不能为空');
      let expected;
      try { expected = JSON.parse(byId('http-json-expected').value); } catch (_) { throw new Error('JSONPath 预期值必须是合法 JSON'); }
      assertions.push({ sequenceNo: assertions.length + 1, type: 'HTTP_JSON_PATH', definition: { jsonPath, expected } });
    }
    if (byId('mysql-scalar-enabled').checked) {
      const sql = byId('mysql-scalar-sql').value.trim();
      if (!/^SELECT\s/i.test(sql) || /;|--|\/\*|\*\//.test(sql)) throw new Error('MySQL SQL 必须是一条无注释的 SELECT');
      const parameters = [...byId('mysql-scalar-parameters').querySelectorAll('.mysql-parameter-row')].map((row) => {
        const source = row.querySelector('.mysql-parameter-source').value;
        const value = row.querySelector('.mysql-parameter-value').value.trim();
        if (!value) throw new Error('MySQL 参数路径或 Header 名不能为空');
        return source === 'REQUEST_JSON_PATH' ? { source, jsonPath: value } : { source, headerName: value };
      });
      let expected;
      try { expected = JSON.parse(byId('mysql-scalar-expected').value); } catch (_) { throw new Error('MySQL 预期值必须是合法 JSON'); }
      if (Array.isArray(expected) || (expected !== null && typeof expected === 'object')) throw new Error('MySQL 预期值必须是 JSON 标量');
      const waitSeconds = Number(byId('mysql-scalar-wait-seconds').value);
      if (!Number.isInteger(waitSeconds) || waitSeconds < 0 || waitSeconds > 10) throw new Error('MySQL 等待秒数必须在 0 到 10 之间');
      assertions.push({ sequenceNo: assertions.length + 1, type: 'MYSQL_SCALAR', definition: { sql, parameters, expected, waitSeconds } });
    }
    return assertions;
  }

  async function loadCorrelationFields() {
    const box = byId('correlation-fields');
    box.replaceChildren();
    const selected = state.caseOptions.find((item) => item.id === byId('run-case-select').value);
    if (!selected || selected.triggerType !== 'BROWSER') return;
    const testCase = await api(`/api/test-cases/${selected.id}`);
    const profile = await api(`/api/capture-profiles/${testCase.profileId}`);
    profile.definition.blackboxCorrelation.matchGroups.flatMap((group) => group.matchers).forEach((matcher) => {
      const label = element('label', '', matcher.field);
      const input = document.createElement('input'); input.name = matcher.field; input.required = true; input.maxLength = 256;
      label.append(input); box.append(label);
    });
  }

  byId('add-matcher').addEventListener('click', () => byId('matcher-fields').append(matcherRow()));
  byId('add-http-header').addEventListener('click', () => byId('http-headers').append(headerRow()));
  byId('trigger-type').addEventListener('change', toggleCaseFields);
  byId('http-status-enabled').addEventListener('change', toggleAssertions);
  byId('http-json-path-enabled').addEventListener('change', toggleAssertions);
  byId('mysql-scalar-enabled').addEventListener('change', toggleAssertions);
  byId('add-mysql-parameter').addEventListener('click', () => {
    byId('mysql-scalar-parameters').append(mysqlParameterRow());
    toggleAssertions();
  });
  byId('run-case-select').addEventListener('change', () => loadCorrelationFields().catch((error) => setNotice(error.message, true)));
  ['profiles', 'cases', 'runs'].forEach((kind) => {
    const form = byId(`${kind === 'profiles' ? 'profile' : kind === 'cases' ? 'case' : 'run'}-filter`);
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      state.lists[kind] = listQuery.applyFilters(state.lists[kind], Object.fromEntries(new FormData(form).entries()));
      loadList(kind);
    });
  });

  byId('profile-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const rows = [...document.querySelectorAll('.matcher-row')];
    const matchers = rows.map((row) => ({ field: row.querySelector('[name=field]').value.trim(), locations: [row.querySelector('[name=location]').value], match: 'EXACT' }));
    try {
      const targetServices = String(form.get('service') || '').split(',').map((value) => value.trim()).filter(Boolean);
      await api('/api/capture-profiles', { method: 'POST', body: JSON.stringify({ name: form.get('name'), version: 1, definition: { blackboxCorrelation: { defaultTtlSeconds: 1800, retentionSeconds: 300, targetServices, matchGroups: [{ name: 'browser', matchers }] } } }) });
      formElement.reset(); await loadList('profiles'); await loadOptions();
    } catch (error) { setNotice(error.message, true); }
  });

  byId('case-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const triggerType = form.get('triggerType');
    try {
      const request = { name: form.get('name'), profileId: form.get('profileId'), triggerType,
        triggerConfig: triggerType === 'HTTP' ? httpConfig() : {}, timeoutSeconds: Number(form.get('timeoutSeconds')),
        httpPayloadCaptureEnabled: byId('http-payload-capture-enabled').checked,
        assertions: triggerType === 'HTTP' ? httpAssertions() : [] };
      await api('/api/test-cases', { method: 'POST', body: JSON.stringify(request) });
      formElement.reset(); byId('mysql-scalar-parameters').replaceChildren(); toggleCaseFields(); await loadList('cases'); await loadOptions();
    } catch (error) { setNotice(error.message, true); }
  });

  byId('run-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const testCase = state.caseOptions.find((item) => item.id === form.get('testCaseId'));
    if (!testCase) return setNotice('请选择测试用例', true);
    try {
      const path = testCase.triggerType === 'HTTP' ? `/api/test-cases/${testCase.id}/runs` : `/api/test-cases/${testCase.id}/blackbox-runs`;
      const body = testCase.triggerType === 'HTTP' ? {} : { correlationData: Object.fromEntries([...form.entries()].filter(([key]) => key !== 'testCaseId')) };
      const run = await api(path, { method: 'POST', body: JSON.stringify(body) });
      state.lists.runs = listQuery.createListQuery(); await loadList('runs');
      if (testCase.triggerType === 'BROWSER') {
        renderBlackboxCapture(run);
        startBlackboxWarmup();
        await monitorBlackboxRun(run.id);
        return;
      }
      await showRun(run.id);
    } catch (error) { setNotice(error.message, true); }
  });

  document.querySelectorAll('.nav-item').forEach((button) => button.addEventListener('click', () => {
    document.querySelectorAll('.nav-item,.view').forEach((item) => item.classList.remove('active'));
    button.classList.add('active'); byId(button.dataset.view).classList.add('active');
  }));
  byId('refresh-config').addEventListener('click', () => Promise.all([loadList('profiles'), loadList('cases'), loadOptions()]));
  byId('refresh-runs').addEventListener('click', () => loadList('runs'));
  byId('blackbox-stop').addEventListener('click', async () => {
    const runId = state.activeBlackboxRunId;
    if (!runId) return;
    try {
      const run = await api(`/api/runs/${runId}/complete`, { method: 'POST' });
      renderBlackboxCapture(run);
      await monitorBlackboxRun(run.id);
    } catch (error) { setNotice(error.message, true); }
  });
  byId('refresh-result').addEventListener('click', () => state.currentRun && showRun(state.currentRun.id));
  document.querySelectorAll('.trace-view').forEach((button) => {
    button.addEventListener('click', () => setTraceView(button.dataset.traceView));
  });
  setTraceView('topology');
  toggleCaseFields();
  refresh();
})();
