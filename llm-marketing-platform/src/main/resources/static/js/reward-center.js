const API_BASE = "http://localhost:8091/api";

const dom = {
    tabActivities: document.getElementById("tabActivities"),
    tabRewards: document.getElementById("tabRewards"),
    activitiesPanel: document.getElementById("activitiesPanel"),
    rewardsPanel: document.getElementById("rewardsPanel"),
    reconcilePanel: document.getElementById("reconcilePanel"),
    reconcileOutput: document.getElementById("reconcileOutput"),
    activityBody: document.getElementById("activityBody"),
    btnReloadActivities: document.getElementById("btnReloadActivities"),
    activitySelect: document.getElementById("activitySelect"),
    btnReload: document.getElementById("btnReload"),
    recordBody: document.getElementById("recordBody"),
    statTotal: document.getElementById("statTotal"),
    statPending: document.getElementById("statPending"),
    statDone: document.getElementById("statDone"),
    statFailed: document.getElementById("statFailed"),
    grantPoints: document.getElementById("grantPoints"),
    grantMode: document.getElementById("grantMode"),
    grantCUserId: document.getElementById("grantCUserId"),
    cUserField: document.getElementById("cUserField"),
    includeFutureUsers: document.getElementById("includeFutureUsers"),
    futureWrap: document.getElementById("futureWrap"),
    btnGrant: document.getElementById("btnGrant"),
    toast: document.getElementById("toast"),
    merchantInfo: document.getElementById("merchantInfo")
};

const query = new URLSearchParams(window.location.search);
const presetActivityId = query.get("activityId");
const presetView = (query.get("view") || "").toLowerCase();

let activityList = [];
let currentActivityId = null;
let activeTab = "activities";
let presetHintChecked = false;

function getToken() {
    return localStorage.getItem("chat_token") || "";
}

function showToast(message, ok = true) {
    if (!dom.toast) return;
    dom.toast.className = `toast show ${ok ? "ok" : "err"}`;
    dom.toast.innerText = message;
    setTimeout(() => {
        dom.toast.className = "toast";
    }, 2200);
}

function parseJwtPayload(tokenValue) {
    try {
        if (!tokenValue || tokenValue.split(".").length < 2) return null;
        const base64Url = tokenValue.split(".")[1];
        const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
        const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
        const binary = atob(padded);
        try {
            const utf8 = decodeURIComponent(binary.split("").map((c) => {
                const code = c.charCodeAt(0).toString(16).padStart(2, "0");
                return `%${code}`;
            }).join(""));
            return JSON.parse(utf8);
        } catch (_) {
            return JSON.parse(binary);
        }
    } catch (_) {
        return null;
    }
}

function renderCurrentMerchant() {
    if (!dom.merchantInfo) return;
    const payload = parseJwtPayload(getToken());
    const username = payload?.username || "-";
    const userId = payload?.userId || "-";
    dom.merchantInfo.innerText = `Current merchant: ${username} (userId=${userId})`;
}

async function authFetch(url, options = {}) {
    const token = getToken();
    const headers = Object.assign({}, options.headers || {}, {
        Authorization: `Bearer ${token}`
    });

    const res = await fetch(url, Object.assign({}, options, { headers }));
    if (res.status === 401) {
        showToast("Login expired. Redirecting...", false);
        setTimeout(() => {
            window.location.href = "/index.html";
        }, 600);
        throw new Error("Unauthorized");
    }

    try {
        return await res.json();
    } catch (_) {
        throw new Error(`Request failed: ${res.status}`);
    }
}

function statusTag(status) {
    if (Number(status) === 1) return '<span class="tag online">online</span>';
    return '<span class="tag offline">draft/stopped</span>';
}

function stateLabel(state) {
    if (state === 1) return { text: "done", className: "done" };
    if (state === 2) return { text: "failed", className: "failed" };
    return { text: "pending", className: "pending" };
}

function formatTime(v) {
    if (!v) return "-";
    return String(v).replace("T", " ");
}

function switchTab(tabName) {
    activeTab = tabName === "rewards" ? "rewards" : "activities";
    const isActivities = activeTab === "activities";

    dom.tabActivities.classList.toggle("active", isActivities);
    dom.tabRewards.classList.toggle("active", !isActivities);

    dom.activitiesPanel.style.display = isActivities ? "block" : "none";
    dom.reconcilePanel.style.display = isActivities ? "block" : "none";
    dom.rewardsPanel.style.display = isActivities ? "none" : "block";

    if (!isActivities && currentActivityId) {
        dom.activitySelect.value = String(currentActivityId);
        loadRecords();
    }
}

