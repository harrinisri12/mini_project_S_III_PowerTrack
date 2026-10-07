/**
 * POWERTRACK · ELECTRICITY GRID COMMAND CENTER
 * Operational Front-End Engine (Vanilla ES6)
 * Tamil Nadu Regional Grid Simulation (Erode, Perundurai, Chennimalai, Tiruppur, Avinashi)
 * Direct Connection to Java HttpServer on port 8080
 */

/* ============================================================
 * 1. API CLIENT DEFINITION
 * ============================================================ */

const API = {
  baseUrl: "/api",

  async get(path) {
    const response = await fetch(`${this.baseUrl}${path}`, {
      headers: { "Accept": "application/json" }
    });

    if (!response.ok) {
      throw new Error(`GET ${path} failed with HTTP status ${response.status}`);
    }

    return response.json();
  },

  async post(path, body = {}) {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept": "application/json"
      },
      body: JSON.stringify(body)
    });

    const data = await response.json();

    if (!response.ok) {
      throw new Error(data.message || `POST ${path} failed with HTTP status ${response.status}`);
    }

    return data;
  }
};

/* ============================================================
 * 2. APPLICATION STATE
 * ============================================================ */

const state = {
  network: [],
  consumers: [],
  technicians: [],
  outages: [],
  history: [],
  stats: {
    totalConsumers: 0,
    powerAvailable: 0,
    powerOutage: 0,
    activeOutages: 0,
    restoredOutages: 0,
    activeNodes: 0,
    availableTechnicians: 0
  },
  events: [],
  selectedNodeId: null,
  consumerAreaFilter: "all",
  consumerSearchTerm: "",
  globalSearchQuery: "",
  activeTab: "view-grid",
  isConnected: true
};

/* ============================================================
 * 3. DOM HELPERS
 * ============================================================ */

const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];

/* ============================================================
 * 4. INITIALIZATION & LIFECYCLE
 * ============================================================ */

document.addEventListener("DOMContentLoaded", async () => {
  bindNavigation();
  bindActions();
  bindModals();
  bindSearchAndFilter();
  bindTopologyInteractions();
  startTerminalClock();

  await initializeApplication();

  // Auto-sync telemetry every 25 seconds
  setInterval(async () => {
    try {
      await loadAllData();
      renderAll();
    } catch {
      // Handled in loadAllData
    }
  }, 25000);
});

async function initializeApplication() {
  try {
    await loadAllData();
    populateFaultDropdown();
    buildInitialEventStream();
    renderAll();
    showToast("Command Center synchronized with Tamil Nadu Grid backend.", "success");
  } catch (error) {
    console.error("PowerTrack initialization error:", error);
    showToast("Unable to connect to Java backend on http://localhost:8080.", "error");
  }
}

/* ============================================================
 * 5. DATA INGESTION FROM JAVA BACKEND
 * ============================================================ */

async function loadAllData() {
  try {
    const results = await Promise.all([
      API.get("/network"),
      API.get("/consumers"),
      API.get("/technicians"),
      API.get("/outages/active"),
      API.get("/outages/history"),
      API.get("/stats")
    ]);

    state.network = Array.isArray(results[0]) ? results[0] : [];
    state.consumers = Array.isArray(results[1]) ? results[1] : [];
    state.technicians = Array.isArray(results[2]) ? results[2] : [];
    state.outages = Array.isArray(results[3]) ? results[3] : [];

    // Filter history for restored outages
    state.history = Array.isArray(results[4])
      ? results[4].filter(o => o.status === "RESTORED").reverse()
      : [];

    state.stats = results[5] || {};

    // Sort active outages by priority
    state.outages.sort(comparePriority);

    state.isConnected = true;
  } catch (error) {
    state.isConnected = false;
    throw error;
  }
}

/* ============================================================
 * 6. NAVIGATION & DOCK ROUTING
 * ============================================================ */

function bindNavigation() {
  $$(".dock-btn[data-tab]").forEach(button => {
    button.addEventListener("click", () => {
      switchViewTab(button.dataset.tab);
    });
  });
}

function switchViewTab(tabId) {
  state.activeTab = tabId;

  $$(".view-section").forEach(sec => sec.classList.remove("active"));
  $$(".dock-btn[data-tab]").forEach(btn => btn.classList.remove("active"));

  const targetView = $(`#${tabId}`);
  const targetDockBtn = $(`.dock-btn[data-tab="${tabId}"]`);

  if (targetView) targetView.classList.add("active");
  if (targetDockBtn) targetDockBtn.classList.add("active");

  window.scrollTo({ top: 0, behavior: "smooth" });
}

/* ============================================================
 * 7. ACTION HANDLERS & SIMULATION
 * ============================================================ */

