const API_BASE = "http://localhost:8091/api";
const ASSISTANT_GENERAL = "general";
const ASSISTANT_LOTTERY = "lottery_agent";
const DEFAULT_LOTTERY_STYLE = "dark_neon";
const THINKING_HINTS = [
    "正在理解你的问题",
    "正在组织回答结构",
    "正在检查可用信息",
    "正在生成最终回复"
];

const dom = {
    authModal: document.getElementById("auth-modal"),
    appContainer: document.getElementById("app-container"),
    username: document.getElementById("username"),
    password: document.getElementById("password"),
    authError: document.getElementById("auth-error"),
    btnLogin: document.getElementById("btn-login"),
    btnRegister: document.getElementById("btn-register"),
    displayUsername: document.getElementById("display-username"),
    assistantSelect: document.getElementById("assistant-select"),
    lotteryStyleWrap: document.getElementById("lottery-style-wrap"),
    lotteryStyleSelect: document.getElementById("lottery-style-select"),
    modelSelect: document.getElementById("model-select"),
    historyList: document.getElementById("sidebar-history-list"),
    chatMessages: document.getElementById("chat-messages"),
    chatInput: document.getElementById("chat-input"),
    toastContainer: document.getElementById("toast-container")
};

document.addEventListener("DOMContentLoaded", () => {
    bindEvents();
    initAssistantSelector();
    initLotteryStyleSelector();
    initBubbleSelects();
    setupAutoResize(dom.chatInput);

    const token = localStorage.getItem("chat_token");
    const username = localStorage.getItem("chat_username");

    if (!token) {
        showModal();
    } else {
        hideModal();
        dom.displayUsername.innerText = username || "已登录用户";
        loadSessions();
        if (!sessionStorage.getItem("current_session_id")) {
            startNewChat();
        }
    }
});

function bindEvents() {
    dom.btnLogin.addEventListener("click", handleLogin);
    dom.btnRegister.addEventListener("click", handleRegister);

    dom.chatInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
}

function initAssistantSelector() {
    const saved = localStorage.getItem("current_assistant_type") || ASSISTANT_GENERAL;
    dom.assistantSelect.value = saved;
    syncLotteryStyleVisibility(saved);

    dom.assistantSelect.addEventListener("change", () => {
        localStorage.setItem("current_assistant_type", dom.assistantSelect.value);
        syncLotteryStyleVisibility(dom.assistantSelect.value);
        sessionStorage.removeItem("current_session_id");
        startNewChat();
        loadSessions();
    });
}

function initLotteryStyleSelector() {
    const savedStyle = localStorage.getItem("lottery_page_style") || DEFAULT_LOTTERY_STYLE;
    dom.lotteryStyleSelect.value = savedStyle;
    dom.lotteryStyleSelect.addEventListener("change", () => {
        localStorage.setItem("lottery_page_style", dom.lotteryStyleSelect.value || DEFAULT_LOTTERY_STYLE);
    });
}

function getCurrentAssistantType() {
    return localStorage.getItem("current_assistant_type") || ASSISTANT_GENERAL;
}

function getLotteryStyleValue() {
    return dom.lotteryStyleSelect.value || localStorage.getItem("lottery_page_style") || DEFAULT_LOTTERY_STYLE;
}

function isLikelyLotteryCreationRequest(text) {
    const s = (text || "").trim();
    if (!s) return false;
    const strongCreate = /(创建|新建|生成|配置|搭建|设计|做一个|来一个).{0,8}(抽奖|转盘|活动)/i;
    const detailsCreate = /(活动名称|奖品|库存|概率|权重|每次抽奖|消耗积分)/i;
    return strongCreate.test(s) || detailsCreate.test(s);
}

function syncLotteryStyleVisibility(assistantType) {
    dom.lotteryStyleWrap.style.display = assistantType === ASSISTANT_LOTTERY ? "inline-flex" : "none";
}