function updateStats(list) {
    const total = list.length;
    const pending = list.filter((x) => (x.awardState ?? 0) === 0).length;
    const done = list.filter((x) => x.awardState === 1).length;
    const failed = list.filter((x) => x.awardState === 2).length;

    dom.statTotal.innerText = String(total);
    dom.statPending.innerText = String(pending);
    dom.statDone.innerText = String(done);
    dom.statFailed.innerText = String(failed);
}

function renderRecords(list) {
    updateStats(list);
    if (!list.length) {
        dom.recordBody.innerHTML = '<tr><td colspan="6" class="empty">No records</td></tr>';
        return;
    }

    dom.recordBody.innerHTML = list.map((item) => {
        const state = stateLabel(item.awardState ?? 0);
        const rewardId = item.rewardId;
        const isDone = item.awardState === 1;
        const isFailed = item.awardState === 2;
        const nickname = item.nickname || "-";
        const username = item.username || "-";
        const mobile = item.mobile || "-";
        const cUserId = item.cUserId || "-";

        return `
            <tr>
                <td>${rewardId || "-"}</td>
                <td>
                    <div>${nickname}</div>
                    <div style="font-size:12px;color:#64748b">${username} | ${mobile}</div>
                    <div style="font-size:12px;color:#94a3b8">${cUserId}</div>
                </td>
                <td>${item.prizeName || "NO_AWARD"}</td>
                <td><span class="tag ${state.className}">${state.text}</span></td>
                <td>${formatTime(item.createTime)}</td>
                <td>
                    <div class="op-wrap">
                        <button class="mini-btn success" data-id="${rewardId}" data-state="1" ${isDone ? "disabled" : ""}>mark done</button>
                        <button class="mini-btn fail" data-id="${rewardId}" data-state="2" ${isFailed ? "disabled" : ""}>mark failed</button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

function renderActivitySelect() {
    if (!activityList.length) {
        dom.activitySelect.innerHTML = "<option value=''>No activities</option>";
        return;
    }

    dom.activitySelect.innerHTML = activityList.map((a) => {
        const label = `${a.activityName || ("activity#" + a.activityId)} ${Number(a.status) === 1 ? "(online)" : "(draft/stopped)"}`;
        return `<option value="${a.activityId}">${label}</option>`;
    }).join("");
}

function renderActivitiesTable() {
    if (!activityList.length) {
        dom.activityBody.innerHTML = '<tr><td colspan="6" class="empty">No activities</td></tr>';
        return;
    }

    dom.activityBody.innerHTML = activityList.map((a) => {
        const activityId = a.activityId;
        const stockText = `${Number(a.surplusStock || 0)} / ${Number(a.totalStock || 0)}`;
        const isOnline = Number(a.status) === 1;

        return `
            <tr>
                <td>
                    <div style="font-weight:700">${a.activityName || ("activity#" + activityId)}</div>
                    <div style="font-size:12px;color:#64748b">ID: ${activityId} | ${formatTime(a.createTime)}</div>
                </td>
                <td>${statusTag(a.status)}</td>
                <td>${stockText}</td>
                <td>${Number(a.rewardCount || 0)}</td>
                <td>${Number(a.pendingRewardCount || 0)}</td>
                <td>
                    <div class="op-wrap">
                        <a class="mini-btn info" target="_blank" href="${a.playLink}">open lottery</a>
                        <button class="mini-btn manage" data-role="enter-manage" data-activity-id="${activityId}">reward manage</button>
                        <button class="mini-btn publish" data-role="status" data-action="publish" data-activity-id="${activityId}" ${isOnline ? "disabled" : ""}>publish</button>
                        <button class="mini-btn stop" data-role="status" data-action="stop" data-activity-id="${activityId}" ${isOnline ? "" : "disabled"}>stop</button>
                        <button class="mini-btn info" data-role="reconcile" data-mode="CHECK" data-activity-id="${activityId}">reconcile</button>
                        <button class="mini-btn info" data-role="reconcile" data-mode="REPAIR" data-activity-id="${activityId}">repair</button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

async function loadActivityOverviewList() {
    const data = await authFetch(`${API_BASE}/merchant/activity/overview-list`);
    if (Number(data.code) !== 200 || !Array.isArray(data.data)) {
        throw new Error(data.message || "Failed to load activities");
    }

    activityList = data.data;
    if (!activityList.length) {
        currentActivityId = null;
        renderActivitySelect();
        renderActivitiesTable();
        renderRecords([]);
        await notifyPresetActivityHint();
        return;
    }

    const currentExists = currentActivityId && activityList.some((x) => String(x.activityId) === String(currentActivityId));
    const presetExists = presetActivityId && activityList.some((x) => String(x.activityId) === String(presetActivityId));

    if (presetExists) {
        currentActivityId = Number(presetActivityId);
    } else if (!currentExists) {
        currentActivityId = Number(activityList[0].activityId);
    }

    renderActivitySelect();
    dom.activitySelect.value = String(currentActivityId);
    renderActivitiesTable();
    await notifyPresetActivityHint();
}

async function notifyPresetActivityHint() {
    if (presetHintChecked || !presetActivityId) return;
    presetHintChecked = true;

    const exists = activityList.some((x) => String(x.activityId) === String(presetActivityId));
    if (exists) return;

    try {
        const data = await authFetch(`${API_BASE}/merchant/reward/records?activityId=${encodeURIComponent(presetActivityId)}`);
        if (Number(data.code) !== 200) {
            showToast(`activityId=${presetActivityId} not owned or not exists`, false);
            return;
        }
        showToast(`activityId=${presetActivityId} not in list`, false);
    } catch (e) {
        showToast(`activityId=${presetActivityId} check failed: ${e.message || "unknown"}`, false);
    }
}

async function loadRecords() {
    if (!currentActivityId) {
        renderRecords([]);
        return;
    }

    dom.btnReload.disabled = true;
    try {
        const data = await authFetch(`${API_BASE}/merchant/reward/records?activityId=${encodeURIComponent(currentActivityId)}`);
        if (Number(data.code) !== 200 || !Array.isArray(data.data)) {
            throw new Error(data.message || "Failed to load records");
        }
        renderRecords(data.data);
    } finally {
        dom.btnReload.disabled = false;
    }
}

async function updateRewardState(rewardId, awardState) {
    const data = await authFetch(`${API_BASE}/merchant/reward/state`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ rewardId: Number(rewardId), awardState: Number(awardState) })
    });
    if (Number(data.code) !== 200) {
        throw new Error(data.message || "Failed to update reward state");
    }
    showToast("Reward state updated");
}