function bindActions() {
  const btnOpenSim = $("#btnOpenFaultSim");
  const btnSimOutagePage = $("#btnSimulateOutagePage");

  [btnOpenSim, btnSimOutagePage].forEach(btn => {
    if (btn) btn.addEventListener("click", () => openFaultModal());
  });

  const btnExecDispatch = $("#btnExecDispatch");
  const btnProcessQueuePage = $("#btnProcessQueuePage");

  [btnExecDispatch, btnProcessQueuePage].forEach(btn => {
    if (btn) btn.addEventListener("click", processNextOutage);
  });

  const btnRefresh = $("#btnRefreshGrid");
  if (btnRefresh) {
    btnRefresh.addEventListener("click", async () => {
      await loadAllData();
      renderAll();
      showToast("Grid telemetry synchronized.", "success");
    });
  }

  const btnSyncHist = $("#btnSyncHistory");
  if (btnSyncHist) {
    btnSyncHist.addEventListener("click", async () => {
      await loadAllData();
      renderAll();
      showToast("Incident audit log updated.", "success");
    });
  }

  const btnDrawerFault = $("#btnDrawerTriggerFault");
  if (btnDrawerFault) {
    btnDrawerFault.addEventListener("click", () => {
      if (state.selectedNodeId) {
        openFaultModal(state.selectedNodeId);
      }
    });
  }
}

/* ============================================================
 * 8. FAULT SIMULATION (POST /api/outages/simulate)
 * ============================================================ */

async function triggerFaultSimulation(event) {
  event.preventDefault();

  const componentSelect = $("#modalSelectComponent");
  const component = componentSelect ? componentSelect.value : null;

  if (!component) {
    showToast("Please select a target network component.", "error");
    return;
  }

  const submitBtn = $("#btnSubmitFault");

  try {
    setButtonLoading(submitBtn, true, "Triggering...");

    const result = await API.post("/outages/simulate", {
      component: component
    });

    if (!result.success) {
      throw new Error(result.message || "Simulation failed.");
    }

    closeFaultModal();
    await loadAllData();
    renderAll();

    const outage = result.outage;
    const areasStr = Array.isArray(outage.areas) ? outage.areas.join(", ") : "Downstream Feeds";

    addEvent(
      `⚡ Fault Detected · ${outage.component}`,
      `Outage OUT-${outage.outageId} · Areas: ${areasStr} (${outage.consumers} consumers isolated)`,
      "fault"
    );

    // Open telemetry drawer on the faulted node
    openNodeTelemetryDrawer(component);

    showToast(`Circuit isolated for ${outage.component}. Outage OUT-${outage.outageId} queued.`, "error");
  } catch (error) {
    console.error("Fault simulation failed:", error);
    showToast(error.message || "Fault simulation failed.", "error");
  } finally {
    setButtonLoading(submitBtn, false, "Trigger Fault Detection");
  }
}

/* ============================================================
 * 9. PROCESS NEXT OUTAGE (POST /api/outages/process)
 * ============================================================ */

async function processNextOutage() {
  if (!state.outages.length) {
    showToast("There are no pending outages in the restoration queue.", "warning");
    return;
  }

  try {
    const result = await API.post("/outages/process", {});

    if (!result.success) {
      throw new Error(result.message || "Unable to process outage.");
    }

    await loadAllData();
    renderAll();

    const outage = result.outage;
    addEvent(
      `● Crew Dispatched · OUT-${outage.outageId}`,
      `${outage.technician} assigned to restore ${outage.component}`,
      "assign"
    );

    showToast(`OUT-${outage.outageId} is now IN PROGRESS. ${outage.technician} dispatched.`, "success");
  } catch (error) {
    console.error("Process next outage failed:", error);
    showToast(error.message || "Unable to dispatch technician.", "error");
  }
}

/* ============================================================
 * 10. RESTORE OUTAGE (POST /api/outages/restore)
 * ============================================================ */

window.restoreOutage = async function(outageId) {
  try {
    const result = await API.post("/outages/restore", {
      outageId: String(outageId)
    });

    if (!result.success) {
      throw new Error(result.message || "Restoration failed.");
    }

    await loadAllData();
    renderAll();

    const outage = result.outage;
    addEvent(
      `● Power Restored · OUT-${outage.outageId}`,
      `${outage.component} back online · Circuit normalized by ${outage.technician || 'Crew'}`,
      "restore"
    );

    showToast(`OUT-${outage.outageId} restored. Power delivery normalized.`, "success");
  } catch (error) {
    console.error("Restoration failed:", error);
    showToast(error.message || "Unable to restore outage.", "error");
  }
};

/* ============================================================
 * 11. TOPOLOGY SVG INTERACTION & TELEMETRY DRAWER
 * ============================================================ */