function buildPromptForAssistant(rawText, assistantType) {
    if (assistantType !== ASSISTANT_LOTTERY) return rawText;
    const hasStyleHint = /(pageStyle|活动风格|页面风格|dark_neon|ins_minimal|fresh_light)/i.test(rawText);
    if (hasStyleHint) return rawText;
    if (!isLikelyLotteryCreationRequest(rawText)) return rawText;
    return `${rawText}\n活动风格：${getLotteryStyleValue()}`;
}

function setupAutoResize(textarea) {
    const resize = () => {
        textarea.style.height = "auto";
        const maxHeight = 180;
        textarea.style.height = Math.min(textarea.scrollHeight, maxHeight) + "px";
        textarea.style.overflowY = textarea.scrollHeight > maxHeight ? "auto" : "hidden";
    };
    textarea.addEventListener("input", resize);
    resize();
}

function initBubbleSelects() {
    [dom.assistantSelect, dom.modelSelect, dom.lotteryStyleSelect].forEach((selectEl) => {
        enhanceBubbleSelect(selectEl);
    });
}

function enhanceBubbleSelect(selectEl) {
    if (!selectEl || selectEl.dataset.enhanced === "true") return;
    selectEl.dataset.enhanced = "true";

    const wrapper = document.createElement("div");
    wrapper.className = "bubble-select";
    if (selectEl.classList.contains("assistant-select")) {
        wrapper.classList.add("bubble-select-block");
    } else {
        wrapper.classList.add("bubble-select-inline");
    }

    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "bubble-select-trigger";

    const label = document.createElement("span");
    label.className = "bubble-select-label";
    const arrow = document.createElement("i");
    arrow.className = "fa-solid fa-angle-down bubble-select-arrow";

    trigger.appendChild(label);
    trigger.appendChild(arrow);

    const menu = document.createElement("div");
    menu.className = "bubble-select-menu";

    const updateLabel = () => {
        const opt = selectEl.options[selectEl.selectedIndex];
        label.textContent = opt ? opt.text : "";
    };

    const close = () => {
        wrapper.classList.remove("open");
    };

    const updateMenuPlacement = () => {
        wrapper.classList.remove("open-up");
        menu.style.maxHeight = "";

        const triggerRect = trigger.getBoundingClientRect();
        const viewportH = window.innerHeight || document.documentElement.clientHeight || 800;
        const spaceBelow = viewportH - triggerRect.bottom - 12;
        const spaceAbove = triggerRect.top - 12;
        const preferUp = spaceBelow < 220 && spaceAbove > spaceBelow;

        if (preferUp) {
            wrapper.classList.add("open-up");
        }

        const available = Math.max(120, preferUp ? (spaceAbove - 10) : (spaceBelow - 10));
        menu.style.maxHeight = `${Math.min(320, available)}px`;
    };

    const buildOptions = () => {
        menu.innerHTML = "";
        Array.from(selectEl.options).forEach((opt) => {
            const item = document.createElement("button");
            item.type = "button";
            item.className = "bubble-select-option";
            if (opt.value === selectEl.value) {
                item.classList.add("active");
            }
            item.textContent = opt.text;
            item.addEventListener("click", () => {
                if (selectEl.value === opt.value) {
                    close();
                    return;
                }
                selectEl.value = opt.value;
                selectEl.dispatchEvent(new Event("change", { bubbles: true }));
                updateLabel();
                buildOptions();
                close();
            });
            menu.appendChild(item);
        });
    };

    selectEl.classList.add("native-select-hidden");
    updateLabel();
    buildOptions();

    trigger.addEventListener("click", (e) => {
        e.stopPropagation();
        const isOpen = wrapper.classList.contains("open");
        document.querySelectorAll(".bubble-select.open").forEach((n) => n.classList.remove("open"));
        if (!isOpen) {
            updateMenuPlacement();
            wrapper.classList.add("open");
        }
    });

    selectEl.addEventListener("change", () => {
        updateLabel();
        buildOptions();
    });

    wrapper.appendChild(trigger);
    wrapper.appendChild(menu);
    selectEl.parentNode.insertBefore(wrapper, selectEl);
    wrapper.appendChild(selectEl);

    document.addEventListener("click", (e) => {
        if (!wrapper.contains(e.target)) {
            close();
        }
    });

    window.addEventListener("resize", () => {
        if (wrapper.classList.contains("open")) {
            updateMenuPlacement();
        }
    });
}