async function grantPoints() {
    if (!currentActivityId) {
        showToast("Please select activity", false);
        return;
    }

    const points = Number(dom.grantPoints.value);
    if (!Number.isFinite(points) || points <= 0) {
        showToast("Points must be > 0", false);
        return;
    }

    const mode = dom.grantMode.value;
    const payload = {
        activityId: Number(currentActivityId),
        points,
        applyToAll: mode === "all"
    };

    if (mode === "all") {
        payload.includeFutureUsers = !!dom.includeFutureUsers.checked;
    } else {
        const userRef = (dom.grantCUserId.value || "").trim();
        if (!userRef) {
            showToast("Please input username/mobile/cUserId", false);
            return;
        }
        payload.userRef = userRef;
    }

    dom.btnGrant.disabled = true;
    try {
        const data = await authFetch(`${API_BASE}/merchant/reward/grant-points`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        if (Number(data.code) !== 200) {
            throw new Error(data.message || "Grant points failed");
        }
        showToast("Points granted");
        await loadRecords();
    } catch (e) {
        showToast(e.message || "Grant points failed", false);
    } finally {
        dom.btnGrant.disabled = false;
    }
}

async function updateActivityStatus(activityId, action) {
    const data = await authFetch(`${API_BASE}/merchant/activity/status`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ activityId: Number(activityId), action })
    });
    if (Number(data.code) !== 200) {
        throw new Error(data.message || "Update status failed");
    }
    showToast(data.data?.message || "Status updated");
}

function renderReconcileResult(result) {
    if (!result) {
        dom.reconcileOutput.innerText = "Empty result";
        return;
    }

    const lines = [];
    lines.push(`activityId: ${result.activityId}`);
    lines.push(`mode: ${result.mode}`);
    lines.push(`repaired: ${result.repaired ? "yes" : "no"}`);
    lines.push(`rateMapExists: ${result.rateMapExists ? "yes" : "no"}`);
    lines.push(`checkedPrizeCount: ${result.checkedPrizeCount}`);
    lines.push(`mismatchCount: ${result.mismatchCount}`);
    lines.push(`message: ${result.message || "-"}`);

    if (Array.isArray(result.mismatches) && result.mismatches.length) {
        lines.push("");
        lines.push("mismatches:");
        result.mismatches.forEach((m, idx) => {
            lines.push(`${idx + 1}. [${m.issueType}] prizeId=${m.prizeId ?? "-"}, prizeName=${m.prizeName ?? "-"}, mysql=${m.mysqlStock ?? "-"}, redis=${m.redisStock ?? "-"}, key=${m.redisKey ?? "-"}`);
        });
    }

    dom.reconcileOutput.innerText = lines.join("\n");
}

