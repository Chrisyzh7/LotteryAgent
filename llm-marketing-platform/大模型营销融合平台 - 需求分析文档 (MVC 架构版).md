# 大模型营销融合平台 - 需求分析文档 (MVC 架构版)

## 1. 核心业务流程 (Simplified Flow)

1. **用户登录**：统一 UserId，作为聊天和抽奖的唯一凭证。
2. **模型交互**：用户在 Controller 发起聊天请求 -> Service 校验数据库中该用户是否有该模型权限 -> 调用大模型 API。
3. **营销触发**：用户达成特定条件（如每日首次登录）-> 调用营销 Service 增加抽奖次数。
4. **抽奖发奖**：用户点击抽奖 -> 命中奖品 -> **直接更新用户权益表** (User_Permission) -> 聊天界面刷新，解锁新模型。

---

# 大模型平台 (LLM-Base) 开发需求分析文档

## 1. 系统核心设计理念 (LLM-First)
大模型平台作为一个独立的 MVC 项目，其核心目标是：**屏蔽不同大模型 API 的差异，为前端提供统一的对话体验。**

### 核心设计点：
*   **策略模式 (Strategy Pattern)**：针对 GPT, DeepSeek, Gemini 等不同厂商，通过统一接口实现，方便随时增加新模型。
*   **流式输出 (SSE/Streaming)**：对话必须支持打字机效果，不能等全部生成完再显示。
*   **上下文管理 (Context)**：自动携带历史记录，让 AI 记得“上一句话说了什么”。

---

## 2. 数据库详细设计 (db_llm)
这是大模型平台运行的基石，重点在于记录“模型属性”和“对话内容”。

### 2.1 模型配置表 (`llm_model_config`)
用于定义系统支持哪些模型，以及它们的 API 地址。
*   `id`: 自增 ID
*   `model_key`: 模型唯一标识 (如 `gpt-4o`, `deepseek-v3`)
*   `model_name`: 前端显示的名称
*   `api_host`: API 请求地址
*   `api_key`: 该模型的专属 Key (如果不加密直接存，建议通过配置中心读取)
*   `is_active`: 是否启用该模型

### 2.2 用户模型权益表 (`user_model_permission`)
**这是连接营销平台的“钩子”。** 记录用户对每个模型的使用额度。
*   `user_id`: 用户 ID (128位)
*   `model_key`: 模型标识
*   `is_unlocked`: 是否解锁 (1=已解锁, 0=锁定)
*   `left_count`: 剩余对话次数 (-1 为无限)

### 2.3 聊天历史表 (`chat_history`)
*   `id`: 自增 ID
*   `session_id`: 对话组 ID (用于区分不同的聊天窗口)
*   `user_id`: 用户 ID
*   `model_key`: 使用的模型
*   `role`: 角色 (`user` 或 `assistant`)
*   `content`: 消息内容
*   `create_time`: 发送时间

---

## 3. 功能模块详细设计 (MVC 架构)

### 3.1 核心对话模块 (Chat Module) - **最优先实现**
*   **Controller层 (`ChatController`)**:
    *   `POST /api/chat/completions`: 接收用户消息。
    *   **参数**: `userId`, `modelKey`, `content`, `sessionId`。
    *   **处理流**: 调用 Service 获取流式响应。
*   **Service层 (`LLMService`)**:
    *   **逻辑流程**:
        1.  **鉴权**: 调用 `PermissionService` 检查用户是否有权使用该模型且次数 > 0。
        2.  **获取上下文**: 从 `chat_history` 捞出该 `sessionId` 下最近的 10 条记录。
        3.  **分发策略**: 根据 `modelKey` 决定使用哪个 API 适配器（Adapter）。
        4.  **调用接口**: 使用 OkHttp 发起流式请求。
        5.  **落库**: 异步保存用户问题和 AI 的回答。
*   **Util层 (`LLMUtil/Adapter`)**:
    *   统一封装 OpenAI 协议，将不同厂家的参数映射成标准格式。

### 3.2 权益管理模块 (Permission Module)
*   **Service层**:
    *   `checkPermission(userId, modelKey)`: 返回是否允许对话。
    *   `consume(userId, modelKey)`: 扣减一次对话次数（如果不是无限次数）。
    *   **[预留融合点]** `grantPermission(userId, awardConfig)`: 被动接收外部指令更新权限。

---

## 4. UI 界面逻辑实现方案 (参考截图)

### 4.1 动态模型选择器
*   **逻辑**: 前端挂载时请求 `GET /api/permission/user-models?userId=xxx`。
*   **展示**:
    *   `is_unlocked = 1`: 正常显示，可点击切换。
    *   `is_unlocked = 0`: 图标置灰并加锁，点击弹出“抽奖页面”或“积分兑换”。

### 4.2 对话框
*   **SSE 实现**: 前端使用 `EventSource` 或 `fetch` 的流式读取，实时渲染 Markdown 格式内容。

---

## 5. Vibe Coding 指令集 (按开发阶段排序)

你可以按照以下指令引导 AI 完成大模型平台开发：

### 第一阶段：搭建多模型骨架
> "Hey AI, 帮我根据 MVC 模式搭建大模型对话骨架：
> 1. 创建 `ChatHistory` 实体类。
> 2. 实现一个 `LLMStrategy` 接口，定义 `executeChat` 方法。
> 3. 为 DeepSeek 创建一个实现类 `DeepSeekAdapter`，使用 OkHttp 调用其 API（使用标准 OpenAI 协议格式）。
> 4. 实现 `ChatController` 的对话接口，暂时不加权限校验，确保能跑通并将对话存入 `chat_history` 表。"