function bindTopologyInteractions() {
  $$(".grid-node-element").forEach(nodeEl => {
    nodeEl.addEventListener("click", () => {
      const nodeId = nodeEl.dataset.node;
      if (nodeId) {
        openNodeTelemetryDrawer(nodeId);
      }
    });
  });

  const btnCloseDrawer = $("#btnCloseDrawer");
  if (btnCloseDrawer) {
    btnCloseDrawer.addEventListener("click", closeNodeTelemetryDrawer);
  }
}

function openNodeTelemetryDrawer(nodeId) {
  const node = state.network.find(n => n.id === nodeId);
  if (!node) return;

  state.selectedNodeId = nodeId;

  const drawer = $("#telemetryDrawer");
  const title = $("#drawerNodeTitle");
  const nameEl = $("#drawerNodeName");
  const idEl = $("#drawerNodeId");
  const statusBadge = $("#drawerNodeStatusBadge");
  const typeEl = $("#drawerNodeType");
  const activeStateEl = $("#drawerNodeActiveState");
  const consumersEl = $("#drawerNodeConsumers");
  const tierEl = $("#drawerNodeTier");
  const linksEl = $("#drawerDownstreamLinks");

  if (title) title.textContent = `${node.type} TELEMETRY`;
  if (nameEl) nameEl.textContent = node.name;
  if (idEl) idEl.textContent = `ID: ${node.id}`;

  if (statusBadge) {
    statusBadge.className = node.active ? "badge-sig healthy" : "badge-sig active";
    statusBadge.textContent = node.active ? "OPERATIONAL" : "CIRCUIT ISOLATED / FAULT";
  }

  if (typeEl) typeEl.textContent = node.type;
  if (activeStateEl) activeStateEl.textContent = node.active ? "ONLINE (100%)" : "OFFLINE (0%)";

  // Calculate connected consumers
  const connectedAreas = getDownstreamAreas(node.id);
  const affectedConsumers = state.consumers.filter(c => connectedAreas.includes(c.area));
  if (consumersEl) consumersEl.textContent = `${affectedConsumers.length} Accounts`;

  const tierMap = {
    "SUBSTATION": "Tier 1 Transmission (230kV/110kV)",
    "FEEDER": "Tier 2 Distribution Feeder (11kV/22kV)",
    "TRANSFORMER": "Tier 3 Step-Down (11kV / 415V)",
    "AREA": "Tier 4 End-Point Delivery Locality"
  };
  if (tierEl) tierEl.textContent = tierMap[node.type] || "Regional Distribution";

  if (linksEl) {
    linksEl.innerHTML = connectedAreas.length > 0
      ? connectedAreas.map(a => `<div style="padding: 2px 0;">&bull; Delivery Zone: <strong style="color: var(--accent-amber);">${escapeHtml(a)}</strong></div>`).join("")
      : "Terminal Distribution Point";
  }

  if (drawer) drawer.classList.add("open");
}

function closeNodeTelemetryDrawer() {
  const drawer = $("#telemetryDrawer");
  if (drawer) drawer.classList.remove("open");
}

function getDownstreamAreas(nodeId) {
  if (nodeId === "SS-ERD-01" || nodeId === "FD-ERD-01" || nodeId === "DT-ERD-101") return ["AREA-ERD-PR", "AREA-ERD-SN"];
  if (nodeId === "SS-PER-02" || nodeId === "FD-PER-02" || nodeId === "DT-PER-102") return ["AREA-PER-SIP"];
  if (nodeId === "FD-CHN-03" || nodeId === "DT-CHN-103") return ["AREA-CHN-TWN"];
  if (nodeId === "SS-TPR-03" || nodeId === "FD-AVI-04" || nodeId === "DT-AVI-104") return ["AREA-AVI-BYP"];
  if (nodeId === "FD-TPR-05" || nodeId === "DT-TPR-105") return ["AREA-TPR-RAY"];
  if (nodeId.startsWith("AREA-")) return [nodeId];
  return [];
}

/* ============================================================
 * 12. SEARCH & FILTERING (Global Command Search)
 * ============================================================ */