async function reconcileStock(activityId, mode) {
    const data = await authFetch(`${API_BASE}/merchant/activity/reconcile-stock`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ activityId: Number(activityId), mode })
    });

    if (Number(data.code) !== 200) {
        throw new Error(data.message || "Reconcile failed");
    }

    renderReconcileResult(data.data);
    if (mode === "REPAIR") {
        showToast("Repair executed");
    } else {
        showToast("Reconcile done");
    }
}

function syncGrantModeUI() {
    const isAll = dom.grantMode.value === "all";
    dom.cUserField.style.display = isAll ? "none" : "flex";
    dom.futureWrap.style.display = isAll ? "inline-flex" : "none";
}

function bindEvents() {
    dom.tabActivities.addEventListener("click", () => switchTab("activities"));
    dom.tabRewards.addEventListener("click", () => switchTab("rewards"));

    dom.btnReloadActivities.addEventListener("click", async () => {
        dom.btnReloadActivities.disabled = true;
        try {
            await loadActivityOverviewList();
            if (activeTab === "rewards") {
                await loadRecords();
            }
        } finally {
            dom.btnReloadActivities.disabled = false;
        }
    });

    dom.activitySelect.addEventListener("change", async () => {
        currentActivityId = Number(dom.activitySelect.value);
        await loadRecords();
    });

    dom.btnReload.addEventListener("click", loadRecords);
    dom.btnGrant.addEventListener("click", grantPoints);
    dom.grantMode.addEventListener("change", syncGrantModeUI);

    dom.recordBody.addEventListener("click", async (e) => {
        const btn = e.target.closest("button[data-id][data-state]");
        if (!btn) return;

        const rewardId = btn.getAttribute("data-id");
        const awardState = btn.getAttribute("data-state");
        if (!rewardId || !awardState) return;

        btn.disabled = true;
        try {
            await updateRewardState(rewardId, awardState);
            await loadRecords();
            await loadActivityOverviewList();
        } catch (err) {
            showToast(err.message || "Update reward state failed", false);
            btn.disabled = false;
        }
    });

    dom.activityBody.addEventListener("click", async (e) => {
        const btn = e.target.closest("button[data-role]");
        if (!btn) return;

        const role = btn.getAttribute("data-role");
        const activityId = Number(btn.getAttribute("data-activity-id"));
        if (!activityId) return;

        try {
            if (role === "enter-manage") {
                currentActivityId = activityId;
                dom.activitySelect.value = String(activityId);
                switchTab("rewards");
                await loadRecords();
                return;
            }

            if (role === "status") {
                const action = btn.getAttribute("data-action");
                if (!action) return;
                btn.disabled = true;
                await updateActivityStatus(activityId, action);
                await loadActivityOverviewList();
                if (currentActivityId === activityId && activeTab === "rewards") {
                    await loadRecords();
                }
                return;
            }

            if (role === "reconcile") {
                const mode = btn.getAttribute("data-mode") || "CHECK";
                btn.disabled = true;
                await reconcileStock(activityId, mode);
                await loadActivityOverviewList();
            }
        } catch (err) {
            showToast(err.message || "Operation failed", false);
        } finally {
            btn.disabled = false;
        }
    });
}

async function init() {
    if (!getToken()) {
        showToast("Please login first", false);
        setTimeout(() => {
            window.location.href = "/index.html";
        }, 600);
        return;
    }

    bindEvents();
    syncGrantModeUI();
    renderCurrentMerchant();

    try {
        await loadActivityOverviewList();
        if (presetActivityId && currentActivityId) {
            dom.activitySelect.value = String(currentActivityId);
        }

        const openRewardsFirst = presetView === "rewards" || !!presetActivityId;
        switchTab(openRewardsFirst ? "rewards" : "activities");
        if (openRewardsFirst) {
            await loadRecords();
        }
    } catch (e) {
        showToast(e.message || "Init failed", false);
    }
}

init();