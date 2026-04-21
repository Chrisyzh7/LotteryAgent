const seq = [0, 1, 2, 3, 4, 5, 6, 7];
const MAX_SPIN_WAIT_MS = 8000;

let prizes = [];
let currentIdx = 0;
let isDrawing = false;
let currentActivityId = null;
let currentDeductPoints = 0;
let currentCUserId = null;
let currentUsername = null;
let currentNickname = null;
let currentMobile = null;

const query = new URLSearchParams(window.location.search);
const styleFromUrl = query.get("style");
const activityIdFromUrl = query.get("activityId");

const dom = {
    title: document.getElementById("activityTitle"),
    cUserInfo: document.getElementById("cUserInfo"),
    userPoints: document.getElementById("userPoints"),
    deductPoints: document.getElementById("deductPoints"),
    drawBtn: document.getElementById("drawBtn"),
    rewardList: document.getElementById("rewardList"),
    resultModal: document.getElementById("resultModal"),
    resultText: document.getElementById("resultText"),
    resultIcon: document.getElementById("resultIcon"),
    cUserModal: document.getElementById("cUserModal"),
    cUserError: document.getElementById("cUserError"),
    cUserUsername: document.getElementById("cUserUsername"),
    cUserPassword: document.getElementById("cUserPassword"),
    cUserNickname: document.getElementById("cUserNickname"),
    cUserMobile: document.getElementById("cUserMobile"),
    cUserLoginBtn: document.getElementById("cUserLoginBtn"),
    switchUserBtn: document.getElementById("switchUserBtn"),
    logoutUserBtn: document.getElementById("logoutUserBtn"),
    pageToast: document.getElementById("pageToast")
};

let toastTimer = null;

function getUserStorageKey(activityId) {
    return `lottery_c_user_${activityId}`;
}

function parseNumber(v, fallback = 0) {
    const n = Number(v);
    return Number.isFinite(n) ? n : fallback;
}

function normalizeStyle(style) {
    if (style === "ins_minimal" || style === "fresh_light" || style === "dark_neon") {
        return style;
    }
    return "dark_neon";
}

function applyTheme(style) {
    document.body.classList.remove("theme-ins", "theme-fresh");
    if (style === "ins_minimal") {
        document.body.classList.add("theme-ins");
    } else if (style === "fresh_light") {
        document.body.classList.add("theme-fresh");
    }
}

function renderGrid(prizeList) {
    const normalized = (prizeList || []).slice(0, 8).map((p, idx) => ({
        id: p.id != null ? String(p.id) : `NO_AWARD_${idx}`,
        prizeName: (p.prizeName && String(p.prizeName).trim()) || "谢谢参与",
        prizeImage: (p.prizeImage && String(p.prizeImage).trim()) || "🎁",
        isNoAward: (p.prizeName || "").includes("谢谢参与")
    }));

    while (normalized.length < 8) {
        normalized.push({
            id: `NO_AWARD_${normalized.length}`,
            prizeName: "谢谢参与",
            prizeImage: "🥲",
            isNoAward: true
        });
    }

    prizes = normalized;
    prizes.forEach((p, idx) => {
        const cell = document.getElementById(`cell-${idx}`);
        if (!cell) return;
        cell.innerHTML = `<div class="icon">${p.prizeImage}</div><div>${p.prizeName}</div>`;
    });
}

function resetActiveCells() {
    document.querySelectorAll(".prize-cell").forEach((c) => c.classList.remove("active"));
}

function getCurrentPoints() {
    return parseNumber(dom.userPoints.innerText, 0);
}

function setCurrentPoints(points) {
    dom.userPoints.innerText = parseNumber(points, 0).toFixed(2);
}

function maskMobile(mobile) {
    const m = String(mobile || "").trim();
    if (!m) return "";
    if (m.length < 7) return m;
    return `${m.slice(0, 3)}****${m.slice(-4)}`;
}