function bindSearchAndFilter() {
  const globalInput = $("#globalCommandSearch");
  const resultsOverlay = $("#searchResultsDropdown");

  if (globalInput && resultsOverlay) {
    globalInput.addEventListener("input", (e) => {
      const term = e.target.value.trim().toLowerCase();
      if (!term) {
        resultsOverlay.classList.remove("open");
        resultsOverlay.innerHTML = "";
        return;
      }

      const results = [];

      // Match Nodes
      state.network.forEach(n => {
        if (n.id.toLowerCase().includes(term) || n.name.toLowerCase().includes(term)) {
          results.push({ type: "NODE", title: `${n.id} · ${n.name}`, sub: n.type, id: n.id });
        }
      });

      // Match Consumers
      state.consumers.forEach(c => {
        if (String(c.id).includes(term) || c.name.toLowerCase().includes(term) || c.area.toLowerCase().includes(term)) {
          results.push({ type: "CONSUMER", title: `PT-CUS-${c.id} · ${c.name}`, sub: c.area, tab: "view-consumers" });
        }
      });

      // Match Technicians
      state.technicians.forEach(t => {
        if (t.name.toLowerCase().includes(term) || t.specialization.toLowerCase().includes(term)) {
          results.push({ type: "CREW", title: `TECH-${t.id} · ${t.name}`, sub: t.specialization, tab: "view-technicians" });
        }
      });

      // Match Outages
      state.outages.forEach(o => {
        if (String(o.outageId).includes(term) || o.component.toLowerCase().includes(term)) {
          results.push({ type: "OUTAGE", title: `OUT-${o.outageId} · ${o.component}`, sub: o.status, tab: "view-outages" });
        }
      });

      if (results.length > 0) {
        resultsOverlay.innerHTML = results.slice(0, 8).map(res => `
          <div class="search-result-item" data-type="${escapeHtml(res.type)}" data-id="${escapeHtml(res.id || '')}" data-tab="${escapeHtml(res.tab || '')}">
            <div>
              <strong style="color: var(--text-pure);">${escapeHtml(res.title)}</strong>
              <div style="font-size: 10px; color: var(--text-muted);">${escapeHtml(res.sub)}</div>
            </div>
            <span class="badge-sig neutral">${escapeHtml(res.type)}</span>
          </div>
        `).join("");

        $$(".search-result-item").forEach(item => {
          item.addEventListener("click", () => {
            const itemType = item.dataset.type;
            const itemId = item.dataset.id;
            const itemTab = item.dataset.tab;

            resultsOverlay.classList.remove("open");
            globalInput.value = "";

            if (itemType === "NODE" && itemId) {
              switchViewTab("view-grid");
              openNodeTelemetryDrawer(itemId);
            } else if (itemTab) {
              switchViewTab(itemTab);
            }
          });
        });

        resultsOverlay.classList.add("open");
      } else {
        resultsOverlay.innerHTML = `<div style="padding: 12px; font-size: 11px; color: var(--text-muted); font-family: var(--font-mono);">No matching telemetry found for "${escapeHtml(term)}"</div>`;
        resultsOverlay.classList.add("open");
      }
    });

    // Close on outside click
    document.addEventListener("click", (e) => {
      if (!globalInput.contains(e.target) && !resultsOverlay.contains(e.target)) {
        resultsOverlay.classList.remove("open");
      }
    });

    // Slash shortcut to focus
    document.addEventListener("keydown", (e) => {
      if (e.key === "/" && document.activeElement !== globalInput) {
        e.preventDefault();
        globalInput.focus();
      }
    });
  }

  // Consumer Filter Input & Area Tabs
  const consumerInput = $("#consumerFilterInput");
  if (consumerInput) {
    consumerInput.addEventListener("input", (e) => {
      state.consumerSearchTerm = e.target.value;
      renderConsumers();
    });
  }

  $$("#consumerAreaFilterButtons .btn-canvas-tool").forEach(btn => {
    btn.addEventListener("click", () => {
      $$("#consumerAreaFilterButtons .btn-canvas-tool").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      state.consumerAreaFilter = btn.dataset.filter || "all";
      renderConsumers();
    });
  });
}

/* ============================================================
 * 13. MODAL DIALOG ENGINE
 * ============================================================ */

function bindModals() {
  const modal = $("#faultModal");
  const btnClose = $("#btnCloseFaultModal");
  const btnCancel = $("#btnCancelFaultModal");
  const form = $("#faultSimForm");

  if (btnClose) btnClose.addEventListener("click", closeFaultModal);
  if (btnCancel) btnCancel.addEventListener("click", closeFaultModal);

  if (modal) {
    modal.addEventListener("click", (e) => {
      if (e.target === modal) closeFaultModal();
    });
  }

  if (form) {
    form.addEventListener("submit", triggerFaultSimulation);
  }
}

function openFaultModal(preselectedComponent = null) {
  const modal = $("#faultModal");
  if (!modal) return;

  populateFaultDropdown();

  if (preselectedComponent) {
    const select = $("#modalSelectComponent");
    if (select) select.value = preselectedComponent;
  }

  modal.classList.add("show");
}

function closeFaultModal() {
  const modal = $("#faultModal");
  if (modal) modal.classList.remove("show");
}

function populateFaultDropdown() {
  const select = $("#modalSelectComponent");
  if (!select || !state.network.length) return;

  const targetNodes = state.network.filter(n => n.type === "FEEDER" || n.type === "TRANSFORMER");
  if (!targetNodes.length) return;

  select.innerHTML = targetNodes.map(n => `
    <option value="${escapeHtml(n.id)}">
      ${escapeHtml(n.id)} · ${escapeHtml(n.name)} (${escapeHtml(n.type)})
    </option>
  `).join("");
}