### 第二阶段：实现上下文记忆
> "修改 `ChatService`，在调用大模型 API 之前，先从 `chat_history` 中根据 `sessionId` 查询出最近 5 次的对话记录，并将其构造进 API 的 `messages` 参数中，使 AI 具备上下文记忆能力。"

### 第三阶段：加入权限拦截 (为融合做准备)
> "实现 `PermissionInterceptor`。在每次调用对话 API 前，检查 `user_model_permission` 表。如果该用户的 `is_unlocked` 为 0 或 `left_count` 为 0，则抛出自定义异常 'Insufficient_Rights'，返回给前端提示去抽奖。"

---

### 为什么这份文档更清晰？
1.  **独立性强**：它描述了一个完整的对话系统，即使没有营销系统，它也能跑。
2.  **数据对齐**：通过 `model_key` 字段，后期可以无缝对应 `big_market` 中的 `award_config` 字段。
3.  **技术方案明确**：提到了 SSE、策略模式、上下文记忆等 LLM 开发的核心难点。





关于上下文记忆：

**单纯地从数据库捞出最后 10 条记录塞给 AI，确实是“初级”的做法，在生产环境下确实显得比较“鸡肋”。**

这种做法有三个致命缺点：

1. **Token 浪费（烧钱）**：对话越长，每次请求带的历史记录越多，消耗的 Token 呈指数级增长。
2. **遗忘症（截断限制）**：如果你只传最后 10 条，第 11 条之前的内容 AI 就完全不知道了。
3. **理解偏移**：如果中间用户聊偏了，AI 会被近期的琐碎对话带跑，忘掉最初设定的任务目标。

在真正专业的大模型平台设计中，上下文记忆通常分为 **“短期记忆”、“总结记忆” 和 “长期记忆”**。

### 1. 为什么大模型必须通过这种方式“记忆”？

首先要明确一点：**目前的 LLM API（如 OpenAI, DeepSeek）都是“无状态”的。**
AI 不像人一样有大脑可以实时存储。它就像一个超级复读机，你必须把“剧本”（历史对话）完整地递给它，它才能根据剧本演下一场戏。

### 2. 进阶的记忆实现方案（让它不再鸡肋）

为了让你的毕业设计更有技术含量，你可以考虑以下几种优化手段：

#### A. 总结记忆 (Summarization Memory) —— “省钱方案”

- **做法**：当对话超过一定长度（比如 20 条），让 AI 自己把前 15 条对话**总结成一段话**（System Prompt）。
- **优点**：用几百个字代替了几千个字的原始对话，极大地节省了 Token。
- **效果**：AI 记得刚才大概聊了什么，但不记得细节词汇。

#### B. 滑动窗口 + 关键信息提取 (Sliding Window + Keyword)

- **做法**：除了传最后几条对话，额外维护一个 User_Profile 字段，记录用户的偏好（例如：用户喜欢 Java，目前在做毕业设计）。
- **逻辑**：每次对话前，先让一个微小的模型（比如 GPT-3.5 或本地轻量模型）判断当前对话是否包含需要永久记住的信息。

#### C. 向量数据库 (Long-term Memory / RAG) —— “专业方案”

- **做法**：将所有的历史对话存入 **向量数据库** (如 Pinecone, Milvus, 或 Redis 的向量扩展)。
- **逻辑**：
  1. 用户问：“我昨天提到的那个 Bug 怎么改？”
  2. 系统去向量库里搜索“Bug”、“修改”相关的历史片段。
  3. 只把搜出来的那个片段喂给 AI。
- **优点**：AI 拥有了“无限”的记忆空间，即使是一年前聊过的东西也能翻出来。

### 3. 在你的 MVC 项目中建议怎么写？

考虑到毕设的复杂度和开发时间，我建议你采用 **“增强型滑动窗口”**，这在大模型开发中叫 **WindowBuffer**。

#### 逻辑代码结构（伪代码）：

```
public List<ChatMessage> getRefinedContext(String userId, String sessionId) {
    // 1. 必带：系统提示词 (System Prompt，决定AI身份)
    List<ChatMessage> context = new ArrayList<>();
    context.add(new ChatMessage("system", "你是一个基于大营销平台的智能助手..."));

    // 2. 关键：获取用户的固定画像 (从数据库 user_account 表读)
    UserAccount user = userMapper.selectById(userId);
    context.add(new ChatMessage("system", "用户信息：等级" + user.getLevel()));

    // 3. 动态：获取最近的 5-8 条详细对话 (短期记忆)
    List<ChatHistory> recentHistory = chatHistoryMapper.getRecent(sessionId, 8);
    context.addAll(convertToOpenAIFormat(recentHistory));

    return context;
}
```

------



### 4. 为什么这对你的项目很重要？

因为你这个项目是**“营销融合平台”**。

如果你的 AI 有了上下文感知，它可以变得非常“聪明”：

- **场景 1**：用户问：“我还有多少次机会？”
- **鸡肋做法**：AI 说：“我不知道，请查询个人中心。”
- **高级做法**：你的后端把用户当前的抽奖次数、积分余额作为 **“系统上下文”** 喂给 AI。AI 会直接回答：“你还有 3 次抽奖机会，刚才抽中了 GPT-4，快去试试看！”

**总结：**
所谓的“上下文记忆”确实不是简单的 limit 10。
**真正的设计是：如何把数据库里的静态数据（余额、权限、等级）转变为 AI 的动态常识。**