function setUserInfoText() {
    if (!currentCUserId) {
        dom.cUserInfo.innerText = "未登录C端用户";
        return;
    }
    const shortId = currentCUserId.length > 10 ? `${currentCUserId.slice(0, 10)}...` : currentCUserId;
    const showName = currentNickname || currentUsername || "用户";
    const usernamePart = currentUsername ? ` (@${currentUsername})` : "";
    const mobilePart = currentMobile ? ` | ${maskMobile(currentMobile)}` : "";
    dom.cUserInfo.innerText = `已登录：${showName}${usernamePart}${mobilePart} (${shortId})`;
}

function openCUserModal() {
    dom.cUserModal.style.display = "flex";
    if (dom.cUserError) {
        dom.cUserError.style.display = "none";
        dom.cUserError.innerText = "";
    }
}

function closeCUserModal() {
    dom.cUserModal.style.display = "none";
}

function showCUserError(msg) {
    if (!dom.cUserError) return;
    dom.cUserError.classList.remove("success");
    dom.cUserError.innerText = msg;
    dom.cUserError.style.display = "block";
}

function showCUserSuccess(msg) {
    if (!dom.cUserError) return;
    dom.cUserError.classList.add("success");
    dom.cUserError.innerText = msg;
    dom.cUserError.style.display = "block";
}

function showPageToast(msg) {
    if (!dom.pageToast) return;
    dom.pageToast.innerText = msg;
    dom.pageToast.classList.add("show");
    if (toastTimer) {
        clearTimeout(toastTimer);
    }
    toastTimer = setTimeout(() => {
        dom.pageToast.classList.remove("show");
    }, 1800);
}

function pulseDrawBtn() {
    if (!dom.drawBtn) return;
    dom.drawBtn.classList.remove("insufficient");
    void dom.drawBtn.offsetWidth;
    dom.drawBtn.classList.add("insufficient");
    setTimeout(() => dom.drawBtn.classList.remove("insufficient"), 420);
}

function isInsufficientPointsMsg(msg) {
    const text = String(msg || "");
    return text.includes("积分不足");
}

function showInsufficientFeedback(msg) {
    const finalMsg = msg && String(msg).trim() ? String(msg) : "当前积分不足";
    showPageToast(finalMsg);
    pulseDrawBtn();
}

function clearCurrentUser(showToastText = "") {
    if (currentActivityId) {
        localStorage.removeItem(getUserStorageKey(currentActivityId));
    }
    currentCUserId = null;
    currentUsername = null;
    currentNickname = null;
    currentMobile = null;
    setCurrentPoints(0);
    setUserInfoText();
    dom.rewardList.innerText = "请先登录后查看记录";
    if (showToastText) {
        showPageToast(showToastText);
    }
}

async function fetchPoints() {
    if (!currentActivityId || !currentCUserId) return;
    const res = await fetch(`/api/c-user/points?activityId=${encodeURIComponent(currentActivityId)}&cUserId=${encodeURIComponent(currentCUserId)}`);
    const data = await res.json();
    if (Number(data.code) !== 200 || !data.data) {
        throw new Error(data.message || data.msg || "积分查询失败");
    }
    const p = data.data;
    currentNickname = p.nickname || currentNickname;
    currentUsername = p.username || currentUsername;
    currentMobile = p.mobile || currentMobile;
    setCurrentPoints(p.remainPoints);
    setUserInfoText();
}

async function cUserAuth() {
    const username = (dom.cUserUsername.value || "").trim();
    const password = (dom.cUserPassword.value || "").trim();
    const nickname = (dom.cUserNickname.value || "").trim();
    const mobile = (dom.cUserMobile.value || "").trim();

    if (!username) {
        showCUserError("请输入用户名");
        return;
    }
    if (!password) {
        showCUserError("请输入密码");
        return;
    }

    const payload = {
        activityId: currentActivityId,
        username,
        password,
        nickname,
        mobile
    };

    const existing = currentActivityId ? localStorage.getItem(getUserStorageKey(currentActivityId)) : null;
    if (existing) {
        payload.cUserId = existing;
    }

    const res = await fetch("/api/c-user/auth", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });

    const data = await res.json();
    const authData = (data && data.data && typeof data.data === "object") ? data.data : {};
    const parsedCUserId = authData.cUserId || authData.cuserId || authData.CUserId || authData.userId || authData.id;

    if (Number(data.code) !== 200 || !parsedCUserId) {
        throw new Error(data.message || data.msg || "C 端登录失败");
    }

    currentCUserId = String(parsedCUserId);
    currentUsername = authData.username || username;
    currentNickname = authData.nickname || nickname || username;
    currentMobile = authData.mobile || mobile;

    localStorage.setItem(getUserStorageKey(currentActivityId), currentCUserId);
    dom.cUserPassword.value = "";

    if (authData.remainPoints != null) {
        setCurrentPoints(authData.remainPoints);
    } else {
        await fetchPoints();
    }

    setUserInfoText();
    if (authData.loginMessage) {
        showCUserSuccess(authData.loginMessage);
        setTimeout(closeCUserModal, 520);
    } else {
        closeCUserModal();
    }
    await loadRewardList();
}