/* ============================================================
 * 14. RENDER ENGINE (ALL VIEWS)
 * ============================================================ */

function renderAll() {
  updateHudStrip();
  renderSvgTopology();
  renderDispatchPanel();
  renderEventStream();
  renderActiveOutagesTable();
  renderRestorationQueueTable();
  renderConsumers();
  renderTechniciansBoard();
  renderHistoryTimeline();
}

/* --- HUD STRIP --- */
function updateHudStrip() {
  const stats = state.stats;
  const activeCount = stats.activeOutages ?? state.outages.length;
  const activeNodes = stats.activeNodes ?? state.network.filter(n => n.active).length;
  const totalNodes = state.network.length || 19;
  const availCrew = stats.availableTechnicians ?? state.technicians.filter(t => t.available).length;
  const networkStatus = $("#hudNetworkStatus");

  if (networkStatus) {
    const online = activeNodes === totalNodes;
    networkStatus.textContent = online ? "Online" : "Attention";
    networkStatus.className = `hud-value ${online ? "healthy" : "fault"}`;
  }

  const outages = $("#hudActiveOutages");
  if (outages) outages.textContent = activeCount;
  if ($("#hudCrew")) $("#hudCrew").textContent = availCrew;

  if ($("#dockOutageCount")) {
    const chip = $("#dockOutageCount");
    chip.textContent = activeCount;
    chip.classList.toggle("zero", activeCount === 0);
  }
}

/* --- INTERACTIVE SVG TOPOLOGY --- */
function renderSvgTopology() {
  const nodeStatusMap = {};
  state.network.forEach(n => {
    nodeStatusMap[n.id] = n.active;
  });

  // 1. Update Node Visuals
  state.network.forEach(node => {
    const nodeG = $(`#nodeSvg-${node.id}`);
    if (!nodeG) return;

    const isActive = nodeStatusMap[node.id] !== undefined ? nodeStatusMap[node.id] : true;
    const halo = nodeG.querySelector(".node-halo");
    const shape = nodeG.querySelector(".node-body-shape");

    if (halo) {
      halo.className.baseVal = `node-halo ${isActive ? 'healthy' : 'fault'}`;
    }

    if (shape) {
      if (!isActive) {
        shape.setAttribute("stroke", "#A88965");
        shape.setAttribute("fill", "#FAF7F2");
      } else {
        shape.setAttribute("stroke", "#A88965");
        shape.setAttribute("fill", "#FAF7F2");
      }
    }
  });

  // 2. Update Circuit Lines & Currents
  const lineNodeMap = {
    "line-SS1-FD1": "FD-ERD-01",
    "line-SS2-FD2": "FD-PER-02",
    "line-SS2-FD3": "FD-CHN-03",
    "line-SS3-FD4": "FD-AVI-04",
    "line-SS3-FD5": "FD-TPR-05",
    "line-FD1-DT1": "DT-ERD-101",
    "line-FD2-DT2": "DT-PER-102",
    "line-FD3-DT3": "DT-CHN-103",
    "line-FD4-DT4": "DT-AVI-104",
    "line-FD5-DT5": "DT-TPR-105",
    "line-DT1-AR1": "AREA-ERD-PR",
    "line-DT1-AR2": "AREA-ERD-SN",
    "line-DT2-AR3": "AREA-PER-SIP",
    "line-DT3-AR4": "AREA-CHN-TWN",
    "line-DT4-AR5": "AREA-AVI-BYP",
    "line-DT5-AR6": "AREA-TPR-RAY"
  };

  Object.entries(lineNodeMap).forEach(([lineId, targetNodeId]) => {
    const lineEl = $(`#${lineId}`);
    if (!lineEl) return;

    const isTargetActive = nodeStatusMap[targetNodeId] !== undefined ? nodeStatusMap[targetNodeId] : true;

    if (!isTargetActive) {
      lineEl.className.baseVal = "circuit-line fault-broken";
    } else {
      lineEl.className.baseVal = "circuit-line active-current power-flow";
    }
  });
}

/* --- TACTICAL DISPATCH PANEL --- */
function renderDispatchPanel() {
  const idEl = $("#nextDispatchId");
  const priorityEl = $("#nextDispatchPriority");
  const consumersEl = $("#nextDispatchConsumers");
  const btn = $("#btnExecDispatch");

  if (!state.outages.length) {
    if (idEl) idEl.textContent = "No active outage";
    if (priorityEl) priorityEl.textContent = "—";
    if (consumersEl) consumersEl.textContent = "0";
    if (btn) btn.disabled = true;
    return;
  }

  const next = state.outages[0];
  if (idEl) idEl.textContent = `OUT-${next.outageId}`;
  if (priorityEl) priorityEl.textContent = String(next.priority || "—").toUpperCase();
  if (consumersEl) consumersEl.textContent = next.consumers;
  if (btn) btn.disabled = false;
}