function showModal() {
    dom.authModal.style.display = "flex";
    dom.appContainer.style.display = "none";
}

function hideModal() {
    dom.authModal.style.display = "none";
    dom.appContainer.style.display = "flex";
}

function showToast(message, type = "info") {
    const toast = document.createElement("div");
    toast.className = `toast ${type}`;

    let icon = '<i class="fa-solid fa-circle-info" style="color:#007aff"></i>';
    if (type === "success") icon = '<i class="fa-solid fa-circle-check" style="color:#34c759"></i>';
    if (type === "error") icon = '<i class="fa-solid fa-circle-exclamation" style="color:#ff3b30"></i>';

    toast.innerHTML = `${icon} <span>${message}</span>`;
    dom.toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = "toast-out 0.3s cubic-bezier(0.25, 0.8, 0.25, 1) forwards";
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

function showError(msg) {
    showToast(msg, "error");
    dom.authError.innerText = msg;
    setTimeout(() => {
        dom.authError.innerText = "";
    }, 3000);
}

async function handleLogin() {
    const username = dom.username.value.trim();
    const password = dom.password.value.trim();
    if (!username || !password) {
        showError("请输入账号和密码");
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/user/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();

        if (data.code === 200) {
            localStorage.setItem("chat_token", data.data.token);
            localStorage.setItem("chat_username", data.data.username);
            dom.displayUsername.innerText = data.data.username;
            hideModal();
            startNewChat();
            loadSessions();
            showToast("登录成功，商家身份已同步", "success");
            return;
        }

        showError(data.message || "登录失败");
    } catch (e) {
        showError("网络错误，请稍后重试");
    }
}

async function handleRegister() {
    const username = dom.username.value.trim();
    const password = dom.password.value.trim();
    if (!username || !password) {
        showError("请输入账号和密码");
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/user/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();

        if (data.code === 200) {
            showToast("注册成功，请登录", "success");
            return;
        }

        showError(data.message || "注册失败");
    } catch (e) {
        showError("网络错误，请稍后重试");
    }
}

function logout() {
    localStorage.removeItem("chat_token");
    localStorage.removeItem("chat_username");
    sessionStorage.removeItem("current_session_id");
    showModal();
}

window.logout = logout;

async function loadSessions() {
    const token = localStorage.getItem("chat_token");
    if (!token) return;

    try {
        const assistantType = getCurrentAssistantType();
        const res = await fetch(`${API_BASE}/chat/sessions?assistantType=${encodeURIComponent(assistantType)}`, {
            headers: { Authorization: `Bearer ${token}` }
        });

        if (res.status === 401) {
            logout();
            return;
        }

        const data = await res.json();
        if (data.code === 200) {
            renderSessionList(data.data || []);
        }
    } catch (e) {
        console.error("加载会话列表失败", e);
    }
}

function renderSessionList(sessions) {
    dom.historyList.innerHTML = "";

    if (!sessions.length) {
        dom.historyList.innerHTML = '<div class="history-item" style="color:#aaa; cursor:default">暂无对话记录</div>';
        return;
    }

    const currentSessionId = sessionStorage.getItem("current_session_id");

    sessions.forEach((sess) => {
        const div = document.createElement("div");
        div.className = "history-item";
        if (sess.sessionId === currentSessionId) {
            div.classList.add("active");
        }

        const title = sess.title || "新对话";
        const shortTitle = title.length > 15 ? `${title.slice(0, 15)}...` : title;
        div.innerHTML = `<i class="fa-regular fa-message"></i> ${escapeHtml(shortTitle)}`;
        div.onclick = () => loadMessages(sess.sessionId);
        dom.historyList.appendChild(div);
    });
}

async function loadMessages(sessionId) {
    const token = localStorage.getItem("chat_token");
    if (!token) return;

    sessionStorage.setItem("current_session_id", sessionId);
    loadSessions();

    try {
        const res = await fetch(`${API_BASE}/chat/messages?sessionId=${encodeURIComponent(sessionId)}`, {
            headers: { Authorization: `Bearer ${token}` }
        });

        if (res.status === 401) {
            logout();
            return;
        }

        const data = await res.json();
        if (data.code === 200) {
            dom.chatMessages.innerHTML = "";
            (data.data || []).forEach((msg) => {
                const bubbleId = appendMessage(msg.messageRole, msg.content || "", true);
                if (msg.messageRole === "assistant") {
                    tryRenderHistoryToolCard(msg.content || "", bubbleId);
                }
            });
            dom.chatMessages.scrollTop = dom.chatMessages.scrollHeight;
        }
    } catch (e) {
        console.error("加载会话消息失败", e);
    }
}

function startNewChat() {
    sessionStorage.removeItem("current_session_id");
    const assistantType = getCurrentAssistantType();
    const guide = assistantType === ASSISTANT_LOTTERY
        ? `我是一个抽奖智能体，你只要描述活动需求，我就能帮你生成面向 C 端用户的抽奖活动链接。\n\n建议按下面格式输入：\n活动名称：\n每次抽奖消耗积分：\n活动风格（可选）：dark_neon / ins_minimal / fresh_light\n奖品名称：\n奖品库存：\n奖品概率（0~1）或权重：\n活动开始时间（可选）：\n活动结束时间（可选）：\n\n示例：\n活动名称：奶茶店七夕抽奖\n每次抽奖消耗积分：10\n活动风格：ins_minimal\n奖品：电视机，库存1，概率0.001\n奖品：奶茶免单券，库存100，概率0.05`
        : "欢迎开启新对话，你可以直接提问。";

    dom.chatMessages.innerHTML = `
        <div class="message assistant">
            <div class="avatar"><i class="fa-solid fa-robot"></i></div>
            <div class="bubble">${renderMarkdownHtml(guide)}</div>
        </div>
    `;
    enhanceMarkdownContainer(dom.chatMessages);

    document.querySelectorAll(".history-item").forEach((el) => el.classList.remove("active"));
}

window.startNewChat = startNewChat;

function appendMessage(role, text, isParse = false, isThinking = false) {
    const msgId = `msg-${Date.now()}-${Math.floor(Math.random() * 1000)}`;

    let finalHtml = text;
    if (isParse && typeof marked !== "undefined") {
        finalHtml = renderMarkdownHtml(text);
    } else if (isThinking) {
        finalHtml = text;
    } else {
        finalHtml = escapeHtml(text).replace(/\n/g, "<br/>");
    }

    const html = role === "user"
        ? `
        <div class="message user">
            <div class="avatar"><i class="fa-solid fa-user"></i></div>
            <div class="bubble">${finalHtml}</div>
        </div>`
        : `
        <div class="message assistant ${isThinking ? "thinking" : ""}">
            <div class="avatar"><i class="fa-solid fa-robot"></i></div>
            <div class="bubble" id="${msgId}">${finalHtml}</div>
        </div>`;

    dom.chatMessages.insertAdjacentHTML("beforeend", html);
    if (isParse && !isThinking) {
        const lastBubble = dom.chatMessages.lastElementChild?.querySelector(".bubble");
        if (lastBubble) {
            enhanceMarkdownContainer(lastBubble);
        }
    }
    dom.chatMessages.scrollTop = dom.chatMessages.scrollHeight;
    return msgId;
}

function buildThinkingHtml() {
    return `
        <div class="thinking-wrap">
            <div class="typing-indicator"><span></span><span></span><span></span></div>
            <div class="thinking-text">\u6b63\u5728\u601d\u8003\u4e2d...</div>
            <div class="thinking-meta">
                <span class="thinking-elapsed">\u5df2\u601d\u8003 0.0 \u79d2</span>
                <span class="thinking-hint">${THINKING_HINTS[0]}</span>
            </div>
        </div>
    `;
}
function startThinkingTicker(bubbleId) {
    const bubble = document.getElementById(bubbleId);
    if (!bubble) return () => {};

    const elapsedEl = bubble.querySelector(".thinking-elapsed");
    const hintEl = bubble.querySelector(".thinking-hint");
    if (!elapsedEl || !hintEl) return () => {};

    let elapsedMs = 0;
    let hintIndex = 0;
    let stopped = false;

    const render = () => {
        const seconds = (elapsedMs / 1000).toFixed(1);
        elapsedEl.innerText = "\u5df2\u601d\u8003 " + seconds + " \u79d2";
        hintEl.innerText = THINKING_HINTS[hintIndex];
    };
    render();

    const timer = setInterval(() => {
        if (stopped) return;
        elapsedMs += 100;
        if (elapsedMs % 4000 === 0) {
            hintIndex = (hintIndex + 1) % THINKING_HINTS.length;
        }
        render();
    }, 100);

    return () => {
        stopped = true;
        clearInterval(timer);
    };
}
async function sendMessage() {
    const text = dom.chatInput.value.trim();
    if (!text) return;

    const token = localStorage.getItem("chat_token");
    if (!token) {
        logout();
        return;
    }

    const assistantType = getCurrentAssistantType();
    const finalPrompt = buildPromptForAssistant(text, assistantType);

    let sessionId = sessionStorage.getItem("current_session_id");
    let isNewSession = false;
    if (!sessionId) {
        const prefix = assistantType === ASSISTANT_LOTTERY ? "lottery" : "general";
        sessionId = `${prefix}_${Math.random().toString(36).slice(2, 12)}`;
        sessionStorage.setItem("current_session_id", sessionId);
        isNewSession = true;
    }

    appendMessage("user", text, false);
    dom.chatInput.value = "";
    dom.chatInput.style.height = "48px";
    dom.chatInput.style.overflowY = "hidden";

    const loadingId = appendMessage("assistant", buildThinkingHtml(), false, true);
    const stopThinkingTicker = startThinkingTicker(loadingId);

    try {
        const res = await fetch(`${API_BASE}/chat/send`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${token}`
            },
            body: JSON.stringify({
                sessionId,
                modelKey: dom.modelSelect.value,
                assistantType,
                prompt: finalPrompt
            })
        });

        if (res.status === 401) {
            logout();
            return;
        }

        const data = await res.json();
        const msgBubble = document.getElementById(loadingId);
        stopThinkingTicker();
        msgBubble.closest(".message").classList.remove("thinking");

        if (data.code === 200) {
            const payload = data.data || {};
            const replyText = payload.replyText || "";
            msgBubble.innerHTML = renderMarkdownHtml(replyText);
            enhanceMarkdownContainer(msgBubble);

            if (payload.toolExecuted) {
                renderToolResultCard(msgBubble, payload);
            }

            if (isNewSession) {
                loadSessions();
            }
        } else {
            msgBubble.innerText = `请求错误: ${data.message || "未知错误"}`;
            msgBubble.style.color = "var(--error-color, red)";
        }

        dom.chatMessages.scrollTop = dom.chatMessages.scrollHeight;
    } catch (e) {
        const msgBubble = document.getElementById(loadingId);
        stopThinkingTicker();
        msgBubble.closest(".message").classList.remove("thinking");
        msgBubble.innerText = `网络异常或请求超时，请重试。\n${e.message}`;
        msgBubble.style.color = "var(--error-color, red)";
    }
}

window.sendMessage = sendMessage;

function renderToolResultCard(targetBubble, payload) {
    const toolName = payload.toolName;
    const toolData = payload.toolData || {};

    const card = document.createElement("div");
    card.className = "activity-card";

    if (toolName === "create_marketing_activity") {
        const activityId = toolData.activityId;
        const activityName = toolData.activityName || "未命名活动";
        const pageStyle = toolData.pageStyle || "dark_neon";
        const playLink = toolData.playLink || `/lottery.html?activityId=${activityId}`;
        const manageLink = `/reward-center.html?activityId=${activityId}`;

        card.innerHTML = `
            <div class="activity-card-title">活动已创建（后端工具调用成功）</div>
            <div class="activity-card-body">
                <div><strong>活动名：</strong>${escapeHtml(activityName)}</div>
                <div><strong>activityId：</strong>${escapeHtml(activityId)}</div>
                <div><strong>风格：</strong>${escapeHtml(pageStyle)}</div>
            </div>
            <div class="activity-card-actions">
                <button class="activity-btn publish-btn" type="button">发布并预热</button>
                <a class="activity-btn play-btn" target="_blank" href="${escapeHtml(playLink)}">打开活动页</a>
                <a class="activity-btn manage-btn" target="_blank" href="${escapeHtml(manageLink)}">发奖管理</a>
            </div>
            <div class="activity-card-status">草稿态，待发布</div>
        `;

        const publishBtn = card.querySelector(".publish-btn");
        const statusEl = card.querySelector(".activity-card-status");

        publishBtn.addEventListener("click", async () => {
            publishBtn.disabled = true;
            statusEl.innerText = "发布中...";

            try {
                const res = await fetch(`${API_BASE}/lottery/publish`, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${localStorage.getItem("chat_token")}`
                    },
                    body: JSON.stringify({ activityId: String(activityId) })
                });
                const data = await res.json();

                if (data.code === 200) {
                    statusEl.innerText = "已发布并预热，C 端可抽奖";
                    return;
                }

                publishBtn.disabled = false;
                statusEl.innerText = `发布失败：${data.msg || data.message || "未知错误"}`;
            } catch (e) {
                publishBtn.disabled = false;
                statusEl.innerText = "发布失败：网络异常";
            }
        });
    } else if (toolName === "publish_marketing_activity") {
        const activityId = toolData.activityId;
        const activityName = toolData.activityName || "活动";
        const pageStyle = toolData.pageStyle || "dark_neon";
        const playLink = toolData.playLink || `/lottery.html?activityId=${activityId}`;
        const manageLink = `/reward-center.html?activityId=${activityId}`;

        card.innerHTML = `
            <div class="activity-card-title">活动已发布（后端工具调用成功）</div>
            <div class="activity-card-body">
                <div><strong>活动名：</strong>${escapeHtml(activityName)}</div>
                <div><strong>activityId：</strong>${escapeHtml(activityId)}</div>
                <div><strong>风格：</strong>${escapeHtml(pageStyle)}</div>
            </div>
            <div class="activity-card-actions">
                <a class="activity-btn play-btn" target="_blank" href="${escapeHtml(playLink)}">打开活动页</a>
                <a class="activity-btn manage-btn" target="_blank" href="${escapeHtml(manageLink)}">发奖管理</a>
            </div>
            <div class="activity-card-status">发布与预热完成</div>
        `;
    } else if (toolName === "get_merchant_activity_links") {
        const activityId = toolData.activityId;
        const activityName = toolData.activityName || "活动";
        const pageStyle = toolData.pageStyle || "dark_neon";
        const playLink = toolData.playLink || `/lottery.html?activityId=${activityId}`;
        const manageLink = toolData.manageLink || `/reward-center.html?activityId=${activityId}`;

        card.innerHTML = `
            <div class="activity-card-title">活动链接查询结果</div>
            <div class="activity-card-body">
                <div><strong>活动名：</strong>${escapeHtml(activityName)}</div>
                <div><strong>activityId：</strong>${escapeHtml(activityId)}</div>
                <div><strong>风格：</strong>${escapeHtml(pageStyle)}</div>
            </div>
            <div class="activity-card-actions">
                <a class="activity-btn play-btn" target="_blank" href="${escapeHtml(playLink)}">打开活动页</a>
                <a class="activity-btn manage-btn" target="_blank" href="${escapeHtml(manageLink)}">发奖管理</a>
            </div>
            <div class="activity-card-status">已返回最近活动链接</div>
        `;
    } else {
        card.innerHTML = `
            <div class="activity-card-title">工具执行结果</div>
            <div class="activity-card-body">${escapeHtml(JSON.stringify(toolData))}</div>
        `;
    }

    targetBubble.appendChild(card);
}