async function ensureCUserReady() {
    if (!currentActivityId) return false;
    const cached = localStorage.getItem(getUserStorageKey(currentActivityId));
    if (cached) {
        currentCUserId = cached;
        try {
            await fetchPoints();
            return true;
        } catch (e) {
            console.warn("缓存C端用户失效，将重新登录", e);
            clearCurrentUser();
        }
    }
    openCUserModal();
    return false;
}

async function loadRewardList() {
    if (!currentCUserId) {
        dom.rewardList.innerText = "请先登录后查看记录";
        return;
    }
    try {
        const res = await fetch(`/api/c-user/rewards?cUserId=${encodeURIComponent(currentCUserId)}`);
        const data = await res.json();
        if (Number(data.code) !== 200 || !Array.isArray(data.data)) {
            dom.rewardList.innerText = "加载记录失败";
            return;
        }

        const records = data.data.filter((item) => String(item.activityId) === String(currentActivityId));
        if (!records.length) {
            dom.rewardList.innerText = "暂无记录";
            return;
        }

        dom.rewardList.innerHTML = records
            .map((item) => {
                const t = item.createTime ? String(item.createTime).replace("T", " ") : "";
                return `<div class="reward-item"><strong>${item.prizeName || "谢谢参与"}</strong><div>${t}</div></div>`;
            })
            .join("");
    } catch (e) {
        console.error("加载中奖记录失败:", e);
        dom.rewardList.innerText = "加载记录失败";
    }
}

function resolveTargetIndex(resultPrizeId, resultMsg) {
    if (resultPrizeId && resultPrizeId !== "NO_AWARD" && resultPrizeId !== "ERROR") {
        const found = prizes.findIndex((p) => String(p.id) === String(resultPrizeId));
        if (found !== -1) return found;
    }

    if (resultMsg) {
        const byName = prizes.findIndex((p) => resultMsg.includes(p.prizeName));
        if (byName !== -1) return byName;
    }

    const noAwardIdx = prizes.findIndex((p) => p.isNoAward);
    return noAwardIdx !== -1 ? noAwardIdx : 7;
}

function resolveResultIcon(resultPrizeId, resultMsg) {
    if (resultPrizeId && resultPrizeId !== "NO_AWARD" && resultPrizeId !== "ERROR") {
        const p = prizes.find((x) => String(x.id) === String(resultPrizeId));
        if (p) return p.prizeImage;
    }
    if (resultMsg && resultMsg.includes("谢谢参与")) {
        return "🥲";
    }
    return "🎁";
}

function showResult(msg, icon) {
    dom.resultText.innerText = msg || "谢谢参与";
    dom.resultIcon.innerText = icon || "🎁";
    dom.resultModal.style.display = "flex";
    isDrawing = false;
}

function closeModal() {
    dom.resultModal.style.display = "none";
}

window.closeModal = closeModal;