/* --- EVENT LOG STREAM --- */
function buildInitialEventStream() {
  state.events = [];

  if (state.outages.length) {
    state.outages.forEach(o => {
      const areasStr = Array.isArray(o.areas) ? o.areas.join(", ") : "";
      addEvent(
        `⚡ Fault Detected · ${o.component}`,
        `OUT-${o.outageId} · ${areasStr} (${o.consumers} consumers) · ${o.priority} Priority`,
        "fault",
        false
      );
    });
  }

  if (state.history.length) {
    state.history.slice(0, 4).forEach(h => {
      addEvent(
        `● Power Restored · OUT-${h.outageId}`,
        `${h.component} verified online by ${h.technician || 'Crew'}`,
        "restore",
        false
      );
    });
  }

  if (!state.events.length) {
    addEvent(
      "PowerTrack Grid Mesh Active",
      "Tamil Nadu Regional Monitoring Initialized &middot; 19 Nodes Online",
      "system",
      false
    );
  }
}

function addEvent(title, sub, type = "system", rerender = true) {
  state.events.unshift({
    title,
    sub,
    type,
    time: formatTimeOnly(new Date())
  });

  state.events = state.events.slice(0, 15);

  if (rerender) {
    renderEventStream();
  }
}

function renderEventStream() {
  const container = $("#tacticalEventStream");
  if (!container) return;

  const incidents = state.events
    .filter(ev => ev.type === "fault" || ev.type === "restore")
    .slice(0, 3);

  if (!incidents.length) {
    container.innerHTML = `<div class="event-stream-empty">No recent incidents</div>`;
    return;
  }

  container.innerHTML = incidents.map(ev => {
    const outageId = `${ev.title} ${ev.sub}`.match(/OUT-[\w-]+/i)?.[0] || "Outage";
    const label = ev.type === "fault" ? "Fault Detected" : "Power Restored";
    return `
    <div class="event-stream-row">
      <span class="event-sig-dot ${escapeHtml(ev.type)}"></span>
      <div class="event-details-wrap">
        <div class="event-msg-title">${label}</div>
        <div class="event-msg-sub">${escapeHtml(outageId)}</div>
      </div>
      <span class="event-time-stamp">${escapeHtml(ev.time)}</span>
    </div>
  `;
  }).join("");
}