function tryRenderHistoryToolCard(content, bubbleId) {
    const text = String(content || "");
    if (!text || !bubbleId) return;

    const bubble = document.getElementById(bubbleId);
    if (!bubble) return;

    const idMatch = text.match(/activityId\s*=\s*(\d+)/i) || text.match(/activityid\s*[:：]\s*(\d+)/i);
    const playMatch = text.match(/\/lottery\.html\?[^\s，。]+/i);
    if (!idMatch && !playMatch) return;

    const activityId = idMatch ? idMatch[1] : null;
    const styleMatch = text.match(/style=([a-z_]+)/i) || text.match(/风格\s*[:：]\s*([a-z_]+)/i);
    const pageStyle = styleMatch ? styleMatch[1] : DEFAULT_LOTTERY_STYLE;
    const playLink = playMatch ? playMatch[0] : `/lottery.html?activityId=${activityId}&style=${pageStyle}`;
    const manageLink = activityId ? `/reward-center.html?activityId=${activityId}` : "/reward-center.html";
    const published = /已发布|发布成功|发布与预热完成|可抽奖/.test(text);
    const isLinkResult = /管理链接|发奖管理|最近活动链接|商家管理/.test(text);

    const payload = {
        toolName: isLinkResult
            ? "get_merchant_activity_links"
            : (published ? "publish_marketing_activity" : "create_marketing_activity"),
        toolData: {
            activityId: activityId,
            activityName: activityId ? `活动${activityId}` : "活动",
            pageStyle: pageStyle,
            playLink: playLink,
            manageLink: manageLink
        }
    };

    renderToolResultCard(bubble, payload);
}