async function loadData() {
    try {
        const infoUrl = activityIdFromUrl
            ? `/api/lottery/info?activityId=${encodeURIComponent(activityIdFromUrl)}`
            : "/api/lottery/info";

        const res = await fetch(infoUrl);
        const data = await res.json();

        if (!data.activity) {
            dom.title.innerText = "当前暂无活动";
            dom.drawBtn.disabled = true;
            dom.drawBtn.style.opacity = "0.5";
            renderGrid([]);
            return;
        }

        currentActivityId = data.activity.id;
        dom.title.innerText = data.activity.activityName || `活动 ${currentActivityId}`;
        currentDeductPoints = parseNumber(data.activity.deductPoints, 0);
        dom.deductPoints.innerText = `-${currentDeductPoints} 积分`;

        const style = normalizeStyle(styleFromUrl || data.activity.pageStyle);
        applyTheme(style);
        renderGrid(data.prizes || []);

        setCurrentPoints(0);
        setUserInfoText();

        await ensureCUserReady();
        if (currentCUserId) {
            await loadRewardList();
        }
    } catch (e) {
        console.error("加载活动失败:", e);
        dom.title.innerText = "活动加载失败";
        dom.drawBtn.disabled = true;
        dom.drawBtn.style.opacity = "0.5";
    }
}

async function doDrawRequest() {
    const res = await fetch("/api/lottery/draw", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            userId: currentCUserId,
            activityId: currentActivityId
        })
    });
    const data = await res.json();
    if (Number(data.code) !== 200) {
        return {
            prizeId: "ERROR",
            prizeName: data.msg || data.message || "抽奖失败",
            remainPoints: null,
            success: false
        };
    }

    const drawData = data.data || {};
    return {
        prizeId: drawData.prizeId || "NO_AWARD",
        prizeName: drawData.prizeName || drawData.message || "谢谢参与",
        remainPoints: drawData.remainPoints,
        success: true
    };
}

async function handleDraw() {
    if (isDrawing || !currentActivityId) return;
    const ok = await ensureCUserReady();
    if (!ok) return;

    if (getCurrentPoints() < currentDeductPoints) {
        showInsufficientFeedback("当前积分不足");
        return;
    }

    isDrawing = true;
    let result = null;
    let timeoutId = null;

    const requestPromise = doDrawRequest()
        .then((r) => {
            result = r;
        })
        .catch((e) => {
            console.error("抽奖请求失败:", e);
            result = { prizeId: "ERROR", prizeName: "网络异常，请稍后重试", remainPoints: null, success: false };
        });

    timeoutId = setTimeout(() => {
        if (!result) {
            result = { prizeId: "ERROR", prizeName: "请求超时，请稍后重试", remainPoints: null, success: false };
        }
    }, MAX_SPIN_WAIT_MS);

    let speed = 45;
    let circles = 0;

    const tick = async () => {
        resetActiveCells();
        const currentCell = document.getElementById(`cell-${seq[currentIdx]}`);
        if (currentCell) currentCell.classList.add("active");

        currentIdx = (currentIdx + 1) % 8;
        if (currentIdx === 0) circles++;

        if (circles < 4 || !result) {
            setTimeout(tick, speed);
            return;
        }

        speed += 45;
        const targetIdx = resolveTargetIndex(result.prizeId, result.prizeName);
        const landedIdx = seq[(currentIdx - 1 + 8) % 8];

        if (speed > 420 && landedIdx === targetIdx) {
            clearTimeout(timeoutId);
            await requestPromise;

            if (result.remainPoints != null) {
                setCurrentPoints(result.remainPoints);
            } else {
                await fetchPoints().catch(() => {});
            }

            if (!result.success && isInsufficientPointsMsg(result.prizeName)) {
                showInsufficientFeedback(result.prizeName);
                isDrawing = false;
                return;
            }

            const icon = resolveResultIcon(result.prizeId, result.prizeName);
            setTimeout(() => showResult(result.prizeName, icon), 500);
            loadRewardList();
            return;
        }

        setTimeout(tick, speed);
    };

    tick();
}

function bindEvents() {
    dom.drawBtn.addEventListener("click", handleDraw);
    dom.cUserLoginBtn.addEventListener("click", async () => {
        try {
            await cUserAuth();
        } catch (e) {
            showCUserError(e.message || "登录失败");
        }
    });

    dom.switchUserBtn.addEventListener("click", () => {
        clearCurrentUser("已退出当前账号，请重新登录");
        openCUserModal();
    });

    dom.logoutUserBtn.addEventListener("click", () => {
        clearCurrentUser("已退出登录");
        openCUserModal();
    });
}

bindEvents();
loadData();