/* --- ACTIVE OUTAGES TABLE --- */
function renderActiveOutagesTable() {
  const tbody = $("#activeOutagesTableBody");
  if (!tbody) return;

  if (!state.outages.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="9" style="text-align: center; padding: 28px; color: var(--text-medium); font-family: var(--font-mono);">
          ✓ All distribution feeders and transformers are fully operational. No active outages.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = state.outages.map(outage => {
    const inProgress = outage.status === "IN PROGRESS";
    const areasStr = Array.isArray(outage.areas) ? outage.areas.join(", ") : "Areas";

    return `
      <tr>
        <td class="code-cell">OUT-${outage.outageId}</td>
        <td><strong>${escapeHtml(outage.component)}</strong></td>
        <td>${escapeHtml(areasStr)}</td>
        <td style="color: var(--text-medium);">${escapeHtml(outage.cause || 'Circuit Trip')}</td>
        <td>
          <span class="priority-tag ${priorityClass(outage.priority)}">
            ${escapeHtml(outage.priority)}
          </span>
        </td>
        <td>${outage.consumers} accounts</td>
        <td>${escapeHtml(outage.technician || 'Not Assigned')}</td>
        <td>
          <span class="badge-sig ${inProgress ? 'inprogress' : 'active'}">
            ${escapeHtml(outage.status)}
          </span>
        </td>
        <td>
          ${inProgress ? `
            <button
              type="button"
              class="btn-restore-action"
              onclick="restoreOutage(${outage.outageId})"
            >
              Restore Power
            </button>
          ` : `
            <span style="font-size: 10px; color: var(--text-muted); font-family: var(--font-mono);">IN QUEUE</span>
          `}
        </td>
      </tr>
    `;
  }).join("");
}

/* --- RESTORATION QUEUE TABLE --- */
function renderRestorationQueueTable() {
  const tbody = $("#restorationQueueTableBody");
  if (!tbody) return;

  if (!state.outages.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" style="text-align: center; padding: 24px; color: var(--text-medium); font-family: var(--font-mono);">
          Restoration priority queue is currently empty.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = state.outages.map((outage, index) => {
    const areasStr = Array.isArray(outage.areas) ? outage.areas.join(", ") : "";
    const inProgress = outage.status === "IN PROGRESS";

    return `
      <tr>
        <td class="code-cell" style="color: var(--accent-amber);">#${String(index + 1).padStart(2, "0")}</td>
        <td class="code-cell">OUT-${outage.outageId}</td>
        <td><strong>${escapeHtml(outage.component)}</strong></td>
        <td>${escapeHtml(areasStr)}</td>
        <td>
          <span class="priority-tag ${priorityClass(outage.priority)}">
            ${escapeHtml(outage.priority)}
          </span>
        </td>
        <td>${outage.consumers} accounts</td>
        <td>
          <span class="badge-sig ${inProgress ? 'inprogress' : 'active'}">
            ${escapeHtml(outage.status)}
          </span>
        </td>
      </tr>
    `;
  }).join("");
}

/* --- CONSUMERS REGISTRY --- */
function renderConsumers() {
  const tbody = $("#consumerRegistryTableBody");
  if (!tbody) return;

  const query = state.consumerSearchTerm.trim().toLowerCase();
  const areaFilter = state.consumerAreaFilter;

  const filtered = state.consumers.filter(c => {
    if (areaFilter !== "all" && c.area !== areaFilter) return false;
    if (query) {
      const target = `${c.id} ${c.name} ${c.area}`.toLowerCase();
      if (!target.includes(query)) return false;
    }
    return true;
  });

  if (!filtered.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" style="text-align: center; padding: 24px; color: var(--text-muted); font-family: var(--font-mono);">
          No consumer accounts matching the selected criteria.
        </td>
      </tr>
    `;
    return;
  }

  function resolveCategory(name) {
    if (name.includes("Hospital") || name.includes("Clinic") || name.includes("Health")) return "Essential / Healthcare";
    if (name.includes("Mills") || name.includes("Tannery") || name.includes("Textile") || name.includes("Knitwear") || name.includes("Dyeing")) return "HT Industrial";
    if (name.includes("Commercial") || name.includes("Bazaar") || name.includes("Logistics") || name.includes("Auto")) return "Commercial LT";
    if (name.includes("School")) return "Institutional";
    return "Domestic 3-Phase";
  }

  tbody.innerHTML = filtered.map(c => `
    <tr>
      <td class="code-cell">PT-CUS-${c.id}</td>
      <td><strong>${escapeHtml(c.name)}</strong></td>
      <td>
        <span class="badge-sig neutral">${escapeHtml(c.area)}</span>
      </td>
      <td style="color: var(--text-medium); font-size: 11.5px;">${escapeHtml(resolveCategory(c.name))}</td>
      <td>
        <span class="badge-sig ${c.powerAvailable ? 'healthy' : 'active'}">
          ${c.powerAvailable ? 'ONLINE' : 'POWER LOSS'}
        </span>
      </td>
    </tr>
  `).join("");
}

/* --- TECHNICIANS 3-COLUMN DISPATCH BOARD --- */
function renderTechniciansBoard() {
  const colAvail = $("#techColAvailable");
  const colAssigned = $("#techColAssigned");
  const colOnSite = $("#techColOnSite");

  const countAvail = $("#countTechAvail");
  const countAssigned = $("#countTechAssigned");
  const countOnSite = $("#countTechOnSite");

  if (!colAvail || !colAssigned || !colOnSite) return;

  const available = [];
  const assigned = [];
  const onSite = [];

  const zoneMap = {
    201: "Erode Central Zone",
    202: "Perundurai SIPCOT Zone",
    203: "Tiruppur North Zone",
    204: "Chennimalai Sub-Division",
    205: "Avinashi Section",
    206: "Erode Urban Grid"
  };

  state.technicians.forEach(t => {
    const assignedOutage = state.outages.find(o => o.technician === t.name);

    if (t.available) {
      available.push({ ...t, zone: zoneMap[t.id] || "Regional Mesh" });
    } else if (assignedOutage && assignedOutage.status === "IN PROGRESS") {
      onSite.push({ ...t, outage: assignedOutage, zone: zoneMap[t.id] || "Regional Mesh" });
    } else {
      assigned.push({ ...t, outage: assignedOutage, zone: zoneMap[t.id] || "Regional Mesh" });
    }
  });

  if (countAvail) countAvail.textContent = available.length;
  if (countAssigned) countAssigned.textContent = assigned.length;
  if (countOnSite) countOnSite.textContent = onSite.length;

  // Render Available
  colAvail.innerHTML = available.length > 0
    ? available.map(t => renderTechCardHtml(t, "AVAILABLE")).join("")
    : `<div style="padding: 16px; color: var(--text-muted); font-size: 11px; font-family: var(--font-mono);">All technicians currently assigned.</div>`;

  // Render Assigned
  colAssigned.innerHTML = assigned.length > 0
    ? assigned.map(t => renderTechCardHtml(t, "EN ROUTE", t.outage)).join("")
    : `<div style="padding: 16px; color: var(--text-muted); font-size: 11px; font-family: var(--font-mono);">No crews in transit.</div>`;

  // Render On Site
  colOnSite.innerHTML = onSite.length > 0
    ? onSite.map(t => renderTechCardHtml(t, "REPAIRING", t.outage)).join("")
    : `<div style="padding: 16px; color: var(--text-muted); font-size: 11px; font-family: var(--font-mono);">No active field repairs.</div>`;
}

function renderTechCardHtml(tech, statusLabel, outage = null) {
  return `
    <div class="tech-tactical-item">
      <div class="tech-top-row">
        <span class="tech-name-lbl">${escapeHtml(tech.name)}</span>
        <span class="tech-id-badge">TECH-${tech.id}</span>
      </div>
      <div class="tech-meta-row">
        <span>${escapeHtml(tech.specialization)}</span>
        <span style="font-family: var(--font-mono); font-size: 10px; color: var(--text-muted);">${escapeHtml(tech.zone)}</span>
      </div>
      <div style="display: flex; align-items: center; justify-content: space-between; border-top: 1px solid var(--border-subtle); padding-top: 6px; margin-top: 2px;">
        <span style="font-family: var(--font-mono); font-size: 10px; color: var(--accent-amber);">
          ${outage ? `TARGET: OUT-${outage.outageId}` : 'STANDBY READY'}
        </span>
        <span class="badge-sig ${statusLabel === 'AVAILABLE' ? 'healthy' : (statusLabel === 'REPAIRING' ? 'active' : 'inprogress')}">
          ${statusLabel}
        </span>
      </div>
    </div>
  `;
}

/* --- INCIDENT TIMELINE HISTORY --- */
function renderHistoryTimeline() {
  const container = $("#historyTimelineFeed");
  if (!container) return;

  if (!state.history.length) {
    container.innerHTML = `<div style="padding: 16px; color: var(--text-muted); font-family: var(--font-mono);">No historical outage records found.</div>`;
    return;
  }

  container.innerHTML = state.history.map(h => {
    const areasStr = Array.isArray(h.areas) ? h.areas.join(", ") : "Areas";

    return `
      <div class="history-timeline-node">
        <div class="hist-top-line">
          <span class="hist-outage-code">OUT-${h.outageId} · ${escapeHtml(h.component)}</span>
          <span class="badge-sig healthy">RESTORED &amp; VERIFIED</span>
        </div>
        <div class="hist-desc-txt">
          <strong>Cause:</strong> ${escapeHtml(h.cause || 'Circuit Trip')} &middot; 
          <strong>Affected:</strong> ${escapeHtml(areasStr)} (${h.consumers} consumers)
        </div>
        <div style="display: flex; align-items: center; justify-content: space-between; font-size: 10.5px; color: var(--text-muted); font-family: var(--font-mono); border-top: 1px solid var(--border-subtle); padding-top: 6px; margin-top: 2px;">
          <span>Resolving Crew: <strong style="color: var(--text-high);">${escapeHtml(h.technician || 'Field Crew')}</strong></span>
          <span>Status: ARCHIVED</span>
        </div>
      </div>
    `;
  }).join("");
}

/* ============================================================
 * 15. UTILITIES & TIMERS
 * ============================================================ */

function startTerminalClock() {
  function tick() {
    const clock = $("#terminalClock");
    if (clock) {
      const now = new Date();
      clock.textContent = now.toLocaleTimeString("en-GB", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false
      });
    }
  }
  tick();
  setInterval(tick, 1000);
}

function formatTimeOnly(date) {
  return date.toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}

function normalizePriority(priority) {
  return String(priority || "LOW").trim().toLowerCase();
}

function priorityClass(priority) {
  const p = normalizePriority(priority);
  if (p === "critical" || p === "high") return "critical";
  if (p === "medium") return "medium";
  return "low";
}

function comparePriority(a, b) {
  const weights = { "CRITICAL": 1, "HIGH": 2, "MEDIUM": 3, "LOW": 4, "PENDING": 5 };
  const wA = weights[String(a.priority).toUpperCase()] || 99;
  const wB = weights[String(b.priority).toUpperCase()] || 99;
  return wA - wB;
}

function setButtonLoading(btn, loading, text) {
  if (!btn) return;
  btn.disabled = loading;
  btn.textContent = text;
}

function showToast(message, type = "success") {
  const container = $("#toastShelf");
  if (!container) return;

  const toast = document.createElement("div");
  toast.className = `toast-pill-tac ${type}`;
  toast.innerHTML = `<span>${type === 'error' ? '⚡' : '●'}</span><span>${escapeHtml(message)}</span>`;

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transform = "translateY(8px)";
    setTimeout(() => toast.remove(), 250);
  }, 4000);
}

function escapeHtml(val) {
  if (val === null || val === undefined) return "";
  return String(val)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}