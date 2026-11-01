/**
 * PowerTrack: Smart Electricity Outage Detection and Restoration System
 * Frontend Application Logic (Vanilla JavaScript)
 * Supports Role Selection, Government Dashboard, and Technician Dashboard
 */

(function () {
    "use strict";

    // =========================================================
    // CONFIGURATION & GLOBAL STATE
    // =========================================================

    const BASE_URL = window.location.protocol.startsWith("http") && window.location.port === "8080"
        ? ""
        : "http://localhost:8080";

    let currentView = "role-selection"; // "role-selection" | "government" | "technician"
    let currentGovOperation = 1;
    let currentTechTab = "active"; // "active" | "completed"
    let selectedTechnicianId = "ALL";
    let backendOnline = false;

    // Technician Dashboard & Filter State
    let cachedTechniciansList = [];
    let techFilterSearchQuery = "";
    let techFilterStatus = "ALL";

    function formatTechId(id) {
        if (!id) return "";
        const str = String(id).trim();
        return str.startsWith("T") ? str : `T${str}`;
    }

    // Government Dashboard Operations Metadata (Operations 1 to 7)
    // Note: Restoration control is strictly assigned to Technician Dashboard
    const GOV_OPERATIONS = {
        1: {
            number: "01",
            title: "View Electricity Network",
            subtitle: "Explore regional distribution hierarchy, substations, and feeders.",
            render: renderGovOperation1
        },
        2: {
            number: "02",
            title: "View Registered Consumers",
            subtitle: "Audit consumer accounts, categories, and hospital criticality ratings.",
            render: renderGovOperation2
        },
        3: {
            number: "03",
            title: "Field Workforce Roster",
            subtitle: "Monitor technicians, active crew assignments, and standby availability.",
            render: renderGovOperation3
        },
        4: {
            number: "04",
            title: "Simulate Fault Detection",
            subtitle: "Register an electrical fault to analyze downstream area and consumer impact.",
            render: renderGovOperation4
        },
        5: {
            number: "05",
            title: "Active Outages & Queue",
            subtitle: "Monitor active outages, emergency priority queue rankings, and affected areas.",
            render: renderGovOperation5
        },
        6: {
            number: "06",
            title: "Dispatch & Technician Assignment",
            subtitle: "Dispatch next queued outage or assign dedicated technicians to active outages.",
            render: renderGovOperation6
        },
        7: {
            number: "07",
            title: "Outage History & Audit Log",
            subtitle: "Audit historical restoration records, resolved grid faults, and crew records.",
            render: renderGovOperation7
        }
    };

    // =========================================================
    // API CLIENT HELPER
    // =========================================================

    // Map to prevent duplicate overlapping in-flight GET requests
    const inFlightRequests = new Map();

    async function apiRequest(endpoint, options = {}) {
        const method = (options.method || "GET").toUpperCase();

        // Deduplicate concurrent overlapping GET requests for the same endpoint
        if (method === "GET" && inFlightRequests.has(endpoint)) {
            return inFlightRequests.get(endpoint);
        }

        const requestPromise = (async () => {
            const url = BASE_URL + endpoint;
            const defaultHeaders = {
                "Accept": "application/json"
            };

            if (options.body) {
                defaultHeaders["Content-Type"] = "application/json";
            }

            const config = {
                ...options,
                headers: {
                    ...defaultHeaders,
                    ...(options.headers || {})
                }
            };

            try {
                const response = await fetch(url, config);
                const contentType = response.headers.get("content-type") || "";

                let data;
                if (contentType.includes("application/json")) {
                    data = await response.json();
                } else {
                    data = await response.text();
                }

                if (!response.ok) {
                    const message = (typeof data === "object" && data.message)
                        ? data.message
                        : `Request failed with status ${response.status}`;
                    throw new Error(message);
                }

                setBackendStatus(true);
                return data;
            } catch (err) {
                if (err.name === "TypeError" && err.message.includes("fetch")) {
                    setBackendStatus(false, "Backend: Offline (Port 8080)");
                    throw new Error(`Unable to reach PowerTrack backend server at ${BASE_URL || "http://localhost:8080"}. Ensure PowerTrackServer is running.`);
                }
                throw err;
            }
        })();

        if (method === "GET") {
            inFlightRequests.set(endpoint, requestPromise);
            requestPromise.finally(() => inFlightRequests.delete(endpoint));
        }

        return requestPromise;
    }

    // =========================================================
    // STATUS INDICATOR SYNCHRONIZATION
    // =========================================================

    function setBackendStatus(isOnline, statusMessage) {
        backendOnline = isOnline;

        const dotIds = [
            "role-backend-status-dot",
            "gov-backend-status-dot",
            "tech-backend-status-dot"
        ];
        const textIds = [
            "role-backend-status-text",
            "gov-backend-status-text",
            "tech-backend-status-text"
        ];

        dotIds.forEach(id => {
            const dot = document.getElementById(id);
            if (dot) {
                dot.className = isOnline ? "status-dot connected" : "status-dot disconnected";
            }
        });

        textIds.forEach(id => {
            const text = document.getElementById(id);
            if (text) {
                text.textContent = statusMessage || (isOnline ? "Database: Connected (Supabase)" : "Database: Offline");
            }
        });
    }

    async function checkBackendHealth() {
        try {
            const health = await apiRequest("/api/health", { method: "GET" });
            if (health && health.status === "UP" && health.database === "CONNECTED") {
                setBackendStatus(true, "Database: Connected (Supabase)");
                updateSystemMetrics();
            } else {
                setBackendStatus(false, "Database: Disconnected");
            }
        } catch (e) {
            setBackendStatus(false, "Backend: Offline");
        }
    }

    async function updateSystemMetrics() {
        try {
            // Fetch combined summary in 1 fast, targeted call avoiding multiple round-trips
            const summary = await apiRequest("/api/dashboard/summary").catch(async () => {
                const s = await apiRequest("/api/stats");
                return { stats: s };
            });

            const stats = summary.stats || summary;

            // 1. Government Metrics
            const govNodes = document.getElementById("gov-metric-nodes");
            const govConsumers = document.getElementById("gov-metric-consumers");
            const govOutages = document.getElementById("gov-metric-outages");
            const govTechs = document.getElementById("gov-metric-technicians");

            if (govNodes) govNodes.textContent = stats.activeNodes ?? "-";
            if (govConsumers) govConsumers.textContent = `${stats.powerAvailable ?? 0} / ${stats.totalConsumers ?? 0}`;
            if (govOutages) govOutages.textContent = stats.activeOutages ?? "0";
            if (govTechs) govTechs.textContent = `${stats.availableTechnicians ?? 0}`;

            // 2. Technician Metrics (directly from pre-aggregated stats)
            const assignedCount = stats.assignedOutages ?? 0;
            const inProgressCount = stats.inProgressOutages ?? 0;
            const restoredCount = stats.restoredOutages ?? 0;
            const activeCount = stats.activeOutages ?? 0;

            const techAssigned = document.getElementById("tech-metric-assigned");
            const techInProgress = document.getElementById("tech-metric-inprogress");
            const techRestored = document.getElementById("tech-metric-restored");
            const techAvailable = document.getElementById("tech-metric-available");

            if (techAssigned) techAssigned.textContent = String(assignedCount);
            if (techInProgress) techInProgress.textContent = String(inProgressCount);
            if (techRestored) techRestored.textContent = String(restoredCount);
            if (techAvailable) techAvailable.textContent = `${stats.availableTechnicians ?? 0}`;

            // Update badge counters on technician tabs
            const activeBadge = document.getElementById("tech-badge-active-count");
            const completedBadge = document.getElementById("tech-badge-completed-count");
            if (activeBadge) activeBadge.textContent = String(activeCount);
            if (completedBadge) completedBadge.textContent = String(restoredCount);

        } catch (e) {
            // Keep placeholder dashes on error
        }
    }

    // =========================================================
    // VIEW ROUTING: ROLE SELECTION, GOVERNMENT, TECHNICIAN
    // =========================================================

    function switchView(viewName) {
        // Prevent duplicate re-initialization if the requested view is already active
        const activeElem = document.querySelector(".app-view.active-view");
        if (currentView === viewName && activeElem && activeElem.id === `view-${viewName === "role-selection" ? "role-selection" : (viewName + "-dashboard")}`) {
            return;
        }

        currentView = viewName;

        const roleView = document.getElementById("view-role-selection");
        const govView = document.getElementById("view-government-dashboard");
        const techView = document.getElementById("view-technician-dashboard");

        if (roleView) roleView.classList.remove("active-view");
        if (govView) govView.classList.remove("active-view");
        if (techView) techView.classList.remove("active-view");

        // Synchronize URL hash without re-triggering duplicate route listeners
        const currentHash = (window.location.hash || "").replace("#", "").toLowerCase();
        if (viewName === "government" && currentHash !== "government") {
            window.location.hash = "government";
        } else if (viewName === "technician" && currentHash !== "technician") {
            window.location.hash = "technician";
        } else if (viewName === "role-selection" && window.location.hash) {
            history.replaceState(null, "", window.location.pathname);
        }

        if (viewName === "government") {
            if (govView) govView.classList.add("active-view");
            selectGovOperation(currentGovOperation);
            updateSystemMetrics();
        } else if (viewName === "technician") {
            if (techView) techView.classList.add("active-view");
            // Run independent initialization requests concurrently
            Promise.all([
                loadTechnicianRosterOptions(true),
                loadTechnicianContent(),
                updateSystemMetrics()
            ]);
        } else {
            // Default: Role Selection
            if (roleView) roleView.classList.add("active-view");
            checkBackendHealth();
        }

        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    function handleHashRouting() {
        const hash = (window.location.hash || "").replace("#", "").toLowerCase();
        if (hash === "government") {
            switchView("government");
        } else if (hash === "technician") {
            switchView("technician");
        } else {
            switchView("role-selection");
        }
    }

    // =========================================================
    // GOVERNMENT DASHBOARD OPERATIONS
    // =========================================================

    function selectGovOperation(opNumber) {
        if (!GOV_OPERATIONS[opNumber]) return;
        currentGovOperation = opNumber;

        // Update operational tab card styling
        for (let i = 1; i <= 7; i++) {
            const card = document.getElementById(`card-gov-op-${i}`);
            const label = document.getElementById(`label-gov-op-${i}`);
            if (card) {
                if (i === opNumber) {
                    card.classList.add("selected");
                    card.setAttribute("aria-selected", "true");
                    if (label) label.textContent = "ACTIVE";
                } else {
                    card.classList.remove("selected");
                    card.setAttribute("aria-selected", "false");
                    if (label) label.textContent = "OPEN";
                }
            }
        }

        // Update Content Panel Header
        const meta = GOV_OPERATIONS[opNumber];
        const tag = document.getElementById("gov-panel-op-tag");
        const title = document.getElementById("gov-panel-title");
        const subtitle = document.getElementById("gov-panel-subtitle");

        if (tag) tag.textContent = `OPERATION ${meta.number}`;
        if (title) title.textContent = meta.title;
        if (subtitle) subtitle.textContent = meta.subtitle;

        // Render operation content
        const body = document.getElementById("gov-panel-content-body");
        if (body && typeof meta.render === "function") {
            body.innerHTML = '<div class="loading-indicator">Loading government grid data...</div>';
            meta.render(body);
        }
    }

    // --- Government Op 01: View Electricity Network ---
    async function renderGovOperation1(container) {
        try {
            const nodes = await apiRequest("/api/network");

            if (!Array.isArray(nodes) || nodes.length === 0) {
                container.innerHTML = '<div class="notice-box empty">No grid network nodes returned by backend.</div>';
                return;
            }

            const substations = nodes.filter(n => n.type === "SUBSTATION");
            const feeders = nodes.filter(n => n.type === "FEEDER");
            const transformers = nodes.filter(n => n.type === "TRANSFORMER");
            const areas = nodes.filter(n => n.type === "AREA");

            let html = `
                <div class="topology-container">
                    <div class="topology-title">Distribution Network Hierarchy (Tamil Nadu Regional Grid)</div>
                    <div class="topology-levels">

                        <div class="topology-level">
                            <div class="level-name">Level 1: 230kV / 110kV Transmission Substations</div>
                            <div class="level-nodes">
                                ${substations.map(n => `
                                    <div class="node-box ${n.active ? "" : "inactive"}">
                                        <span class="node-id">${escapeHtml(n.id)} [${n.active ? "ACTIVE" : "OFFLINE"}]</span>
                                        <span class="node-desc">${escapeHtml(n.name)}</span>
                                    </div>
                                `).join("")}
                            </div>
                        </div>

                        <div class="topology-level">
                            <div class="level-name">Level 2: 11kV / 22kV High-Tension Distribution Feeders</div>
                            <div class="level-nodes">
                                ${feeders.map(n => `
                                    <div class="node-box ${n.active ? "" : "inactive"}">
                                        <span class="node-id">${escapeHtml(n.id)} [${n.active ? "ACTIVE" : "OFFLINE"}]</span>
                                        <span class="node-desc">${escapeHtml(n.name)}</span>
                                    </div>
                                `).join("")}
                            </div>
                        </div>

                        <div class="topology-level">
                            <div class="level-name">Level 3: Step-Down Distribution Transformers (11kV / 415V)</div>
                            <div class="level-nodes">
                                ${transformers.map(n => `
                                    <div class="node-box ${n.active ? "" : "inactive"}">
                                        <span class="node-id">${escapeHtml(n.id)} [${n.active ? "ACTIVE" : "OFFLINE"}]</span>
                                        <span class="node-desc">${escapeHtml(n.name)}</span>
                                    </div>
                                `).join("")}
                            </div>
                        </div>

                        <div class="topology-level">
                            <div class="level-name">Level 4: Consumer Locality Distribution Areas</div>
                            <div class="level-nodes">
                                ${areas.map(n => `
                                    <div class="node-box ${n.active ? "" : "inactive"}">
                                        <span class="node-id">${escapeHtml(n.id)} [${n.active ? "ACTIVE" : "OFFLINE"}]</span>
                                        <span class="node-desc">${escapeHtml(n.name)}</span>
                                    </div>
                                `).join("")}
                            </div>
                        </div>

                    </div>
                </div>

                <h4 style="font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 10px; color: var(--color-dark);">Grid Component Inventory &amp; Connections</h4>
                <div class="table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Node ID</th>
                                <th>Component Name</th>
                                <th>Grid Classification</th>
                                <th>Operating Status</th>
                                <th>Directly Connected Elements</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${nodes.map(n => {
                                const connections = Array.isArray(n.connectedTo) && n.connectedTo.length > 0
                                    ? n.connectedTo.join(", ")
                                    : "None";
                                return `
                                    <tr>
                                        <td><strong>${escapeHtml(n.id)}</strong></td>
                                        <td>${escapeHtml(n.name)}</td>
                                        <td>${escapeHtml(n.type)}</td>
                                        <td>
                                            <span class="badge ${n.active ? "badge-restored" : "badge-outage"}">
                                                ${n.active ? "ACTIVE" : "FAULT / OFFLINE"}
                                            </span>
                                        </td>
                                        <td>${escapeHtml(connections)}</td>
                                    </tr>
                                `;
                            }).join("")}
                        </tbody>
                    </table>
                </div>
            `;

            container.innerHTML = html;
        } catch (err) {
            renderErrorNotice(container, err);
        }
    }

    // --- Government Op 02: View Registered Consumers ---
    async function renderGovOperation2(container) {
        try {
            const consumers = await apiRequest("/api/consumers");

            if (!Array.isArray(consumers) || consumers.length === 0) {
                container.innerHTML = '<div class="notice-box empty">No registered consumers found in backend.</div>';
                return;
            }

            const total = consumers.length;
            const powered = consumers.filter(c => c.powerAvailable).length;
            const unpowered = total - powered;

            let html = `
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px;">
                    <div style="font-size: 12px; color: var(--text-secondary);">
                        Total Accounts: <strong>${total}</strong> |
                        Powered Normal: <strong style="color: var(--tone-success);">${powered}</strong> |
                        Outage Interrupted: <strong style="color: var(--tone-high);">${unpowered}</strong>
                    </div>
                    <div style="max-width: 280px; width: 100%;">
                        <input type="text" id="consumer-filter-input" class="form-input" placeholder="Filter by name, ID, or area..." style="padding: 6px 10px; font-size: 12px;">
                    </div>
                </div>

                <div class="table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Consumer ID</th>
                                <th>Account / Facility Name</th>
                                <th>Category Type</th>
                                <th>Criticality Rating</th>
                                <th>Grid Locality Area</th>
                                <th>Power Status</th>
                            </tr>
                        </thead>
                        <tbody id="consumer-tbody">
                            ${renderConsumerRows(consumers)}
                        </tbody>
                    </table>
                </div>
            `;

            container.innerHTML = html;

            const filterInput = document.getElementById("consumer-filter-input");
            const tbody = document.getElementById("consumer-tbody");
            if (filterInput && tbody) {
                filterInput.addEventListener("input", (e) => {
                    const term = e.target.value.toLowerCase().trim();
                    const filtered = consumers.filter(c =>
                        String(c.id).includes(term) ||
                        (c.name && c.name.toLowerCase().includes(term)) ||
                        (c.area && c.area.toLowerCase().includes(term)) ||
                        (c.consumerType && c.consumerType.toLowerCase().includes(term)) ||
                        (c.criticalityLevel && c.criticalityLevel.toLowerCase().includes(term))
                    );
                    tbody.innerHTML = renderConsumerRows(filtered);
                });
            }

        } catch (err) {
            renderErrorNotice(container, err);
        }
    }

    function renderConsumerRows(consumers) {
        if (!consumers || consumers.length === 0) {
            return '<tr><td colspan="6" style="text-align: center; color: var(--text-secondary);">No consumers match filter criteria.</td></tr>';
        }

        return consumers.map(c => {
            const criticality = c.criticalityLevel || inferConsumerCriticality(c.name, c.consumerType);
            const isHigh = criticality === "HIGH";
            return `
            <tr class="${isHigh ? "high-priority-row" : ""}">
                <td><strong>CON-${escapeHtml(String(c.id))}</strong></td>
                <td>
                    <strong>${escapeHtml(c.name)}</strong>
                    ${isHigh ? `<div style="font-size: 10px; color: var(--tone-high); font-weight: 700; margin-top: 2px;">ESSENTIAL FACILITY</div>` : ""}
                </td>
                <td>${escapeHtml(c.consumerType || "General")}</td>
                <td>
                    <span class="badge badge-${criticality.toLowerCase()}">
                        ${escapeHtml(criticality)}
                    </span>
                </td>
                <td>${escapeHtml(c.area)}</td>
                <td>
                    <span class="badge ${c.powerAvailable ? "badge-restored" : "badge-outage"}">
                        ${c.powerAvailable ? "POWER ON" : "OUTAGE"}
                    </span>
                </td>
            </tr>
            `;
        }).join("");
    }

    function inferConsumerCriticality(name, type) {
        const t = (type || "").toUpperCase();
        const n = (name || "").toLowerCase();

        if (t.includes("HOSPITAL") || t.includes("CLINIC") || t.includes("HEALTH") || t.includes("EMERGENCY") || t.includes("WATER") ||
            n.includes("hospital") || n.includes("clinic") || n.includes("health center") || n.includes("emergency") || n.includes("water supply")) {
            return "HIGH";
        }
        if (t.includes("COMMERCIAL") || t.includes("INDUSTRIAL") || t.includes("EDUCATION") ||
            n.includes("college") || n.includes("school") || n.includes("spinning") || n.includes("mill") || n.includes("textile")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    // --- Government Op 03: Field Workforce Roster ---
    async function renderGovOperation3(container) {
        try {
            const technicians = await apiRequest("/api/technicians");

            if (!Array.isArray(technicians) || technicians.length === 0) {
                container.innerHTML = '<div class="notice-box empty">No field technicians registered in roster.</div>';
                return;
            }

            const total = technicians.length;
            const available = technicians.filter(t => t.available).length;
            const dispatched = total - available;

            let html = `
                <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 14px;">
                    Total Personnel: <strong>${total}</strong> |
                    Available on Standby: <strong style="color: var(--tone-success);">${available}</strong> |
                    Dispatched to Outages: <strong style="color: var(--tone-medium);">${dispatched}</strong>
                </div>

                <div class="table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Technician ID</th>
                                <th>Personnel Name</th>
                                <th>Technical Specialization</th>
                                <th>Current Duty Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${technicians.map(t => `
                                <tr>
                                    <td><strong>${escapeHtml(formatTechId(t.technicianId || t.id))}</strong></td>
                                    <td>${escapeHtml(t.name)}</td>
                                    <td>${escapeHtml(t.specialization || t.skills)}</td>
                                    <td>
                                        <span class="badge ${t.available ? "badge-restored" : "badge-in-progress"}">
                                            ${t.available ? "AVAILABLE ON STANDBY" : "DISPATCHED / ACTIVE ON SITE"}
                                        </span>
                                    </td>
                                </tr>
                            `).join("")}
                        </tbody>
                    </table>
                </div>
            `;

            container.innerHTML = html;
        } catch (err) {
            renderErrorNotice(container, err);
        }
    }

    // --- Government Op 04: Simulate Fault Detection ---
    async function renderGovOperation4(container) {
        let nodes = [];
        try {
            nodes = await apiRequest("/api/network");
        } catch (err) {
            renderErrorNotice(container, err);
            return;
        }

        const feeders = Array.isArray(nodes) ? nodes.filter(n => n.type === "FEEDER") : [];
        const transformers = Array.isArray(nodes) ? nodes.filter(n => n.type === "TRANSFORMER") : [];
        const hasComponents = feeders.length > 0 || transformers.length > 0;

        let optionsHtml = "";
        if (!hasComponents) {
            optionsHtml = '<option value="">No distribution components in database</option>';
        } else {
            if (feeders.length > 0) {
                optionsHtml += `<optgroup label="11kV / 22kV Distribution Feeders">
                    ${feeders.map(f => `<option value="${escapeHtml(f.id)}">${escapeHtml(f.id)} (${escapeHtml(f.name)}) [${f.active ? "OPERATIONAL" : "FAULT / OFFLINE"}]</option>`).join("")}
                </optgroup>`;
            }
            if (transformers.length > 0) {
                optionsHtml += `<optgroup label="Distribution Step-Down Transformers">
                    ${transformers.map(t => `<option value="${escapeHtml(t.id)}">${escapeHtml(t.id)} (${escapeHtml(t.name)}) [${t.active ? "OPERATIONAL" : "FAULT / OFFLINE"}]</option>`).join("")}
                </optgroup>`;
            }
        }

        let html = `
            <div class="form-box">
                <div style="font-size: 13px; line-height: 1.5; margin-bottom: 14px;">
                    Simulate an electrical trip or fault on a feeder line or transformer. The system evaluates downstream consumers, checks for hospitals or critical facilities, and assigns priority ranking in the restoration queue.
                </div>

                <form id="gov-simulate-fault-form">
                    <div class="form-group">
                        <label for="gov-fault-component-select" class="form-label">Select Target Component</label>
                        <select id="gov-fault-component-select" class="form-select" ${!hasComponents ? "disabled" : ""}>
                            ${optionsHtml}
                        </select>
                        <div class="form-help">Eligible targets: Feeders (FD-...) or Distribution Transformers (DT-...).</div>
                    </div>

                    <div class="form-group">
                        <label for="gov-fault-component-input" class="form-label">Or Enter Component ID Directly</label>
                        <input type="text" id="gov-fault-component-input" class="form-input" placeholder="e.g. FD-ERODE-01 or DT-PERUNDURAI-01">
                    </div>

                    <button type="submit" id="gov-btn-simulate" class="btn btn-action" ${!hasComponents ? "disabled" : ""}>
                        Simulate Fault Detection &amp; Enqueue
                    </button>
                </form>
            </div>

            <div id="gov-simulate-result-box"></div>
        `;

        container.innerHTML = html;

        const form = document.getElementById("gov-simulate-fault-form");
        let isSimulatingFault = false;

        if (form) {
            form.addEventListener("submit", async (e) => {
                e.preventDefault();
                if (isSimulatingFault) return;

                const selectElem = document.getElementById("gov-fault-component-select");
                const inputElem = document.getElementById("gov-fault-component-input");
                const resultBox = document.getElementById("gov-simulate-result-box");
                const submitBtn = document.getElementById("gov-btn-simulate");

                const componentId = inputElem && inputElem.value.trim()
                    ? inputElem.value.trim().toUpperCase()
                    : (selectElem ? selectElem.value : "");

                if (!componentId) {
                    alert("Please select or enter a component ID.");
                    return;
                }

                const confirmed = confirm(`Simulate electrical fault on component ${componentId}?`);
                if (!confirmed) return;

                isSimulatingFault = true;
                if (submitBtn) {
                    submitBtn.disabled = true;
                    submitBtn.textContent = "Simulating...";
                }
                resultBox.innerHTML = '<div class="loading-indicator">Simulating fault and executing grid topology traversal...</div>';

                try {
                    const result = await apiRequest("/api/outages/simulate", {
                        method: "POST",
                        body: JSON.stringify({ component: componentId })
                    });

                    if (result.success && result.outage) {
                        const out = result.outage;
                        const areasList = Array.isArray(out.areas) ? out.areas.join(", ") : "None";
                        const critFacilities = Array.isArray(out.criticalFacilities) && out.criticalFacilities.length > 0
                            ? out.criticalFacilities.map(f => `<li><strong>${escapeHtml(f)}</strong></li>`).join("")
                            : "<li>None detected</li>";

                        const isHigh = (out.priority || "").toUpperCase() === "HIGH";

                        // Update select dropdown option in-place to reflect fault
                        if (selectElem) {
                            for (let opt of selectElem.options) {
                                if (opt.value === componentId) {
                                    opt.text = opt.text.replace("[OPERATIONAL]", "[FAULT / OFFLINE]");
                                    break;
                                }
                            }
                        }

                        resultBox.innerHTML = `
                            <div class="notice-box success">
                                <strong>Fault Registered Successfully in Supabase Database</strong>
                                <div style="margin-top: 8px; line-height: 1.6;">
                                    <div>Outage ID: <strong>OUT-${escapeHtml(String(out.outageId || out.id))}</strong></div>
                                    <div>Failed Component: <strong>${escapeHtml(out.component || out.node)}</strong></div>
                                    <div style="margin: 6px 0;">
                                        Restoration Priority: <span class="badge badge-${(out.priority || "").toLowerCase()}">${escapeHtml(out.priority)}</span>
                                        ${out.queuePosition > 0 ? `<span style="margin-left: 8px; font-weight: 700;">Queue Position: #${out.queuePosition}</span>` : ""}
                                    </div>
                                    ${isHigh ? `
                                        <div style="margin: 8px 0; padding: 8px 12px; background-color: var(--tone-high-bg); border-left: 4px solid var(--tone-high); color: var(--tone-high); font-weight: 700; font-size: 12px;">
                                            CRITICAL FACILITY DETECTED: This outage is prioritized at the top of the queue to protect healthcare and essential services.
                                        </div>
                                    ` : ""}
                                    <div>Priority Reason: <em>${escapeHtml(out.priorityReason || "")}</em></div>
                                    <div style="margin-top: 4px;">Critical Facilities Affected:</div>
                                    <ul style="margin: 3px 0 8px 20px; font-size: 12px;">${critFacilities}</ul>
                                    <div>Downstream Localities: <strong>${escapeHtml(areasList)}</strong></div>
                                    <div>Impacted Consumers: <strong>${escapeHtml(String(out.consumers))}</strong></div>
                                    <div>Current Status: <span class="badge badge-outage">${escapeHtml(out.status)}</span></div>
                                </div>
                            </div>
                        `;
                    } else {
                        resultBox.innerHTML = `
                            <div class="notice-box error">
                                <strong>Simulation Failed:</strong> ${escapeHtml(result.message || "Failed to create outage.")}
                            </div>
                        `;
                    }
                    updateSystemMetrics();
                } catch (err) {
                    resultBox.innerHTML = `
                        <div class="notice-box error">
                            <strong>Simulation Error:</strong> ${escapeHtml(err.message)}
                        </div>
                    `;
                } finally {
                    isSimulatingFault = false;
                    if (submitBtn) {
                        submitBtn.disabled = false;
                        submitBtn.textContent = "Simulate Fault Detection & Enqueue";
                    }
                }
            });
        }
    }

    // --- Government Op 05: Active Outages & Queue ---
    async function renderGovOperation5(container) {
        try {
            const activeOutages = await apiRequest("/api/outages/active");

            if (!Array.isArray(activeOutages) || activeOutages.length === 0) {
                container.innerHTML = `
                    <div class="notice-box empty">
                        <strong>No active outages.</strong> All regional distribution circuits and transformers are currently operational.
                    </div>
                `;
                return;
            }

            const queuedCount = activeOutages.filter(o => o.status === "ACTIVE").length;
            const inProgressCount = activeOutages.filter(o => o.status === "IN PROGRESS").length;

            let html = `
                <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 14px;">
                    Total Active Incidents: <strong>${activeOutages.length}</strong> |
                    Queued (Awaiting Crew): <strong style="color: var(--tone-high);">${queuedCount}</strong> |
                    In Progress (Crew Dispatched): <strong style="color: var(--tone-medium);">${inProgressCount}</strong>
                </div>

                <div class="table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th style="width: 80px; text-align: center;">Queue Pos</th>
                                <th>Outage ID</th>
                                <th>Priority</th>
                                <th>Critical Facilities &amp; Priority Reason</th>
                                <th>Failed Component</th>
                                <th>Consumers</th>
                                <th>Assigned Technician</th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${activeOutages.map(o => {
                                const areas = Array.isArray(o.areas) ? o.areas.join(", ") : "None";
                                const isQueued = o.status === "ACTIVE";
                                const isHigh = (o.priority || "").toUpperCase() === "HIGH";
                                const critList = Array.isArray(o.criticalFacilities) && o.criticalFacilities.length > 0
                                    ? o.criticalFacilities.map(f => `<div style="font-weight: 600; font-size: 11px;">${escapeHtml(f)}</div>`).join("")
                                    : `<span style="color: var(--text-secondary); font-size: 11px;">None</span>`;

                                return `
                                    <tr class="${isHigh && isQueued ? "high-priority-row" : ""}">
                                        <td style="text-align: center;">
                                            ${isQueued
                                                ? `<span style="font-weight: 700; color: var(--tone-high); font-size: 13px;">#${o.queuePosition || 1}</span>`
                                                : `<span style="font-size: 11px; color: var(--color-accent); font-weight: 600;">Dispatched</span>`
                                            }
                                        </td>
                                        <td><strong>OUT-${escapeHtml(String(o.outageId || o.id))}</strong></td>
                                        <td>
                                            <span class="badge badge-${(o.priority || "").toLowerCase()}">
                                                ${escapeHtml(o.priority)}
                                            </span>
                                        </td>
                                        <td>
                                            ${isHigh ? `<div style="display: inline-block; padding: 2px 6px; background-color: var(--tone-high-bg); border: 1px solid var(--tone-high); color: var(--tone-high); font-size: 10px; font-weight: 700; border-radius: 2px; margin-bottom: 4px;">ESSENTIAL FACILITY</div>` : ""}
                                            <div style="margin-bottom: 3px;">${critList}</div>
                                            <div style="font-size: 11px; color: var(--text-secondary);"><em>${escapeHtml(o.priorityReason || "")}</em></div>
                                        </td>
                                        <td>
                                            <strong>${escapeHtml(o.component || o.node)}</strong>
                                            <div style="font-size: 11px; color: var(--text-secondary);">${escapeHtml(areas)}</div>
                                        </td>
                                        <td><strong>${escapeHtml(String(o.consumers || 0))}</strong></td>
                                        <td>${escapeHtml(o.technician || "NOT ASSIGNED")}</td>
                                        <td>
                                            <span class="badge ${isQueued ? "badge-outage" : "badge-in-progress"}">
                                                ${escapeHtml(o.status)}
                                            </span>
                                        </td>
                                    </tr>
                                `;
                            }).join("")}
                        </tbody>
                    </table>
                </div>
            `;

            container.innerHTML = html;
        } catch (err) {
            renderErrorNotice(container, err);
        }
    }

    // --- Government Op 06: Outage Dispatch & Technician Assignment ---
    async function renderGovOperation6(container) {
        try {
            const [activeOutages, technicians] = await Promise.all([
                apiRequest("/api/outages/active").catch(() => []),
                apiRequest("/api/technicians").catch(() => [])
            ]);

            const queuedList = Array.isArray(activeOutages)
                ? activeOutages.filter(o => o.status === "ACTIVE")
                : [];
            const availableTechs = Array.isArray(technicians)
                ? technicians.filter(t => t.available)
                : [];

            let html = `
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 20px;">
                    
                    <!-- Box A: Auto-Dispatch Next Highest Priority Outage -->
                    <div class="form-box">
                        <h4 style="font-size: 14px; font-weight: 700; color: var(--color-dark); margin-bottom: 10px; text-transform: uppercase;">
                            Option A: Priority Queue Auto-Dispatch
                        </h4>
                        <div style="font-size: 12.5px; color: var(--text-secondary); margin-bottom: 14px; line-height: 1.5;">
                            Dispatches the next highest-priority queued outage (hospitals and critical loads first), automatically matching the qualified technician.
                        </div>

                        <div style="font-size: 12px; margin-bottom: 16px; padding: 10px; background-color: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 3px;">
                            Queued Outages Waiting: <strong>${queuedList.length}</strong> | Available Crews: <strong>${availableTechs.length}</strong>
                            ${queuedList.length > 0 ? `
                                <div style="margin-top: 6px; padding-top: 6px; border-top: 1px dashed var(--border-color); color: var(--color-dark);">
                                    Top Priority in Queue: <strong>OUT-${escapeHtml(String(queuedList[0].outageId || queuedList[0].id))}</strong>
                                    [<span class="badge badge-${(queuedList[0].priority || "").toLowerCase()}">${escapeHtml(queuedList[0].priority)}</span>]
                                    - <em>${escapeHtml(queuedList[0].component || queuedList[0].node)}</em>
                                </div>
                            ` : ""}
                        </div>

                        <button type="button" id="gov-btn-auto-dispatch" class="btn btn-action" ${queuedList.length === 0 ? "disabled" : ""}>
                            Dispatch Highest-Priority Outage
                        </button>
                    </div>

                    <!-- Box B: Direct Manual Technician Assignment -->
                    <div class="form-box">
                        <h4 style="font-size: 14px; font-weight: 700; color: var(--color-dark); margin-bottom: 10px; text-transform: uppercase;">
                            Option B: Direct Technician Assignment
                        </h4>
                        <div style="font-size: 12.5px; color: var(--text-secondary); margin-bottom: 14px; line-height: 1.5;">
                            Directly designate a specific available field technician to any active queued outage.
                        </div>

                        <form id="gov-direct-assign-form">
                            <div class="form-group">
                                <label for="gov-assign-outage-select" class="form-label">Select Active Outage</label>
                                <select id="gov-assign-outage-select" class="form-select" ${queuedList.length === 0 ? "disabled" : ""}>
                                    ${queuedList.length > 0 ? queuedList.map(o => `
                                        <option value="${escapeHtml(String(o.outageId || o.id))}">
                                            OUT-${escapeHtml(String(o.outageId || o.id))} [${escapeHtml(o.priority)}] - ${escapeHtml(o.component || o.node)} (${o.consumers} consumers)
                                        </option>
                                    `).join("") : '<option value="">No queued outages waiting</option>'}
                                </select>
                            </div>

                            <div class="form-group">
                                <label for="gov-assign-tech-select" class="form-label">Select Available Field Technician</label>
                                <select id="gov-assign-tech-select" class="form-select" ${availableTechs.length === 0 ? "disabled" : ""}>
                                    ${availableTechs.length > 0 ? availableTechs.map(t => `
                                        <option value="${t.id}">
                                            ${formatTechId(t.id)}: ${escapeHtml(t.name)} (${escapeHtml(t.specialization || t.skills)})
                                        </option>
                                    `).join("") : '<option value="">No standby technicians available</option>'}
                                </select>
                            </div>

                            <button type="submit" id="gov-btn-direct-assign" class="btn btn-accent" ${queuedList.length === 0 || availableTechs.length === 0 ? "disabled" : ""}>
                                Assign Technician to Outage
                            </button>
                        </form>
                    </div>

                </div>

                <div id="gov-dispatch-result-box"></div>
            `;

            container.innerHTML = html;

            // Handler: Auto-dispatch
            const autoBtn = document.getElementById("gov-btn-auto-dispatch");
            if (autoBtn) {
                autoBtn.addEventListener("click", async () => {
                    const confirmed = confirm("Dispatch the next highest-priority outage to an available field crew?");
                    if (!confirmed) return;

                    const resultBox = document.getElementById("gov-dispatch-result-box");
                    autoBtn.disabled = true;
                    autoBtn.textContent = "Dispatching...";
                    resultBox.innerHTML = '<div class="loading-indicator">Assigning next queued outage in Supabase database...</div>';

                    try {
                        const result = await apiRequest("/api/outages/process", { method: "POST" });
                        if (result.success && result.outage) {
                            const out = result.outage;
                            resultBox.innerHTML = `
                                <div class="notice-box success">
                                    <strong>Outage Dispatched Successfully</strong>
                                    <div style="margin-top: 6px;">
                                        Outage ID: <strong>OUT-${escapeHtml(String(out.outageId || out.id))}</strong> |
                                        Priority: <span class="badge badge-${(out.priority || "").toLowerCase()}">${escapeHtml(out.priority)}</span> |
                                        Assigned Technician: <strong>${escapeHtml(out.technician)}</strong> |
                                        Status: <span class="badge badge-in-progress">${escapeHtml(out.status)}</span>
                                    </div>
                                </div>
                            `;
                        } else {
                            resultBox.innerHTML = `<div class="notice-box error">${escapeHtml(result.message || "Dispatch failed.")}</div>`;
                        }
                        cachedTechniciansList = [];
                        updateSystemMetrics();
                        renderGovOperation6(container);
                    } catch (err) {
                        resultBox.innerHTML = `<div class="notice-box error"><strong>Error:</strong> ${escapeHtml(err.message)}</div>`;
                    } finally {
                        autoBtn.disabled = false;
                        autoBtn.textContent = "Dispatch Highest-Priority Outage";
                    }
                });
            }

            // Handler: Direct assignment
            const directForm = document.getElementById("gov-direct-assign-form");
            if (directForm) {
                directForm.addEventListener("submit", async (e) => {
                    e.preventDefault();
                    const outSelect = document.getElementById("gov-assign-outage-select");
                    const techSelect = document.getElementById("gov-assign-tech-select");
                    const resultBox = document.getElementById("gov-dispatch-result-box");
                    const submitBtn = document.getElementById("gov-btn-direct-assign");

                    if (!outSelect || !techSelect || !outSelect.value || !techSelect.value) {
                        alert("Please select both an outage and a technician.");
                        return;
                    }

                    const outageId = outSelect.value;
                    const technicianId = techSelect.value;

                    submitBtn.disabled = true;
                    submitBtn.textContent = "Assigning...";
                    resultBox.innerHTML = '<div class="loading-indicator">Persisting technician assignment to Supabase database...</div>';

                    try {
                        const result = await apiRequest("/api/outages/assign", {
                            method: "POST",
                            body: JSON.stringify({ outageId, technicianId })
                        });

                        if (result.success && result.outage) {
                            const out = result.outage;
                            resultBox.innerHTML = `
                                <div class="notice-box success">
                                    <strong>Technician Assigned Successfully in PostgreSQL Database</strong>
                                    <div style="margin-top: 6px;">
                                        Outage OUT-${escapeHtml(String(out.outageId || out.id))} assigned to <strong>${escapeHtml(out.technician)}</strong>. Status updated to <span class="badge badge-in-progress">${escapeHtml(out.status)}</span>.
                                    </div>
                                </div>
                            `;
                        } else {
                            resultBox.innerHTML = `<div class="notice-box error">${escapeHtml(result.message || "Assignment failed.")}</div>`;
                        }
                        cachedTechniciansList = [];
                        updateSystemMetrics();
                        renderGovOperation6(container);
                    } catch (err) {
                        resultBox.innerHTML = `<div class="notice-box error"><strong>Error:</strong> ${escapeHtml(err.message)}</div>`;
                    } finally {
                        submitBtn.disabled = false;
                        submitBtn.textContent = "Assign Technician to Outage";
                    }
                });
            }

        } catch (err) {
            renderErrorNotice(container, err);
        }
    }

    // --- Government Op 07: Outage History & Audit Log ---
    async function renderGovOperation7(container) {
        try {
            const history = await apiRequest("/api/outages/history");

            if (!Array.isArray(history) || history.length === 0) {
                container.innerHTML = '<div class="notice-box empty">No historical outage audit records found in Supabase.</div>';
                return;
            }

            const totalRecords = history.length;
            const restoredRecords = history.filter(h => h.status === "RESTORED").length;

            let html = `
                <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 14px;">
                    Total Resolved Records: <strong>${totalRecords}</strong> |
                    Fully Restored to Grid: <strong style="color: var(--tone-success);">${restoredRecords}</strong> |
                    Persistent Supabase Historical Audit Log
                </div>

                <div class="table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Outage ID</th>
                                <th>Failed Component</th>
                                <th>Fault Classification</th>
                                <th>Priority</th>
                                <th>Restored Status</th>
                                <th>Restoring Technician</th>
                                <th>Consumers Restored</th>
                                <th>Affected Localities</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${history.map(h => {
                                const areas = Array.isArray(h.areas) ? h.areas.join(", ") : "None";
                                return `
                                    <tr>
                                        <td><strong>OUT-${escapeHtml(String(h.outageId || h.id))}</strong></td>
                                        <td><strong>${escapeHtml(h.component || h.node)}</strong></td>
                                        <td>${escapeHtml(h.cause || "Circuit Trip")}</td>
                                        <td>
                                            <span class="badge badge-${(h.priority || "").toLowerCase()}">
                                                ${escapeHtml(h.priority)}
                                            </span>
                                        </td>
                                        <td>
                                            <span class="badge badge-restored">
                                                ${escapeHtml(h.status)}
                                            </span>
                                        </td>
                                        <td>${escapeHtml(h.technician || "K. Selvam")}</td>
                                        <td><strong>${escapeHtml(String(h.consumers || 0))}</strong></td>
                                        <td>${escapeHtml(areas)}</td>
                                    </tr>
                                `;
                            }).join("")}
                        </tbody>
                    </table>
                </div>
            `;

            container.innerHTML = html;
        } catch (err) {
            renderErrorNotice(container, err);
        }
    }

    // =========================================================
    // TECHNICIAN DASHBOARD WORKSPACE LOGIC
    // =========================================================

    async function loadTechnicianRosterOptions(forceRefresh = false) {
        try {
            if (forceRefresh || cachedTechniciansList.length === 0) {
                const technicians = await apiRequest("/api/technicians").catch(() => []);
                if (Array.isArray(technicians)) {
                    cachedTechniciansList = technicians;
                }
            }

            const selector = document.getElementById("tech-crew-selector");
            if (!selector) return;

            const searchLower = (techFilterSearchQuery || "").toLowerCase().trim();
            const filteredTechnicians = cachedTechniciansList.filter(t => {
                // Status filter
                if (techFilterStatus === "AVAILABLE" && !t.available) return false;
                if (techFilterStatus === "BUSY" && t.available) return false;

                // Search query filter: Name, ID, Specialization/Skills, Assigned Area
                if (searchLower) {
                    const idStr = String(t.technicianId || t.id || "").toLowerCase();
                    const nameStr = String(t.name || "").toLowerCase();
                    const specStr = String(t.specialization || t.skills || "").toLowerCase();
                    const areaStr = String(t.assignedArea || "").toLowerCase();
                    const matches = idStr.includes(searchLower) ||
                                    nameStr.includes(searchLower) ||
                                    specStr.includes(searchLower) ||
                                    areaStr.includes(searchLower);
                    if (!matches) return false;
                }
                return true;
            });

            const currentVal = selector.value || selectedTechnicianId || "ALL";
            let optionsHtml = '<option value="ALL">All Field Crews (Global Restoration View)</option>';

            filteredTechnicians.forEach(t => {
                const techVal = String(t.technicianId || t.id);
                const techBadge = formatTechId(t.technicianId || t.id);
                const statusStr = t.available ? "Standby" : (t.currentWorkload > 0 ? `Active (${t.currentWorkload} tasks)` : "Active on Site");
                const isSelected = String(currentVal) === techVal || String(currentVal) === String(t.id);
                optionsHtml += `<option value="${techVal}" ${isSelected ? "selected" : ""}>
                    ${escapeHtml(techBadge)}: ${escapeHtml(t.name)} (${escapeHtml(t.specialization || t.skills)}) - [${statusStr}]
                </option>`;
            });

            if (filteredTechnicians.length === 0 && (searchLower || techFilterStatus !== "ALL")) {
                optionsHtml += '<option value="" disabled>-- No technicians match filter --</option>';
            }

            selector.innerHTML = optionsHtml;

            // Immediately update the Active Technician Profile display
            renderTechnicianProfile();
        } catch (e) {
            console.error("Failed to load technician roster options:", e);
        }
    }

    function renderTechnicianProfile() {
        const container = document.getElementById("tech-profile-container");
        if (!container) return;

        // If "ALL" is selected, render Global Workforce Overview
        if (selectedTechnicianId === "ALL" || !selectedTechnicianId) {
            const total = cachedTechniciansList.length;
            const availableCount = cachedTechniciansList.filter(t => t.available).length;
            const busyCount = total - availableCount;
            const totalWorkload = cachedTechniciansList.reduce((acc, t) => acc + (t.currentWorkload || 0), 0);

            container.innerHTML = `
                <div class="tech-profile-card">
                    <div class="tech-profile-header">
                        <div class="tech-profile-title-wrap">
                            <h3 class="tech-profile-name">Global Field Workforce Roster</h3>
                            <div class="tech-profile-badges">
                                <span class="badge badge-assigned">${total} Registered Field Technicians</span>
                                <span class="badge badge-restored">${availableCount} Standby (Ready)</span>
                                ${busyCount > 0 ? `<span class="badge badge-in-progress">${busyCount} Active on Site</span>` : ""}
                            </div>
                        </div>
                        <div style="font-size: 12px; color: var(--text-secondary);">
                            Technician Overview &bull; Select an individual technician to inspect personal credentials and work orders
                        </div>
                    </div>
                    <div class="tech-profile-grid">
                        <div class="tech-profile-field">
                            <span class="tech-profile-field-label">Active Field Workforce</span>
                            <span class="tech-profile-field-val">${total} Certified Engineers &amp; Linemen</span>
                        </div>
                        <div class="tech-profile-field">
                            <span class="tech-profile-field-label">Workforce Availability</span>
                            <span class="tech-profile-field-val">${availableCount} Ready &bull; ${busyCount} Dispatched</span>
                        </div>
                        <div class="tech-profile-field">
                            <span class="tech-profile-field-label">Dispatched Work Orders</span>
                            <span class="tech-profile-field-val">${totalWorkload} Outages currently under active repair</span>
                        </div>
                        <div class="tech-profile-field">
                            <span class="tech-profile-field-label">Primary Specializations</span>
                            <span class="tech-profile-field-val">Electrical Lines, Transformers, Substation Distribution</span>
                        </div>
                    </div>
                </div>
            `;
            return;
        }

        // Find the selected technician
        const cleanId = String(selectedTechnicianId).replace(/\D/g, "");
        const tech = cachedTechniciansList.find(t =>
            String(t.technicianId) === cleanId ||
            String(t.id) === String(selectedTechnicianId) ||
            String(t.id).replace(/\D/g, "") === cleanId
        );

        if (!tech) {
            container.innerHTML = `
                <div class="notice-box empty">
                    Selected technician record not found in active roster. Please choose another technician.
                </div>
            `;
            return;
        }

        const techDisplayId = formatTechId(tech.technicianId || tech.id);
        const isAvail = tech.available;
        const workload = tech.currentWorkload || 0;
        const phone = tech.phone || ("+91 94440 " + (21000 + parseInt(cleanId || "0", 10)));
        const email = tech.email || (tech.name ? tech.name.toLowerCase().replace(/[^a-z0-9]/g, "") + "@powertrack.tneb.gov.in" : "technician@powertrack.tneb.gov.in");
        const area = tech.assignedArea || "Chennai Central Grid Division";
        const skills = tech.skills || tech.specialization || "Power Grid & Electrical Restoration";

        let workloadDesc = "0 Active Outages (Standby / Ready for Dispatch)";
        if (workload > 0) {
            if (Array.isArray(tech.activeOutages) && tech.activeOutages.length > 0) {
                const outageTags = tech.activeOutages.map(o => `OUT-${o.outageId} (${escapeHtml(o.component)})`).join(", ");
                workloadDesc = `${workload} Active Outage (${outageTags})`;
            } else {
                workloadDesc = `${workload} Active Outage Assignment(s)`;
            }
        }

        container.innerHTML = `
            <div class="tech-profile-card ${!isAvail ? "active-on-site" : ""}">
                <div class="tech-profile-header">
                    <div class="tech-profile-title-wrap">
                        <h3 class="tech-profile-name">${escapeHtml(tech.name)}</h3>
                        <div class="tech-profile-badges">
                            <span class="badge badge-assigned">${escapeHtml(techDisplayId)}</span>
                            <span class="badge ${isAvail ? "badge-restored" : "badge-in-progress"}">
                                ${isAvail ? "&check; AVAILABLE / STANDBY" : "&#9881; ACTIVE ON SITE"}
                            </span>
                            <span class="badge badge-low">${escapeHtml(skills)}</span>
                        </div>
                    </div>
                    <div style="font-size: 12px; color: var(--text-secondary);">
                        Technician Profile &bull; Field Operations Roster
                    </div>
                </div>
                <div class="tech-profile-grid">
                    <div class="tech-profile-field">
                        <span class="tech-profile-field-label">Technician ID</span>
                        <span class="tech-profile-field-val">${escapeHtml(techDisplayId)} <span style="font-weight: 400; font-size: 11.5px; color: var(--text-secondary);">(DB Record #${tech.technicianId || tech.id})</span></span>
                    </div>
                    <div class="tech-profile-field">
                        <span class="tech-profile-field-label">Personnel Name</span>
                        <span class="tech-profile-field-val">${escapeHtml(tech.name)}</span>
                    </div>
                    <div class="tech-profile-field">
                        <span class="tech-profile-field-label">Contact Phone</span>
                        <span class="tech-profile-field-val">${escapeHtml(phone)}</span>
                    </div>
                    <div class="tech-profile-field">
                        <span class="tech-profile-field-label">Email Address</span>
                        <span class="tech-profile-field-val">${escapeHtml(email)}</span>
                    </div>
                    <div class="tech-profile-field">
                        <span class="tech-profile-field-label">Availability Status</span>
                        <span class="tech-profile-field-val" style="color: ${isAvail ? "var(--tone-success)" : "var(--color-action)"}; font-weight: 700;">
                            ${isAvail ? "Available / Standby (Immediate Dispatch)" : "Active on Site / Dispatched"}
                        </span>
                    </div>
                    <div class="tech-profile-field">
                        <span class="tech-profile-field-label">Assigned Grid Locality / Area</span>
                        <span class="tech-profile-field-val">${escapeHtml(area)}</span>
                    </div>
                    <div class="tech-profile-field">
                        <span class="tech-profile-field-label">Skills &amp; Qualifications</span>
                        <span class="tech-profile-field-val">${escapeHtml(skills)}</span>
                    </div>
                    <div class="tech-profile-field">
                        <span class="tech-profile-field-label">Current Active Workload</span>
                        <span class="tech-profile-field-val" style="font-weight: 700;">${workloadDesc}</span>
                    </div>
                </div>
            </div>
        `;
    }

    async function loadTechnicianContent() {
        const body = document.getElementById("tech-panel-content-body");
        if (!body) return;

        body.innerHTML = '<div class="loading-indicator">Loading technician restoration tasks from Supabase...</div>';

        if (currentTechTab === "active") {
            renderTechnicianActiveTasks(body);
        } else {
            renderTechnicianCompletedTasks(body);
        }
    }

    // --- Technician Tab 1: Assigned & Active Outages ---
    async function renderTechnicianActiveTasks(container) {
        try {
            const activeOutages = await apiRequest("/api/outages/active");

            if (!Array.isArray(activeOutages) || activeOutages.length === 0) {
                container.innerHTML = `
                    <div class="notice-box empty">
                        <strong>No active restoration tasks.</strong> All circuits in your area are operational. Standby for dispatch.
                    </div>
                `;
                return;
            }

            // Filter by selected technician if not ALL
            let filtered = activeOutages;
            if (selectedTechnicianId !== "ALL" && selectedTechnicianId) {
                const cleanId = parseInt(String(selectedTechnicianId).replace(/\D/g, ""), 10);
                const selectedTech = cachedTechniciansList.find(t =>
                    (cleanId && t.technicianId === cleanId) ||
                    String(t.id) === String(selectedTechnicianId) ||
                    String(t.id).replace(/\D/g, "") === String(cleanId)
                );
                const techNameLower = selectedTech && selectedTech.name ? selectedTech.name.toLowerCase().trim() : "";

                filtered = activeOutages.filter(o => {
                    const outTechId = o.assignedTechnicianId != null ? parseInt(String(o.assignedTechnicianId).replace(/\D/g, ""), 10) : null;
                    if (cleanId && outTechId === cleanId) return true;
                    if (techNameLower && o.technician && o.technician.toLowerCase().trim() === techNameLower) return true;
                    if (o.technician && o.technician.toLowerCase().includes(String(selectedTechnicianId).toLowerCase())) return true;
                    return false;
                });
            }

            if (filtered.length === 0) {
                container.innerHTML = `
                    <div class="notice-box empty">
                        No active outages currently assigned to selected technician.
                        <div style="margin-top: 6px; font-size: 12px;">Switch filter to <strong>"All Field Crews"</strong> to review other pending restoration tasks.</div>
                    </div>
                `;
                return;
            }

            let html = `
                <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 16px;">
                    Displaying <strong>${filtered.length}</strong> active restoration tasks &bull;
                    Status options: <strong>Assigned &rarr; In Progress &rarr; Restored</strong>
                </div>

                <div class="tech-tasks-grid">
                    ${filtered.map(o => {
                        const outId = String(o.outageId || o.id).replace("OUT-", "");
                        const isHigh = (o.priority || "").toUpperCase() === "HIGH";
                        const isInProgress = o.status === "IN PROGRESS";
                        const isAssigned = o.status === "ASSIGNED" || (o.technician && o.technician !== "NOT ASSIGNED" && !isInProgress);
                        const areas = Array.isArray(o.areas) ? o.areas.join(", ") : "None";
                        const critFacilities = Array.isArray(o.criticalFacilities) && o.criticalFacilities.length > 0
                            ? o.criticalFacilities.join("; ")
                            : "None reported";

                        return `
                            <div class="tech-task-card ${isHigh ? "high-priority" : ""}" id="tech-card-${outId}">
                                <div class="tech-task-header">
                                    <div class="tech-task-id">
                                        OUT-${escapeHtml(outId)}: <span style="font-weight: 600;">${escapeHtml(o.component || o.node)}</span>
                                    </div>
                                    <div class="tech-task-badges">
                                        <span class="badge badge-${(o.priority || "").toLowerCase()}">${escapeHtml(o.priority)}</span>
                                        <span class="badge ${isInProgress ? "badge-in-progress" : (isAssigned ? "badge-assigned" : "badge-outage")}">
                                            ${escapeHtml(o.status)}
                                        </span>
                                    </div>
                                </div>

                                ${isHigh ? `
                                    <div style="margin-bottom: 12px; padding: 6px 10px; background-color: var(--tone-high-bg); border-left: 3px solid var(--tone-high); color: var(--tone-high); font-size: 11.5px; font-weight: 700;">
                                        ESSENTIAL HEALTHCARE / EMERGENCY LOAD: Immediate restoration required for critical services.
                                    </div>
                                ` : ""}

                                <div class="tech-task-details">
                                    <div class="tech-detail-item">
                                        <span class="tech-detail-label">Location / Feeder Line</span>
                                        <span class="tech-detail-val">${escapeHtml(o.component || o.node)}</span>
                                    </div>
                                    <div class="tech-detail-item">
                                        <span class="tech-detail-label">Fault Classification</span>
                                        <span class="tech-detail-val">${escapeHtml(o.cause || "Circuit Breaker Trip")}</span>
                                    </div>
                                    <div class="tech-detail-item">
                                        <span class="tech-detail-label">Assigned Field Crew</span>
                                        <span class="tech-detail-val" style="color: var(--color-dark); font-weight: 700;">${escapeHtml(o.technician || "NOT ASSIGNED")}</span>
                                    </div>
                                    <div class="tech-detail-item">
                                        <span class="tech-detail-label">Downstream Localities</span>
                                        <span class="tech-detail-val">${escapeHtml(areas)}</span>
                                    </div>
                                    <div class="tech-detail-item">
                                        <span class="tech-detail-label">Consumers De-Energized</span>
                                        <span class="tech-detail-val">${escapeHtml(String(o.consumers || 0))} Accounts</span>
                                    </div>
                                    <div class="tech-detail-item">
                                        <span class="tech-detail-label">Critical Facilities</span>
                                        <span class="tech-detail-val">${escapeHtml(critFacilities)}</span>
                                    </div>
                                </div>

                                <!-- Field Restoration Controls -->
                                <div class="tech-controls-box">
                                    <div class="tech-controls-title">Field Restoration Controls</div>
                                    
                                    ${!isInProgress ? `
                                        <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 10px;">
                                            Current Work Status: <strong>${escapeHtml(o.status)}</strong>. Acknowledge assignment and mark as In Progress once on site.
                                        </div>
                                        <div class="tech-actions-row">
                                            <button type="button" class="btn btn-accent btn-tech-start-progress" data-outage="${outId}">
                                                Start Repair Work &rarr; Set to "In Progress"
                                            </button>
                                        </div>
                                    ` : `
                                        <div style="font-size: 12px; color: var(--color-dark); margin-bottom: 10px; font-weight: 600;">
                                            Repair Work In Progress on Site. Enter restoration notes and mark as restored upon completing physical repairs:
                                        </div>
                                        <textarea id="tech-notes-${outId}" class="tech-notes-input" rows="2" placeholder="Restoration Notes (e.g. Cleared feeder obstruction, replaced 11kV fuse, verified line voltage, closed breaker)"></textarea>
                                        <div class="tech-actions-row">
                                            <button type="button" class="btn btn-restore-success btn-tech-mark-restored" data-outage="${outId}">
                                                &check; Mark as Restored &amp; Re-Energize Power
                                            </button>
                                        </div>
                                    `}
                                </div>
                            </div>
                        `;
                    }).join("")}
                </div>
            `;

            container.innerHTML = html;

            // Wire "Set to In Progress" buttons
            container.querySelectorAll(".btn-tech-start-progress").forEach(btn => {
                btn.addEventListener("click", async () => {
                    const outId = btn.getAttribute("data-outage");
                    btn.disabled = true;
                    btn.textContent = "Updating...";

                    try {
                        const result = await apiRequest("/api/outages/update-status", {
                            method: "POST",
                            body: JSON.stringify({
                                outageId: outId,
                                status: "IN PROGRESS",
                                technicianId: selectedTechnicianId !== "ALL" ? selectedTechnicianId : null
                            })
                        });

                        if (result.success) {
                            alert(`Outage OUT-${outId} marked as IN PROGRESS. On-site repair active.`);
                            updateSystemMetrics();
                            loadTechnicianRosterOptions(true);
                            loadTechnicianContent();
                        } else {
                            alert(`Error: ${result.message}`);
                        }
                    } catch (err) {
                        alert(`Update failed: ${err.message}`);
                    } finally {
                        btn.disabled = false;
                    }
                });
            });

            // Wire "Mark as Restored" buttons
            container.querySelectorAll(".btn-tech-mark-restored").forEach(btn => {
                btn.addEventListener("click", async () => {
                    const outId = btn.getAttribute("data-outage");
                    const notesElem = document.getElementById(`tech-notes-${outId}`);
                    const details = notesElem ? notesElem.value.trim() : "";

                    const confirmed = confirm(`Confirm power restoration and re-energization for Outage OUT-${outId}? This will normalize power to all connected consumers.`);
                    if (!confirmed) return;

                    btn.disabled = true;
                    btn.textContent = "Restoring Grid Power...";

                    try {
                        const result = await apiRequest("/api/outages/restore", {
                            method: "POST",
                            body: JSON.stringify({
                                outageId: outId,
                                restorationDetails: details || `Technician physical repair completed on component. Normal power restored.`
                            })
                        });

                        if (result.success) {
                            alert(`SUCCESS! Outage OUT-${outId} marked as RESTORED. Grid component reactivated and consumer power normalized.`);
                            updateSystemMetrics();
                            loadTechnicianRosterOptions(true);
                            loadTechnicianContent();
                        } else {
                            alert(`Restoration failed: ${result.message}`);
                        }
                    } catch (err) {
                        alert(`Restoration error: ${err.message}`);
                    } finally {
                        btn.disabled = false;
                        btn.textContent = "Mark as Restored & Re-Energize Power";
                    }
                });
            });

        } catch (err) {
            renderErrorNotice(container, err);
        }
    }

    // --- Technician Tab 2: Completed Restoration History ---
    async function renderTechnicianCompletedTasks(container) {
        try {
            const history = await apiRequest("/api/outages/history");

            if (!Array.isArray(history) || history.length === 0) {
                container.innerHTML = `
                    <div class="notice-box empty">
                        No completed restoration tasks recorded yet.
                    </div>
                `;
                return;
            }

            let filtered = history.filter(h => h.status === "RESTORED");
            if (selectedTechnicianId !== "ALL" && selectedTechnicianId) {
                const cleanId = parseInt(String(selectedTechnicianId).replace(/\D/g, ""), 10);
                const selectedTech = cachedTechniciansList.find(t =>
                    (cleanId && t.technicianId === cleanId) ||
                    String(t.id) === String(selectedTechnicianId) ||
                    String(t.id).replace(/\D/g, "") === String(cleanId)
                );
                const techNameLower = selectedTech && selectedTech.name ? selectedTech.name.toLowerCase().trim() : "";

                filtered = filtered.filter(h => {
                    const outTechId = h.assignedTechnicianId != null ? parseInt(String(h.assignedTechnicianId).replace(/\D/g, ""), 10) : null;
                    if (cleanId && outTechId === cleanId) return true;
                    if (techNameLower && h.technician && h.technician.toLowerCase().trim() === techNameLower) return true;
                    if (h.technician && h.technician.toLowerCase().includes(String(selectedTechnicianId).toLowerCase())) return true;
                    return false;
                });
            }

            if (filtered.length === 0) {
                container.innerHTML = `
                    <div class="notice-box empty">
                        No completed restoration tasks recorded for selected technician.
                    </div>
                `;
                return;
            }

            let html = `
                <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 14px;">
                    Completed Restoration Work Orders: <strong>${filtered.length}</strong>
                </div>

                <div class="table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Outage ID</th>
                                <th>Re-Energized Component</th>
                                <th>Severity / Priority</th>
                                <th>Restored Status</th>
                                <th>Completing Crew</th>
                                <th>Consumers Powered</th>
                                <th>Restored Timestamp</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${filtered.map(h => {
                                const restoredTime = h.restoredAt || "Normalized";
                                return `
                                    <tr>
                                        <td><strong>OUT-${escapeHtml(String(h.outageId || h.id))}</strong></td>
                                        <td><strong>${escapeHtml(h.component || h.node)}</strong></td>
                                        <td>
                                            <span class="badge badge-${(h.priority || "").toLowerCase()}">
                                                ${escapeHtml(h.priority)}
                                            </span>
                                        </td>
                                        <td>
                                            <span class="badge badge-restored">
                                                ${escapeHtml(h.status)}
                                            </span>
                                        </td>
                                        <td>${escapeHtml(h.technician || "K. Selvam")}</td>
                                        <td><strong>${escapeHtml(String(h.consumers || 0))} Powered</strong></td>
                                        <td><span style="font-size: 11px; color: var(--text-secondary);">${escapeHtml(restoredTime)}</span></td>
                                    </tr>
                                `;
                            }).join("")}
                        </tbody>
                    </table>
                </div>
            `;

            container.innerHTML = html;
        } catch (err) {
            renderErrorNotice(container, err);
        }
    }

    // =========================================================
    // ERROR DISPLAY HELPER
    // =========================================================

    function renderErrorNotice(container, error) {
        container.innerHTML = `
            <div class="notice-box error">
                <strong>Error connecting to backend:</strong> ${escapeHtml(error.message || String(error))}
                <div style="margin-top: 8px; font-size: 12px; color: var(--text-secondary);">
                    Ensure the Java HTTP server is compiled and running in the backend directory:<br>
                    <code>cd backend</code><br>
                    <code>java -cp ".;lib/postgresql-42.7.3.jar" PowerTrackServer</code>
                </div>
            </div>
        `;
    }

    function escapeHtml(str) {
        if (str === null || str === undefined) return "";
        return String(str)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    // =========================================================
    // POLICY MODALS
    // =========================================================

    function initPolicyModals() {
        const modal = document.getElementById("policy-modal");
        const title = document.getElementById("modal-title");
        const body = document.getElementById("modal-body");
        const closeX = document.getElementById("btn-modal-close-x");
        const closeBtn = document.getElementById("btn-modal-close");
        const linkPrivacy = document.getElementById("link-privacy");
        const linkTerms = document.getElementById("link-terms");

        function openModal(modalTitle, modalContent) {
            if (!modal) return;
            if (title) title.textContent = modalTitle;
            if (body) body.innerHTML = modalContent;
            modal.classList.add("open");
        }

        function closeModal() {
            if (modal) modal.classList.remove("open");
        }

        if (closeX) closeX.addEventListener("click", closeModal);
        if (closeBtn) closeBtn.addEventListener("click", closeModal);
        if (modal) {
            modal.addEventListener("click", (e) => {
                if (e.target === modal) closeModal();
            });
        }

        if (linkPrivacy) {
            linkPrivacy.addEventListener("click", () => {
                openModal(
                    "Privacy Policy - PowerTrack",
                    "<p>PowerTrack operates as an educational and operational electricity distribution system. No personal consumer payment information is stored. Grid accounts are simulated and mapped to PostgreSQL for technical research.</p>"
                );
            });
        }

        if (linkTerms) {
            linkTerms.addEventListener("click", () => {
                openModal(
                    "Terms of Service - PowerTrack",
                    "<p>PowerTrack provides regional grid telemetry, priority queue restoration dispatch, and workforce assignment for academic and research evaluation under the Tamil Nadu distribution grid framework.</p>"
                );
            });
        }
    }

    // =========================================================
    // INITIALIZATION & EVENT LISTENERS
    // =========================================================

    function initEventListeners() {
        // 1. Role Selection Landing Page Card Clicks
        const govCard = document.getElementById("card-role-government");
        const techCard = document.getElementById("card-role-technician");

        if (govCard) {
            govCard.addEventListener("click", () => switchView("government"));
            govCard.addEventListener("keydown", (e) => {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    switchView("government");
                }
            });
        }

        if (techCard) {
            techCard.addEventListener("click", () => switchView("technician"));
            techCard.addEventListener("keydown", (e) => {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    switchView("technician");
                }
            });
        }

        // 2. Back to Role Selection Buttons
        const backGov = document.getElementById("btn-back-role-gov");
        const backTech = document.getElementById("btn-back-role-tech");

        if (backGov) backGov.addEventListener("click", () => switchView("role-selection"));
        if (backTech) backTech.addEventListener("click", () => switchView("role-selection"));

        // 3. Government Operation Cards Click Listeners
        for (let i = 1; i <= 7; i++) {
            const card = document.getElementById(`card-gov-op-${i}`);
            if (card) {
                card.addEventListener("click", () => selectGovOperation(i));
            }
        }

        const govRefreshBtn = document.getElementById("gov-btn-refresh-operation");
        if (govRefreshBtn) {
            govRefreshBtn.addEventListener("click", () => {
                selectGovOperation(currentGovOperation);
                updateSystemMetrics();
            });
        }

        // 4. Technician Dashboard Controls & Profile Filter
        const techSelector = document.getElementById("tech-crew-selector");
        if (techSelector) {
            techSelector.addEventListener("change", (e) => {
                selectedTechnicianId = e.target.value;
                renderTechnicianProfile();
                loadTechnicianContent();
            });
        }

        const techSearchInput = document.getElementById("tech-filter-search");
        if (techSearchInput) {
            techSearchInput.addEventListener("input", (e) => {
                techFilterSearchQuery = e.target.value;
                loadTechnicianRosterOptions(false);
            });
        }

        const techStatusSelect = document.getElementById("tech-filter-status");
        if (techStatusSelect) {
            techStatusSelect.addEventListener("change", (e) => {
                techFilterStatus = e.target.value;
                loadTechnicianRosterOptions(false);
            });
        }

        const techResetBtn = document.getElementById("btn-tech-reset-filter");
        if (techResetBtn) {
            techResetBtn.addEventListener("click", () => {
                techFilterSearchQuery = "";
                techFilterStatus = "ALL";
                selectedTechnicianId = "ALL";
                if (techSearchInput) techSearchInput.value = "";
                if (techStatusSelect) techStatusSelect.value = "ALL";
                loadTechnicianRosterOptions(false);
                loadTechnicianContent();
            });
        }

        const techRefreshBtn = document.getElementById("btn-tech-refresh");
        if (techRefreshBtn) {
            techRefreshBtn.addEventListener("click", () => {
                loadTechnicianRosterOptions(true);
                loadTechnicianContent();
                updateSystemMetrics();
            });
        }

        const techTabActive = document.getElementById("tab-tech-active");
        const techTabCompleted = document.getElementById("tab-tech-completed");

        if (techTabActive && techTabCompleted) {
            techTabActive.addEventListener("click", () => {
                currentTechTab = "active";
                techTabActive.classList.add("active");
                techTabActive.setAttribute("aria-selected", "true");
                techTabCompleted.classList.remove("active");
                techTabCompleted.setAttribute("aria-selected", "false");
                loadTechnicianContent();
            });

            techTabCompleted.addEventListener("click", () => {
                currentTechTab = "completed";
                techTabCompleted.classList.add("active");
                techTabCompleted.setAttribute("aria-selected", "true");
                techTabActive.classList.remove("active");
                techTabActive.setAttribute("aria-selected", "false");
                loadTechnicianContent();
            });
        }

        // 5. Hash Routing Listener
        window.addEventListener("hashchange", handleHashRouting);

        // 6. Policy Modals
        initPolicyModals();
    }

    // Document Ready
    document.addEventListener("DOMContentLoaded", () => {
        initEventListeners();
        handleHashRouting();
        checkBackendHealth();
    });

})();