function renderMarkdownHtml(text) {
    const source = String(text || "");
    const parsed = (typeof marked !== "undefined")
        ? marked.parse(source)
        : escapeHtml(source).replace(/\n/g, "<br/>");
    const sanitized = (typeof DOMPurify !== "undefined")
        ? DOMPurify.sanitize(parsed, { USE_PROFILES: { html: true } })
        : parsed;
    return `<div class="markdown-body">${sanitized}</div>`;
}

function enhanceMarkdownContainer(root) {
    if (!root) return;
    const markdownRoots = root.classList && root.classList.contains("markdown-body")
        ? [root]
        : Array.from(root.querySelectorAll(".markdown-body"));
    markdownRoots.forEach((markdownEl) => {
        markdownEl.querySelectorAll("pre code").forEach((codeEl) => {
            if (typeof hljs !== "undefined") {
                hljs.highlightElement(codeEl);
            }
        });
        markdownEl.querySelectorAll("pre").forEach((preEl) => {
            if (preEl.querySelector(".code-copy-btn")) return;
            const copyBtn = document.createElement("button");
            copyBtn.type = "button";
            copyBtn.className = "code-copy-btn";
            copyBtn.textContent = "\u590d\u5236";
            copyBtn.addEventListener("click", async () => {
                const codeText = preEl.querySelector("code")?.innerText || preEl.innerText || "";
                try {
                    if (navigator.clipboard?.writeText) {
                        await navigator.clipboard.writeText(codeText);
                    } else {
                        const temp = document.createElement("textarea");
                        temp.value = codeText;
                        document.body.appendChild(temp);
                        temp.select();
                        document.execCommand("copy");
                        document.body.removeChild(temp);
                    }
                    copyBtn.textContent = "\u5df2\u590d\u5236";
                    setTimeout(() => {
                        copyBtn.textContent = "\u590d\u5236";
                    }, 1200);
                } catch (e) {
                    copyBtn.textContent = "\u590d\u5236\u5931\u8d25";
                    setTimeout(() => {
                        copyBtn.textContent = "\u590d\u5236";
                    }, 1200);
                }
            });
            preEl.appendChild(copyBtn);
        });
    });
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}
