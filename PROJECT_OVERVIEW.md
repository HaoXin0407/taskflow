# TaskFlow 项目理解文档

> 本文档基于 `D:\My Course\ProjectTools\taskflow` 的**真实代码**整理，所有文件路径、类名、方法名均可在仓库中检索到。
> 标注 `⚠️ 缺失` 的部分为仓库中确实不存在的内容，未做任何臆测。

---

## 目录

- [一、项目定位与整体架构](#一项目定位与整体架构)
  - [1.1 项目定位](#11-项目定位)
  - [1.2 技术栈](#12-技术栈)
  - [1.3 目录结构](#13-目录结构)
  - [1.4 模块职责与依赖关系](#14-模块职责与依赖关系)
- [二、核心业务流程](#二核心业务流程)
  - [2.1 登录认证链路](#21-登录认证链路)
  - [2.2 令牌鉴权链路](#22-令牌鉴权链路)
  - [2.3 工单全生命周期](#23-工单全生命周期)
  - [2.4 审批流链路](#24-审批流链路)
  - [2.5 看板统计链路](#25-看板统计链路)
  - [2.6 操作日志链路](#26-操作日志链路)
- [三、重点代码模块解析](#三重点代码模块解析)
  - [3.1 工单状态机](#31-工单状态机核心)
  - [3.2 工单流转引擎](#32-工单流转引擎)
  - [3.3 三层权限体系](#33-三层权限体系)
  - [3.4 JWT 无状态认证](#34-jwt-无状态认证)
  - [3.5 审批链数据模型](#35-审批链数据模型)
  - [3.6 统一响应与全局异常](#36-统一响应与全局异常)
  - [3.7 AOP 操作日志](#37-aop-操作日志)
  - [3.8 前端路由与菜单](#38-前端路由与菜单)
  - [3.9 SLA 超时扫描](#39-sla-超时扫描定时任务)
- [四、关键设计决策](#四关键设计决策)
  - [4.1 决策清单](#41-决策清单)
  - [4.2 扩展点](#42-扩展点)
  - [4.3 潜在风险与已知缺陷](#43-潜在风险与已知缺陷)
- [五、面试问答准备](#五面试问答准备)
- [六、仓库缺失信息清单](#六仓库缺失信息清单)

---

## 一、项目定位与整体架构

### 1.1 项目定位

TaskFlow 是一个**企业级工单流转与审批管理系统**，覆盖两条核心业务链路：

| 业务线 | 说明 | 核心表 |
|--------|------|--------|
| **工单流转** | 提交 → 受理 → 处理 → 验收 → 归档，带完整流转轨迹与 SLA 监控 | `wf_ticket` / `wf_ticket_flow` / `wf_ticket_comment` |
| **审批流** | 发起 → 多级串行审批 → 通过/驳回，节点级权限 | `ap_approval` / `ap_approval_node` |

配套能力：RBAC 权限体系、操作日志审计、通知中心、数据看板、定时 SLA 扫描。

**技术定位**：单体应用（非微服务），但按**分层架构 + 包结构隔离**组织，为后续拆分预留了边界。

### 1.2 技术栈

| 层次 | 技术 | 版本 | 备注 |
|------|------|------|------|
| 运行时 | JDK | 17 | `pom.xml` 中 `java.version=17` |
| 框架 | Spring Boot | 3.2.5 | `spring-boot-starter-parent` |
| 安全 | Spring Security | 6.2.4 | JWT 无状态认证 |
| 持久层 | MyBatis-Plus | 3.5.7 | BaseMapper + 注解 SQL，无 XML |
| 数据库 | MySQL | 8.3（驱动） | 12 张表 |
| 令牌 | JJWT | 0.12.5 | `jjwt-api` / `jjwt-impl` / `jjwt-jackson` |
| 接口文档 | Knife4j (OpenAPI3) | 4.5.0 | `/api/doc.html` |
| 切面 | Spring AOP | — | 操作日志 |
| 校验 | Jakarta Validation | 3.0.2 | `@Valid` + `@NotBlank` |
| 构建 | Maven | — | 含 Maven Wrapper（`mvnw`） |

**前端**

| 技术 | 版本 | 备注 |
|------|------|------|
| Vue | 3 | `<script setup>` 组合式 API |
| Vite | — | 构建工具 |
| Element Plus | — | UI 组件库 |
| ECharts | — | 看板图表 |
| Pinia | — | 用户状态 + 权限缓存 |
| Vue Router | 4 | `createWebHistory` |
| Axios | — | 统一封装 `utils/request.js` |

> Lombok 1.18.42 必须 ≥1.18.42，否则 JDK17 + Maven 3.10 下报
> `TypeTag :: UNKNOWN`。配置细节见 `backend/pom.xml`。

### 1.3 目录结构

```
taskflow/
├── backend/                                  Spring Boot 后端
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd / .mvn/              Maven Wrapper
│   └── src/
│       ├── main/
│       │   ├── java/com/taskflow/
│       │   │   ├── TaskFlowApplication.java  启动类
│       │   │   ├── annotation/  OperLog.java              操作日志注解
│       │   │   ├── aspect/      OperLogAspect.java        日志切面
│       │   │   ├── common/      Result / PageResult / ResultCode
│       │   │   │                BusinessException / GlobalExceptionHandler
│       │   │   │                Constants
│       │   │   ├── config/      SecurityConfig / MybatisPlusConfig
│       │   │   │                PasswordConfig / TicketProperties
│       │   │   │                DataInitializer / Knife4jConfig
│       │   │   │                MyMetaObjectHandler
│       │   │   ├── controller/  Auth / Ticket / Approval / Notice
│       │   │   │                Dashboard / User / Common / Debug
│       │   │   ├── dto/         请求参数对象（9 个）
│       │   │   ├── entity/      数据库实体（12 个，与 12 张表对应）
│       │   │   ├── enums/       TicketStatus / TicketAction
│       │   │   │                TicketType / ApprovalStatus
│       │   │   ├── mapper/      MyBatis Mapper（12 个）
│       │   │   ├── security/    LoginUser / SecurityUtils
│       │   │   │                JwtAuthenticationFilter
│       │   │   ├── service/     7 个业务服务
│       │   │   ├── task/        TicketSlaTask.java        定时任务
│       │   │   ├── util/        JwtUtils.java
│       │   │   └── vo/          LoginVO / TicketDetailVO / DashboardVO
│       │   └── resources/
│       │       ├── application.yml
│       │       └── mapper/                            ⚠️ 空目录
│       └── test/java/com/taskflow/                ⚠️ 仅 2 个测试类
│           ├── enums/TicketStatusMachineTest.java
│           └── service/AuthServiceTest.java
│
├── frontend/                                 Vue3 前端
│   ├── package.json / vite.config.js
│   └── src/
│       ├── main.js / App.vue
│       ├── api/index.js                          138 行，全部接口封装
│       ├── router/index.js                       路由 + 菜单权限过滤
│       ├── store/user.js                         Pinia：用户 + 权限
│       ├── utils/request.js                      Axios 拦截器
│       ├── utils/auth.js                         token 存取
│       ├── directives/permission.js              v-has-perm 指令
│       ├── layout/MainLayout.vue                 侧边栏 + 顶栏
│       └── views/                                12 个页面组件
│
├── sql/taskflow.sql                            建表 + 初始化数据
└── docs/
    ├── 搭建教程.md
    ├── 面试问答.md
    └── 环境配置指引.md
```

**代码规模**：后端 75 个 Java 文件，前端 22 个 js/vue 文件。

| 最大的后端文件 | 行数 | 最大的前端文件 | 行数 |
|---------------|:----:|---------------|:----:|
| `service/TicketService.java` | 527 | `views/approval/List.vue` | 510 |
| `service/ApprovalService.java` | 291 | `views/ticket/Detail.vue` | 488 |
| `service/UserService.java` | 219 | `views/ticket/List.vue` | 447 |
| `aspect/OperLogAspect.java` | 192 | `views/Dashboard.vue` | 343 |
| `util/JwtUtils.java` | 185 | `views/system/User.vue` | 282 |

### 1.4 模块职责与依赖关系

**分层依赖（严格单向，禁止反向）**

```
Controller  ──依赖──▶  Service  ──依赖──▶  Mapper  ──依赖──▶  MySQL
    │                      │
    │                      └──依赖──▶  Entity / DTO / VO
    └──依赖──▶  DTO / VO / 注解
```

**安全模块的横向切面**：`JwtAuthenticationFilter` 挂在 Spring Security 过滤链上，
所有请求都会经过它，是 Controller 之前的第一道关卡。

**模块职责表**

| 包 | 职责 | 关键类 | 依赖方向 |
|----|------|--------|---------|
| `common` | 统一响应、错误码、异常、全局异常处理 | `Result` / `ResultCode` / `GlobalExceptionHandler` | 不依赖业务层 |
| `config` | 安全配置、MyBatis 配置、属性绑定、初始化 | `SecurityConfig` / `DataInitializer` | 依赖 security / entity |
| `security` | JWT 解析、认证上下文、权限工具 | `JwtAuthenticationFilter` / `SecurityUtils` | 依赖 common / entity |
| `controller` | HTTP 入口、参数校验、权限注解 | 8 个 Controller | 依赖 service / dto / vo |
| `service` | 业务逻辑、事务边界、权限校验 | 7 个 Service | 依赖 mapper / entity / dto |
| `mapper` | 数据访问 | 12 个 Mapper | 仅依赖 entity |
| `entity` | 数据库表映射 | 12 个实体 | 无业务依赖 |
| `dto` / `vo` | 入参 / 出参，隔离数据模型 | 12 个对象 | 依赖 entity（VO 部分） |
| `enums` | 状态机与业务枚举 | `TicketStatus` / `TicketAction` | 无依赖 |
| `task` | 定时任务 | `TicketSlaTask` | 依赖 service / mapper |
| `util` | JWT 工具 | `JwtUtils` | 依赖 common |

**权限标识全集**（来自 `sql/taskflow.sql`，共 16 条）

| 类型 | 标识 | 说明 | ADMIN | MANAGER | USER |
|------|------|------|:---:|:---:|:---:|
| 目录 | `dashboard` | 数据看板 | ✅ | ✅ | ✅ |
| 目录 | `ticket` | 工单中心 | ✅ | ✅ | ✅ |
| 目录 | `approval` | 审批中心 | ✅ | ✅ | ✅ |
| 目录 | `notice` | 通知中心 | ✅ | ✅ | ✅ |
| 目录 | `system` | 系统管理 | ✅ | ❌ | ❌ |
| 功能 | `ticket:list` | 查看工单 | ✅ | ✅ | ✅ |
| 功能 | `ticket:add` | 创建工单 | ✅ | ✅ | ✅ |
| 功能 | `ticket:handle` | 受理/转派/关闭工单 | ✅ | ✅ | ❌ |
| 功能 | `ticket:delete` | 删除工单 | ✅ | ❌ | ❌ |
| 功能 | `approval:list` | 查看审批单 | ✅ | ✅ | ✅ |
| 功能 | `approval:submit` | **发起**审批 | ✅ | ✅ | ✅ |
| 功能 | `approval:audit` | **审核**审批 | ✅ | ✅ | ❌ |
| 功能 | `notice:list` | 查看通知 | ✅ | ✅ | ✅ |
| 功能 | `system:user` | 用户管理 | ✅ | ❌ | ❌ |
| 功能 | `system:role` | 角色管理 | ✅ | ❌ | ❌ |
| 功能 | `system:log` | 操作日志 | ✅ | ❌ | ❌ |

> 目录类标识（`dashboard`/`ticket`/`approval`/`notice`/`system`）仅用于兼容前端菜单树结构，
> 真正的功能控制由 `xxx:yyy` 形式的具体功能权限承担。
> 角色权限的分配逻辑写在 `sql/taskflow.sql` 中（按 `perm_code` 子查询，可读且防写错列数）。

> **循环依赖的处理是本项目的一个真实教训**：`PasswordEncoder` 曾定义在 `SecurityConfig` 里，
> 形成 `SecurityConfig → JwtAuthenticationFilter → AuthService → PasswordEncoder → SecurityConfig`
> 的闭环，Spring Boot 2.6+ 默认禁止。修复方式是拆出独立的 `config/PasswordConfig.java`，
> 并给过滤器的 `AuthService` 依赖加 `@Lazy`。

---

## 二、核心业务流程

### 2.1 登录认证链路

```
[前端] views/Login.vue
   │ POST /api/auth/login  {username, password}
   ▼
[后端] controller/AuthController.login()
   │
   ▼
service/AuthService.login()
   ├─ ① userMapper.selectOne(username)          查用户
   ├─ ② passwordEncoder.matches(明文, BCrypt密文)  密码比对
   ├─ ③ 校验 status == 1（账号未禁用）
   ├─ ④ 更新 last_login_at
   └─ ⑤ buildLoginVO(user, jwtUtils.generateToken())
          │
          ├─ 查角色：selectRoleCodesByUserId()
          ├─ 查权限：selectPermCodesByUserId()
          │   （ADMIN 直接给 selectAllMenus() 的全部权限）
          ├─ 查菜单：permissionMapper.selectMenuByUserId()
          └─ 查未读通知数
   ▼
返回 LoginVO {token, tokenPrefix, userId, username, nickname, dept,
              roles[], permissions[], menus[], unreadCount}
   │
   ▼
[前端] utils/auth.js 存 token 到 localStorage
        store/user.js 保存 roles / permissions / userInfo
        router/index.js 跳转 /dashboard
```

**关键实现细节**

- **密码比对**：`passwordEncoder.matches()`（BCrypt 自带随机盐，同一密码每次密文不同）
- **明文密码升级**：`config/DataInitializer.java` 启动时把 5 个演示账号的明文密码
  批量替换为 BCrypt 密文，兼容老数据
- **登录失败不区分"用户不存在"和"密码错误"**，统一抛 `ResultCode.LOGIN_FAILED`，
  避免用户名枚举攻击

> ⚠️ **缺失**：无登录失败次数限制 / 验证码 / 账号锁定机制。

### 2.2 令牌鉴权链路

```
[请求] 任意 /api/** 请求
   │  Authorization: Bearer <token>
   ▼
security/JwtAuthenticationFilter.doFilterInternal()
   ├─ ① resolveToken(request)               从请求头取 Bearer 令牌
   ├─ ② jwtUtils.getUserId(token)            校验签名 + 有效期，解析 userId
   ├─ ③ authService.loadLoginUserById(userId) ★重新查库加载最新权限
   ├─ ④ 构造 UsernamePasswordAuthenticationToken
   └─ ⑤ 放入 SecurityContextHolder
   ▼
[后续] SecurityConfig 过滤链 → Controller
   │
   ├─ @PreAuthorize("hasAuthority('xxx')")   功能权限
   └─ Service 层 checkActionPermission()     数据权限
```

**第③步为什么每次请求都查库？**
保证「管理员改了角色，被改用户下一次请求立即生效」。代价是每次请求多一次用户查询 +
角色权限查询（详见 [4.3 潜在风险](#43-潜在风险与已知缺陷)）。

**异常处理**：`JwtAuthenticationFilter` 内部 `catch` 后直接放行（不中断链路），
由后续的 `@PreAuthorize` 或 `SecurityContextHolder` 为空来触发 401/403。

### 2.3 工单全生命周期

**状态机流转图**（定义在 `enums/TicketStatus.java`）

```
                  ┌──────────┐
                  │ 待受理 0 │  ← 创建
                  └────┬─────┘
              ACCEPT │  (需 ticket:handle)
                       ▼
                 ┌──────────┐
      TRANSFER →│ 处理中 1 │←──┐
      SUBMIT    └────┬─────┘   │
      REJECT         │ SUBMIT  │ REJECT
                       ▼         │
                 ┌──────────┐   │
      TRANSFER →│ 待确认 2 │───┘
      REJECT    └────┬─────┘
              CONFIRM (仅提单人)
                       ▼
                 ┌──────────┐
      CLOSE     │ 已完成 3 │
      REOPEN    └────┬─────┘
                       │ REOPEN
                 ┌──────────┐
                 │ 已关闭 4 │ 终态
                 └──────────┘

                 ┌──────────┐
                 │ 已驳回 5 │──REOPEN──▶ 可重开
                 └──────────┘
```

**单一动作入口**：`TicketService.handleAction(ticketId, dto)`
（文件 `service/TicketService.java`，约 213 行起）

```java
① 状态机校验  currentStatus.canDo(action.getCode())
② 数据权限校验 checkActionPermission(ticket, action)
③ 更新工单    状态迁移 + 动作相关的字段变更
④ 记录轨迹    saveFlow(ticketId, from, to, action, remark)
⑤ 发送通知    通知提单人 + 动作专属通知
```

**各动作的字段副作用**

| 动作 | 状态变更 | 额外字段变更 | 通知对象 |
|------|---------|-------------|---------|
| `ACCEPT` | 0→1 | `assignee_id = 当前用户` | 提单人 |
| `TRANSFER` | 不变 | `assignee_id = dto.assigneeId` | 新处理人 + 提单人 |
| `SUBMIT` | 1→2 | — | 提单人 |
| `REJECT` | 1→2 / 2→5 | `assignee_id = creator_id`（退回提单人） | 提单人 |
| `CONFIRM` | 2→3 | `finish_at = now()` | 处理人 |
| `CLOSE` | 3→4 | — | 提单人 |
| `REOPEN` | 5→3 | `finish_at=null, is_timeout=0` | 提单人 |

**创建链路**：`TicketService.create()`
→ 设置初始状态 `PENDING` → 计算 SLA 截止时间 `calcDeadline(priority)`
→ （管理岗）`resolveInitialAssignee()` 指定处理人 → 写轨迹 → 通知有 `ticket:handle` 权限的人

### 2.4 审批流链路

```
[发起] ApprovalService.submit()
   ├─ 校验：审批人非空、不含自己、目标用户存在且启用
   ├─ 插入 ap_approval（current_node=1, total_node=N, status=0 待审批）
   ├─ 按选择顺序插入 N 条 ap_approval_node（node_index 1..N, status=0）
   └─ 通知第 1 级审批人
   ▼
[审批] ApprovalService.audit()
   ├─ 查 ap_approval，校验 status == PENDING
   ├─ 定位当前节点：node_index = approval.current_node AND status = 0
   ├─ ★数据权限：node.approver_id == 当前用户，否则 APPROVAL_NO_PERMISSION
   ├─ 更新节点 status（1=通过 / 2=驳回）+ comment + approve_time
   ├─ 通过且 current_node == total_node → 整单 status = APPROVED
   ├─ 通过且还有下一节点 → approval.current_node++
   └─ 驳回 → 整单 status = REJECTED
   ▼
[取消] ApprovalService.cancel() —— 仅申请人可取消待审批单
```

**审批链的数据结构**：`ap_approval_node` 用 `node_index` 表达串行顺序，
`approver_id` 表达每个节点的审批人，`status`（0待处理/1通过/2驳回）表达节点状态。
主表 `ap_approval` 用 `current_node` 记录当前走到第几级。

**跨角色审批**：manager 发起审批指定员工审批时，审批人是普通员工，
他**没有** `approval:audit` 权限。因此 `ApprovalController.audit()` 的 `@PreAuthorize`
用的是 `approval:list`（所有有审批功能的角色都有），
真正的拦截在 Service 层比对 `node.approver_id`。

> 这一点是本项目**最容易被面试追问**的设计点，详见 [3.3](#33-三层权限体系)。

### 2.5 看板统计链路

`DashboardService.getDashboard()`（103 行）聚合 4 类数据：

| 数据来源 | Mapper 方法 | 说明 |
|---------|------------|------|
| 工单汇总 | `ticketMapper.selectSummary()` | 总数/处理中/完成率/超时数等 |
| 状态分布 | `ticketMapper.countGroupByStatus()` | 各状态工单数 |
| 类型分布 | `ticketMapper.countGroupByType()` | 各类型工单数 |
| 优先级分布 | `ticketMapper.countGroupByPriority()` | 各优先级工单数 |

前端 `views/Dashboard.vue`（343 行）用 ECharts 渲染 4 张图 + 8 个统计卡片。

### 2.6 操作日志链路

```
Controller 方法标注 @OperLog(title="...", businessType=...)
   ▼
aspect/OperLogAspect.around()         @Around 环绕通知
   ├─ 记录开始时间
   ├─ 执行业务方法（proceed()）
   ├─ 捕获返回值 → objectMapper.writeValueAsString()（过滤 MultipartFile）
   ├─ 捕获异常 → 记录异常信息，不影响主流程
   ├─ 计算耗时
   └─ 异步写入 sys_oper_log
```

**关键设计**：日志写入失败**绝不影响主流程**（整体 try-catch），
参数序列化过滤 `MultipartFile` / Servlet 相关对象避免序列化异常。

---

## 三、重点代码模块解析

### 3.1 工单状态机（核心）

**文件**：`backend/src/main/java/com/taskflow/enums/TicketStatus.java`

**设计意图**：把「当前状态 → 允许哪些动作」的映射**集中声明**，
而不是散落在 Service 的 if-else 中。收益：新增状态只改一处，非法流转天然被拦截。

**数据结构**

```java
public enum TicketStatus {
    PENDING(0, "待受理", false, "ACCEPT", "CLOSE"),
    PROCESSING(1, "处理中", false, "TRANSFER", "SUBMIT", "REJECT"),
    CONFIRMING(2, "待确认", false, "CONFIRM", "REJECT", "TRANSFER"),
    FINISHED(3, "已完成", false, "CLOSE", "REOPEN"),
    CLOSED(4, "已关闭", true),          // 终态
    REJECTED(5, "已驳回", false, "REOPEN");

    private final Integer code;          // 状态码，存数据库
    private final String desc;           // 中文名
    private final boolean finalState;    // 是否终态
    private final Set<String> allowedActions;  // 允许的动作码集合
}
```

**三个值得讲的技术点**

1. **动作码用字符串字面量而非 `TicketAction` 枚举常量**
   注释明确说明：`TicketAction` 反过来依赖 `TicketStatus` 定义目标状态，
   若互相引用常量，JVM 类初始化时会出现循环依赖，可能拿到 `null` 导致状态机判断失效。
   这是一个真实踩过的坑。

2. **`allowedActions` 用不可变集合**
   `Collections.unmodifiableSet(new HashSet<>(...))`，防止运行时被意外修改。

3. **`canDo()` 同时判断终态和动作白名单**
   ```java
   public boolean canDo(String actionCode) {
       return !finalState && allowedActions.contains(actionCode);
   }
   ```

**测试覆盖**：`src/test/java/com/taskflow/enums/TicketStatusMachineTest.java`
用 `@ParameterizedTest` + `@CsvSource` 覆盖状态码映射、正常路径、非法流转，
还包含"遍历所有状态 × 所有动作，验证白名单与实际流转一致"的完备性检查。

### 3.2 工单流转引擎

**文件**：`backend/src/main/java/com/taskflow/service/TicketService.java`（527 行，本项目最大类）

**方法清单**

| 方法 | 职责 | 事务 |
|------|------|:---:|
| `create(TicketDTO)` | 创建工单，含处理人指定校验 | ❌ |
| `page(TicketQueryDTO)` | 分页查询，含数据权限收敛 | ❌ |
| `detail(Long)` | 详情 + 轨迹 + 评论 | ❌ |
| `handleAction(Long, TicketActionDTO)` | **状态机唯一入口** | ✅ |
| `addComment(...)` | 添加评论 | ✅ |
| `checkActionPermission(...)` | 数据权限校验（private） | — |
| `resolveInitialAssignee(Long)` | 处理人指定校验（private） | — |
| `calcDeadline(Integer)` | SLA 截止时间计算 | — |
| `saveFlow(...)` | 记录流转轨迹 | — |

**`handleAction` 的五步结构**（这是最值得背的代码）

```java
① 状态机校验   if (!currentStatus.canDo(action.getCode())) throw ...
② 数据权限校验 checkActionPermission(ticket, action)
③ 更新工单     状态迁移 + 动作副作用
④ 记录轨迹     saveFlow(...)   ← 保证"状态改了但没日志"不会发生
⑤ 发送通知     通知提单人 + 动作专属通知
```

**把所有状态变更收敛到一个入口**是关键设计：
无论从哪个业务场景触发流转（列表页操作、详情页操作、定时任务），
都会留下完整轨迹，不会出现"状态改了但没日志"的情况。

**`checkActionPermission` 的权限矩阵**

| 动作 | 允许者 |
|------|--------|
| `ACCEPT` | 管理员 或 有 `ticket:handle` 权限 |
| `SUBMIT` / `REJECT` | 管理员 或 当前处理人 |
| `CONFIRM` | 管理员 或 提单人 |
| `TRANSFER` / `CLOSE` / `REOPEN` | 管理员 或 有 `ticket:handle` 权限 |

### 3.3 三层权限体系

**这是本项目最核心的设计，也是面试的高频考点。**

| 层级 | 问题 | 实现 | 代码位置 |
|------|------|------|---------|
| **① 功能权限** | 这个角色**允许不允许**做这个操作？ | `@PreAuthorize("hasAuthority('xxx')")` | Controller 方法/类 |
| **② 数据权限** | 这个角色**能看到/能操作**哪些行？ | Service 层按角色能力收敛 | `TicketService.page()` / `checkActionPermission()` |
| **③ 业务权限** | **这条**数据是不是你的？ | Service 层比对字段归属 | `ApprovalService.audit()` 比对 `node.approver_id` |

**关键区分（第 2 层 vs 第 3 层）**

第 2 层的判断依据是**角色能力**，第 3 层是**数据归属**。
举例说明审批：

```java
// 第 2 层：能不能进这个接口 —— approval:list，所有有审批功能的角色都有
@PreAuthorize("hasAuthority('approval:list') or hasRole('ADMIN')")

// 第 3 层：能不能审这一张单 —— 必须是这单当前节点的审批人
BusinessException.throwIf(!currentNode.getApproverId().equals(userId),
        ResultCode.APPROVAL_NO_PERMISSION);
```

> 如果第 3 层写成 `hasAuthority('approval:audit')`，
> 那么「manager 发起审批指定员工审批」的场景会直接 403 卡死 ——
> 因为员工不可能有管理岗权限。**审批权是数据权限，不是功能权限。**

**数据权限的判断依据刻意脱离"是不是 admin"**

```java
boolean canHandleAll = SecurityUtils.isAdmin()
        || SecurityUtils.hasPermission("ticket:handle");
```

用「有没有处理职能」而非「是不是管理员」判断，
新增「客服」这类角色时只要配好权限就自动生效，不用改代码。

**`SecurityUtils` 工具类**（`security/SecurityUtils.java`）
静态方法从 `SecurityContextHolder` 取当前用户，避免到处传 `userId`：

| 方法 | 行为 |
|------|------|
| `getLoginUser()` | 未登录抛 401 |
| `getLoginUserOrNull()` | 未登录返回 null |
| `getUserId()` / `getUserIdOrNull()` | 同上 |
| `getPermissions()` | 返回权限标识集合 |
| `isAdmin()` | 判断是否含 `ADMIN` 角色 |
| `hasPermission(String)` | 判断是否拥有某权限标识 |

### 3.4 JWT 无状态认证

**文件**：`util/JwtUtils.java`（185 行）

**关键实现**

| 能力 | 实现 |
|------|------|
| 令牌结构 | `header.payload.signature`，HS256 |
| 载荷 | `userId` / `username` / `iat` / `exp` |
| 密钥管理 | **从配置 + 固定盐派生**（非明文硬编码），`volatile` + 双重检查锁定 |
| 有效期 | 可配置（`taskflow.jwt.*`） |
| 解析失败 | 返回 null 而非抛异常，由过滤器决定是否放行 |

**关于 `volatile` + 双重检查锁定**（代码注释明确说明）：
签名密钥是**懒加载**的，多线程并发调用时若不加 `volatile`，
可能出现「多个线程同时看到 null，各自创建一份密钥」的情况，浪费且难以排查。

**已知设计缺陷**（仓库自己也在 `面试问答.md` Q14 承认）：
密钥由配置 + 固定盐派生，**没有真正的密钥轮换机制**。
生产环境应改用 KMS 或环境变量注入。

### 3.5 审批链数据模型

**表结构**（`sql/taskflow.sql`）

```sql
ap_approval        -- 审批单主表
  approval_no      单号（唯一索引）
  applicant_id     申请人
  current_node     当前节点序号（1-based）
  total_node       节点总数
  status           0待审批 1通过 2驳回

ap_approval_node   -- 审批链节点表
  approval_id      所属审批单
  node_index       节点序号（1-based，串行顺序）
  approver_id      该节点的审批人
  status           0待处理 1通过 2驳回
  comment          审批意见
  approve_time     审批时间
```

**设计取舍**：采用**预生成全部节点**的方案，而不是动态创建。
优点：审批链结构在发起时就确定、可预览、便于展示"第 2/3 级审批"；
缺点：审批人中途变更需要额外处理（当前版本不支持改单）。

**待办查询 SQL**（`ApApprovalMapper`）通过 JOIN `ap_approval_node` 且
`n.approver_id = #{userId}` 实现"谁该审就看到谁的单"。

### 3.6 统一响应与全局异常

**`common/Result<T>`**：统一返回 `{ code, msg, data, timestamp }`

**`common/ResultCode`**：错误码集中定义，示例：

| 码 | 含义 |
|---|---|
| 200 | 成功 |
| 400 / 4002 / 4003 | 业务异常（状态错误 / 已结束 / 非审批人） |
| 401 / 1003 / 1004 | 未登录 / 令牌失效 |
| 403 | 无访问权限 |

**`GlobalExceptionHandler`**（119 行）用 `@RestControllerAdvice` 统一处理：

| 异常 | 处理 |
|------|------|
| `BusinessException` | 返回业务码 + 业务提示 |
| `MethodArgumentNotValidException` | 提取字段校验信息 |
| `AccessDeniedException` | 403 |
| 其他 `Exception` | 500 + "系统繁忙，请稍后重试"（不暴露堆栈） |

> **安全点**：500 不把异常堆栈返回给前端，只记服务端日志。

### 3.7 AOP 操作日志

**文件**：`aspect/OperLogAspect.java`（192 行）

```java
@Around("@annotation(operLog)")     // 切点：所有标注 @OperLog 的方法
public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) {
    long start = System.currentTimeMillis();
    SysOperLog entity = new SysOperLog();
    entity.setTitle(operLog.title());
    entity.setBusinessType(operLog.businessType().name());
    ...
    try {
        Object result = joinPoint.proceed();     // 执行业务
        entity.setStatus(1);                     // 成功
        entity.setResult(serialize(result));     // 序列化返回值
        return result;
    } catch (Throwable e) {
        entity.setStatus(0);                     // 失败
        entity.setErrorMsg(e.getMessage());
        throw e;                                 // 原样抛出，不吞异常
    } finally {
        entity.setCostTime(System.currentTimeMillis() - start);
        saveLog(entity);                         // 写库
    }
}
```

**三个工程细节**

1. **参数序列化过滤**：`MultipartFile` / Servlet 相关对象无法序列化，直接放进去会抛异常
2. **日志写入失败不影响主流程**：整体 try-catch
3. **异常原样抛出**：`throw e` 保证业务异常不被切面吞掉

### 3.8 前端路由与菜单

**文件**：`frontend/src/router/index.js`（161 行）

**设计**：`businessRoutes` 导出业务页面，**直接作为 MainLayout 的 children 静态注册**，
菜单显示与否由 `getMenuRoutes(userRoles, userPermissions)` 过滤。

```js
export function getMenuRoutes(userRoles, userPermissions) {
  const isAdmin = userRoles?.includes('ADMIN')
  return businessRoutes
    .filter((r) => !r.meta.hidden)                              // 排除内部路由
    .filter((r) => {
      const perms = r.meta.permissions
      if (!perms || !perms.length) return true    // 无要求：所有人可见
      if (isAdmin) return true                    // 超管全放行
      return perms.some((p) => userPermissions?.includes(p))
    })
}
```

**为什么不用 `router.addRoute()` 动态注册？**
动态注册要求每层嵌套时路径拼接容易出错，且刷新页面后动态路由会丢失必须重算。
本项目规模下静态注册 + 菜单按权限过滤更稳妥 —— **真正的安全边界在后端接口鉴权**，
前端隐藏菜单只是体验优化。

**路由守卫**（`router.beforeEach`）：未登录跳 `/login?redirect=xxx`、
已登录访问 `/login` 踢回 `/dashboard`、动态设置 `document.title`。

**权限指令**（`directives/permission.js`）：
`v-has-perm="'system:user'"` 在 `mounted` 时检查权限，无权限则**直接从 DOM 移除**元素
（比 `v-if` 隐藏更彻底）。

### 3.9 SLA 超时扫描定时任务

**文件**：`task/TicketSlaTask.java`（90 行）

| 任务 | cron | 作用 |
|------|------|------|
| `scanTimeoutTickets()` | `0 5 * * * ?` | 每小时第 5 分钟扫描超时工单，标记 `is_timeout=1` 并通知处理人 |
| `dailyReport()` | `0 0 2 * * ?` | 每天凌晨 2 点统计昨日工单情况 |

**四个工程细节**（代码注释明确列出）

1. `@Scheduled` 声明式定时，Spring 自动管理线程池
2. `initialDelay` 让首次执行延迟，避开应用启动高峰
3. 批量处理前先判断 `is_timeout=0`，避免重复发通知
4. **任务内部整体 try-catch** —— 保证一次异常不会导致后续调度被取消

> ⚠️ `dailyReport()` 目前只 `log.info` 到日志，**没有真正把日报发给管理员**，
> 注释里写的"给管理员发日报"尚未实现。

---

## 四、关键设计决策

### 4.1 决策清单

| # | 决策 | 备选方案 | 选择理由 | 代价 |
|---|------|---------|---------|------|
| 1 | **状态机用枚举集中声明** | Service 里 if-else 判断 | 新增状态只改一处；非法流转天然拦截；可单元测试 | 枚举与动作互相引用会 JVM 初始化循环依赖，需用字符串字面量 |
| 2 | **所有状态变更收敛到 `handleAction` 单一入口** | 各业务方法各自改状态 | 保证每次流转都有轨迹记录，不出现"状态改了但没日志" | 单一方法较长（~90 行），可拆分为策略模式 |
| 3 | **权限分三层**（功能/数据/业务） | 只做功能权限 | 功能权限答"能不能做"，数据权限答"能操作哪些行"，缺一不可 | 三层判定散落多处，需统一约定 |
| 4 | **数据权限依据「职能权限」而非「是否 admin」** | 硬编码角色判断 | 新增角色配好权限即生效，不用改代码 | 需要维护权限标识与角色的对应关系 |
| 5 | **业务接口与管理接口分离**（`/common/users` vs `/system/user/list`） | 复用同一接口加权限 | 管理场景需 `system:user`；业务场景（选审批人）是全员日常 | 增加了接口数量 |
| 6 | **审批接口用 `approval:list` 门槛 + 业务层判归属** | 用 `approval:audit` 门槛 | 审批权是数据权限；跨角色审批场景下员工无 `approval:audit` | 接口入口看似放宽，需靠业务层保证安全 |
| 7 | **每次请求重新查库加载权限** | 权限写进 JWT | 管理员改权限后立即生效 | 每请求多 2~3 次查询（见风险） |
| 8 | **静态注册路由 + 菜单按权限过滤** | `addRoute` 动态注册 | 避免路径拼接错误与刷新丢路由 | 路由表对所有人可见（前端不构成安全边界） |
| 9 | **Mapper 用注解 SQL 而非 XML** | XML 文件 | 类型安全、IDE 提示好、文件少 | 复杂动态 SQL 可读性下降 |
| 10 | **逻辑删除而非物理删除** | 物理删除 | 保留审计痕迹，符合合规要求 | 需要在所有查询加 `deleted=0`，漏加会出脏数据 |
| 11 | **BCrypt 密码 + 启动时自动升级明文** | 明文 / MD5 | BCrypt 自带随机盐 + 可调计算成本 | 登录时需一次 bcrypt 比对（较慢） |
| 12 | **操作日志用 AOP 而非手动调用** | 每个方法手写 | 无侵入，新增接口加注解即可 | 需注意 `proceed()` 异常处理与参数序列化 |
| 13 | **`PasswordEncoder` 拆独立配置类** | 放 `SecurityConfig` | 打断循环依赖，Spring Boot 2.6+ 必需 | 多一个类 |

### 4.2 扩展点

| 扩展需求 | 扩展位置 | 改动量 |
|---------|---------|:-----:|
| 新增工单状态 | `enums/TicketStatus` 加枚举项 | 小 |
| 新增工单动作 | `enums/TicketAction` + `TicketStatus.allowedActions` + `checkActionPermission` | 中 |
| 新增业务模块 | 新建 controller/service/mapper + entity | 中 |
| 新增角色 | `sys_role` + `sys_role_permission` 配数据即可，**无需改代码** | 小 |
| 新增定时任务 | `task/` 包下加 `@Component` | 小 |
| 新增审批节点类型（会签/或签） | `ApprovalService.audit()` 的节点推进逻辑 + `ap_approval_node` 加字段 | 大 |
| 接入其他通知渠道 | `NoticeService.send()` 内部分支 | 中 |
| 换数据库 | `application.yml` 数据源 + `MybatisPlusConfig` 分页插件 | 中 |

### 4.3 潜在风险与已知缺陷

**⚠️ 以下均为代码中确实存在的问题，未做粉饰**

| # | 风险 | 影响 | 位置 | 建议 |
|---|------|------|------|------|
| 1 | **每请求 3 次权限查询** | 高并发下数据库压力大 | `AuthService.loadLoginUserById()` | 加 Redis 缓存（权限变更时主动失效） |
| 2 | **JWT 无密钥轮换机制** | 密钥泄露无法快速止损 | `util/JwtUtils.java` | 改用环境变量 / KMS 注入，支持双密钥并存期 |
| 3 | **登录无失败次数限制** | 可暴力破解 | `AuthService.login()` | 加 Redis 计数 + 账号锁定 |
| 4 | **无接口限流** | 可被刷爆 | 全局 | Redis 令牌桶 / Sentinel |
| 5 | **审批/工单无幂等控制** | 重复提交会产生重复单据 | `ApprovalService.submit()` / `TicketService.create()` | 加请求幂等号（前端生成 UUID）+ 唯一索引 |
| 6 | **逻辑删除字段易漏加条件** | 漏加会查出已删数据 | 所有查询 | 用 MyBatis-Plus 全局逻辑删除配置替代手写 |
| 7 | **`TicketService` 527 行偏大** | 可维护性下降 | `service/TicketService.java` | 拆成 `TicketCommandService` / `TicketQueryService` / `TicketFlowRecorder` |
| 8 | **`checkActionPermission` 用 switch** | 新增动作易漏 | 同上 | 改为在 `TicketAction` 枚举中声明所需权限 |
| 9 | **SLA 日报未真正发送** | 功能未完成 | `task/TicketSlaTask.dailyReport()` | 调用 `NoticeService` 发给管理员 |
| 10 | **`/debug/**` 在安全白名单内** | 生产环境泄露权限结构 | `config/SecurityConfig.WHITE_LIST` | 生产环境移除或加 `@Profile("dev")` |
| 11 | **`mapper/` 资源目录为空** | 冗余 | `backend/src/main/resources/mapper/` | 删除或补 XML |
| 12 | **无分页深翻页优化** | 10 万级数据 OFFSET 慢 | `TicketService.page()` | 大数据量改游标分页/搜索引擎 |
| 13 | **工单表无软删除唯一性约束** | 逻辑删除后可重复建单号 | `wf_ticket.ticket_no` | 已加唯一索引，但逻辑删除后需重建 |

---

## 五、面试问答准备

### Q1：介绍一下你的项目

> **答题结构（2 分钟版）**：
>
> TaskFlow 是一个企业级工单流转与审批管理系统，基于 Spring Boot 3.2.5 + Vue3。
> 它覆盖两条业务链路：**工单流转**（提交→受理→处理→验收→归档，带轨迹和 SLA 监控）
> 和**审批流**（发起→多级串行审批）。
>
> 技术上：Spring Security + JWT 做无状态认证，MyBatis-Plus 持久化，
> 12 张表覆盖用户/角色/权限/工单/审批/通知/日志。
>
> 我认为最有价值的三个设计：
> 1. **状态机集中声明**在枚举里，6 个状态 × 7 个动作的合法流转一目了然，非法流转天然被拦截，还配了单元测试；
> 2. **权限分三层**（功能权限用注解、数据权限看角色能力、业务权限看数据归属），避免把"能不能做"和"能操作哪些"搞混；
> 3. **所有状态变更收敛到单一入口**，保证每次流转都有轨迹记录。
>
> 性能上目前是单机部署、索引齐全的状态，如果量级上来我会先给权限查询加 Redis 缓存。

### Q2：为什么用状态机？直接 if-else 不行吗？

> if-else 当然能写，但有三个问题：
>
> 1. **流转规则会散落各处**。比如"处理中才能提交"这条规则，
>    写在 Service 的 update 里、写在定时任务里、写在批量导入里，
>    总有一天会漏掉一处。
> 2. **非法流转难枚举**。6 个状态 × 7 个动作有 42 种组合，人工点页面根本测不全。
>    我写了 `TicketStatusMachineTest`，用参数化测试把合法/非法组合全列出来，
>    任何一次改动导致非法流转被放行，测试立刻失败。
> 3. **新增状态成本高**。加一个状态要改 N 处判断，改漏一处就是线上事故。
>
> 集中在枚举里之后，新增状态只改一处。
>
> **有个坑值得提**：状态机里我用的是**动作码字符串字面量**而不是 `TicketAction` 枚举常量，
> 因为两者互相依赖会导致 JVM 类初始化循环依赖，可能拿到 null 让状态机判断失效。
> 这是我实际踩过的。

### Q3：JWT 认证的完整流程？

> 登录时：查用户 → BCrypt 比对密码 → 校验账号状态 → 生成 JWT（载荷含 userId/username/iat/exp）
> → 同时把角色、权限、菜单、未读数一起返回给前端。
>
> 之后每次请求：`JwtAuthenticationFilter` 从 `Authorization` 头取 Bearer 令牌
> → 校验签名和有效期 → 解析出 userId → **重新查库**加载最新的角色权限
> → 构造 `UsernamePasswordAuthenticationToken` 放进 `SecurityContextHolder`
> → 后续 `@PreAuthorize` 和 Service 层就能拿到身份做鉴权。
>
> **为什么每次都查库而不用 JWT 里带权限？**
> 因为要保证"管理员改了角色，被改用户下一次请求立即生效"。
> 代价是每请求多 2~3 次查询 —— 这是我明确知道的性能瓶颈，
> 如果量级上来我会用 Redis 缓存权限，角色变更时主动失效。

### Q4：权限校验为什么前后端都要做？

> 前端做的是**体验**，后端做的是**安全**。
>
> 前端隐藏菜单只是让用户少看到几个按钮，他改一下前端代码或者直接调接口就绕过去了。
> 后端才是真正的边界。
>
> 我项目里的分工是：前端用 `v-has-perm` 指令**直接从 DOM 移除**无权限的按钮，
> 后端每个 Controller 用 `@PreAuthorize` 拦功能权限，Service 层做数据权限和业务权限。
>
> 有一次真实的教训：普通员工发起审批时审批人下拉框显示"无数据"，
> 原因是前端调了 `/system/user/list`（用户管理接口，需要 `system:user` 权限）。
> 我最后拆了两个接口：管理场景用受保护的那个，业务场景用 `/common/users`（登录即可，
> 只返回 id/昵称/部门这类低敏感字段）。

### Q5：接口鉴权怎么实现的？权限粒度到哪一层？

> 用 Spring Security 的 `@PreAuthorize("hasAuthority('xxx')")`，
> 基于 `LoginUser` 里的权限标识集合（来自 RBAC 五张表）。
>
> 粒度分三层：
> - **功能权限**：能不能进这个接口
> - **数据权限**：进来后能看到哪些行 —— 比如普通员工只能看"我创建的 + 指派给我的"
> - **业务权限**：这条数据是不是你的 —— 比如只能受理指派给自己的工单
>
> 关键是后两层**不能靠注解**，因为注解只能表达角色维度，表达不了行维度。
> 我举一个最有说服力的例子：**审批权是数据权限，不是功能权限**。
>
> manager 发起审批指定员工审批时，审批人是普通员工，他不可能有 `approval:audit` 权限。
> 如果接口用 `hasAuthority('approval:audit')` 拦，跨角色审批就彻底卡死。
> 所以我的做法是：接口门槛降到 `approval:list`，
> 真正的拦截放在 Service 层比对 `ap_approval_node.approver_id == 当前用户`。
>
> **安全性没有降低** —— 我实测过：不是审批人的调用依然被业务层拒绝，
> 返回"您不是当前节点的审批人"。安全边界从"角色标签"换成了"数据归属"，反而更准确。

### Q6：为什么要用逻辑删除？和物理删除有什么区别？

> 逻辑删除是加 `deleted=0` 条件，数据还在库里。
>
> 我的场景是工单和用户 —— 工单被删了但要保留轨迹和操作日志（合规要求），
> 用户离职了也要保留他处理过的工单记录。
>
> 代价是：**所有查询都得记得加条件**，漏一个就会查出已删数据。
> 我项目里现在还是手写的 `deleted = 0`，更好的做法是用 MyBatis-Plus 的
> 全局逻辑删除配置，让框架自动处理。这个我在风险清单里列了。

### Q7：分页查询怎么实现的？数据量大怎么办？

> MyBatis-Plus 的 `Page` + 分页插件，`TicketService.page()` 返回 `PageResult`。
>
> 工单表上建了 `idx_status`、`idx_assignee`、`idx_creator` 三个索引，
> 对应最常见的过滤维度。
>
> **数据量大了怎么办**：
> 1. 先加索引（组合索引按查询模式设计）；
> 2. 深翻页问题 —— OFFSET 100000 会扫描 10 万行，MySQL 8 可以用延迟关联优化；
> 3. 再大就上 Elasticsearch 或直接做归档表；
> 4. 看板的统计查询是最重的（4 个 group by），可以改成预聚合表 + 定时刷新，
>    牺牲实时性换查询性能 —— 项目里已经有定时任务的框架了，改造成本不高。

### Q8：事务用在哪些地方？为什么加 `@Transactional`？

> 主要在**多表写入**的地方。比如工单流转一次要更新 `wf_ticket`、
> 写 `wf_ticket_flow`、发通知，任何一步失败都应该整体回滚。
>
> 我统一用 `@Transactional(rollbackFor = Exception.class)`，
> 加上 `rollbackFor` 是因为默认只回滚 RuntimeException，
> 受检异常不回滚 —— 这个坑我也踩过。

### Q9：统一响应和全局异常怎么设计的？

> 统一返回 `{ code, msg, data, timestamp }`，`Result.success()` / `Result.error()` 静态工厂。
>
> 异常用 `@RestControllerAdvice` 全局捕获：`BusinessException` 返回业务码和提示，
> 参数校验异常提取字段信息，其他异常统一返回 500 + "系统繁忙"。
>
> **安全点**：500 不把堆栈返回前端，只记服务端日志。
>
> 前端在 `utils/request.js` 用 Axios 响应拦截器统一处理：
> code=200 直接解包，401 清 token 跳登录，其他错误统一弹提示。
> 这样业务代码里就只写 `api.get('/xxx')`，不用每次判 code。

### Q10：你的项目里权限是怎么设计的？数据权限和功能权限有什么区别？

见 [3.3 三层权限体系](#33-三层权限体系)，答题要点：

> **功能权限**回答"这个角色允许不允许做这件事"，用 `@PreAuthorize` 拦截。
> **数据权限**回答"这个角色能看到哪些行"，必须在 Service 或 SQL 层判断。
> **业务权限**回答"这条数据是不是你的"，注解表达不了，必须写代码。
>
> 我踩过的坑：
> - 早期写成"非管理员一律只能看自己的工单"，导致主管有受理权限却看不到别人的单，
>   **有权限但没数据**的死锁。改成按"职能权限"（`ticket:handle`）判断后解决。
> - 把"发起审批"和"审批操作"都挂在 `approval:audit` 上，
>   导致普通员工发不了审批；而 submit 接口又没做后端校验，
>   **前后端权限不一致**。拆成 `approval:submit` 和 `approval:audit` 才理清。

### Q11：启动时遇到 Bean 循环依赖，你怎么排查和解决的？

> 真实踩过的。报错 `Requested bean is currently in creation`。
>
> 排查：Spring 的报错会打印依赖环图示。我的环是
> `jwtAuthenticationFilter → authService → passwordEncoder → securityConfig → jwtAuthenticationFilter`。
>
> 根因：`PasswordEncoder` 定义在 `SecurityConfig` 里，
> 而 `SecurityConfig` 注入 `JwtAuthenticationFilter`，后者依赖 `AuthService`，
> `AuthService` 又需要 `PasswordEncoder` —— 闭环。Spring Boot 2.6+ 默认禁止。
>
> 解法：把"认证算法"相关的 Bean 拆到独立的 `PasswordConfig`。
> 额外给过滤器的 `AuthService` 依赖加 `@Lazy` 兜底。
>
> **不建议**开 `spring.main.allow-circular-references=true` 压警告 ——
> 循环依赖本身就是设计信号。

### Q12：AOP 用在了哪？有什么坑？

> 用在操作日志上，`@Around` 环绕通知拦截 `@OperLog` 注解。
>
> 三个坑：
> 1. **参数序列化会炸** —— `MultipartFile` 和 Servlet 相关对象不能序列化，
>    必须在转换前过滤掉；
> 2. **日志写入失败不能影响主流程** —— 整体 try-catch；
> 3. **业务异常不能被吞** —— catch 后要 `throw e` 原样抛出。
>
> 另外 `objectMapper.writeValueAsString()` 声明的是受检异常，
> 必须 try-catch，这个我一开始漏了，编译直接报错。

### Q13：前端动态路由怎么实现的？

> 我最后**没有用** `router.addRoute()` 动态注册，而是静态注册 + 菜单按权限过滤。
>
> 原因：动态注册要求嵌套两层时路径拼接容易出错，而且刷新页面后动态路由会丢失必须重算。
>
> 具体做法：`businessRoutes` 直接作为 MainLayout 的 children，
> `getMenuRoutes(roles, permissions)` 按权限过滤出显示哪些菜单项，
> 隐藏项（比如工单详情、个人中心）用 `meta.hidden` 排除。
>
> **关键认知**：前端隐藏菜单不是安全边界，只是体验优化。
> 我一开始就把 MainLayout 的 `children` 写成空数组、业务路由放在 `asyncRoutes` 里
> 却从来没 `addRoute`，结果登录后跳 `/dashboard` 直接 404 —— 这也是真实的坑。

### Q14：如果流量/数据量翻倍怎么办？

> 分层回答：
>
> **数据库层**
> - 工单表补组合索引（按查询模式设计，比如 `(status, priority, create_time)`）
> - 看板的 4 个 group by 是最重的，改成预聚合表 + 定时刷新（项目已有定时任务框架）
> - 深翻页用延迟关联或游标分页
> - 读写分离：工单查询走从库，写走主库
>
> **应用层**
> - 权限查询加 Redis 缓存（这是当前最大的性能瓶颈，每请求 3 次查询）
> - 加接口限流，防止突发流量打垮数据库
> - 无状态设计天然支持水平扩容，多起几个实例挂 Nginx 就行
>
> **架构层**
> - 如果工单量和审批量差异大，或者团队规模上来，可以把通知模块拆出去
> - 定时任务改成分布式调度（XXL-Job / Scheduler），避免多实例重复执行
>   —— 这个我现在的 `TicketSlaTask` 在多实例部署下**确实会重复执行**，是已知问题
>
> 我会**先做监控和压测**，用真实数据决定优化优先级，而不是凭感觉改。

### Q15：如何保证幂等？

> **诚实回答：目前没有做，这是我知道的缺陷。**
>
> 现在的风险：
> - 重复点"提交"会创建两张一样的工单
> - 重复点"通过"虽然状态校验能挡住大部分，但并发下有窗口期
>
> 我的方案会是：
> 1. 前端提交时带一个幂等号（UUID），后端用 Redis 存 `幂等号 → 结果`，重复请求直接返回上次结果；
> 2. 数据库层面，工单号、审批单号已经加了唯一索引，可以把幂等号也做成唯一索引兜底；
> 3. 状态流转用**乐观锁**：更新时带上 version 条件，`UPDATE ... WHERE id=? AND status=?`，
>    影响行数为 0 就说明状态已被别人改过，直接抛"状态已变更，请刷新"。
>
> 第 3 点其实和现有代码的逻辑天然契合 —— 我现在虽然没用 version 字段，
> 但 `handleAction` 里是"先查状态 → 再更新"，两步之间确实有并发窗口。

### Q16：线上出现某个角色的用户菜单全空，你怎么排查？

> 这个我真遇到过。三步：
>
> **第一步：先分清是前端还是后端**
> 让用户 F12 看登录接口 `/auth/login` 的响应里 `permissions` 是不是空数组。
> 空数组 → 后端数据问题；有值但菜单空 → 前端过滤逻辑问题。
> 这一步直接把范围缩小一半。
>
> **第二步：查数据链路**
> 权限是 `sys_user → sys_user_role → sys_role → sys_role_permission → sys_permission`
> 五张表串下来的，任何一环断了权限就空。查的时候**一定要用 LEFT JOIN** ——
> 用 INNER JOIN 的话某环断了结果集整个是空的，你反而看不出是哪一环断的。
>
> **第三步：定位具体哪一环**
> 我那次查出来是 `sys_role_permission` 表里普通员工角色一条记录都没有。
> 原因很离谱：初始化 SQL 写 INSERT 时漏了一列，
> MySQL 报 `Column count doesn't match`，**整条 INSERT 全部不执行**。
>
> 这个错误难发现是因为导入 SQL 时它容易被忽略（后面很多语句照常执行），
> 但影响是"整条失效"而不是"少一条"。
>
> **修复后我做了两件事**：
> 1. 把硬编码 ID 改成子查询（按 `perm_code` 反查 id），天然不会写错列数；
> 2. 加了个调试接口 `/debug/permission?username=xxx`，
>    它会模拟前端的菜单过滤逻辑，逐个返回"显示/隐藏 + 原因"。
>    有了这个以后排查同类问题不用临时写 SQL。
>
> **最后提醒**：前端权限缓存到 localStorage，改完数据库必须让用户**重新登录**。

### Q17：一个接口该不该按角色收紧权限？

> 我项目里有真实的正反两面。
>
> **反面**：审批人下拉框调的是 `/system/user/list`（用户管理接口，需 `system:user`）。
> 普通员工发起审批时 403，下拉框"无数据"，不选人又不能提交 —— **死锁**。
>
> **我的判断标准**：
> **「这个操作如果每个员工每天都要做，那它就不该是管理员专属」**。
> 反例就是"请假要管理员权限才能提交"—— 明显不合理。
>
> **正确做法**：按使用场景拆接口。管理场景用受保护的管理接口，
> 业务场景用 `/common/users`（登录即可，只返回 id/昵称/部门这类低敏感字段）。
>
> **这不构成权限绕过**：
> 1. 数据本身低敏感 —— 同事姓名部门本来就是组织内公开信息，敏感字段一律不返回；
> 2. 真正的安全边界在业务层 —— 能不能审批某单据取决于 `ap_approval_node` 的归属，
>    与能否看到用户列表无关。

### Q18：功能权限和数据权限有什么区别？你怎么落地？

见 [3.3](#33-三层权限体系)，答题要点：

> **落地三条原则**：
> 1. 功能权限用注解，数据权限用 Service 层 —— 注解只能表达角色维度，表达不了行维度；
> 2. 数据权限的判断依据要脱离角色标签 —— 用"有没有某项职能权限"而不是"是不是 admin"，
>    这样新增角色时只要配好权限就自动生效；
> 3. 前端隐藏只是体验优化，后端拦截才是安全边界 —— 所有权限判断后端都做了第二遍。
>
> 我最想强调的一个认知：**审批权是数据权限，不是功能权限**。
> 能不能审批某张单取决于"你是不是这单当前节点的审批人"，
> 而不是"你是什么角色" —— 角色是员工，但在这张单上他是审批人。

---

## 六、仓库缺失信息清单

以下均为**仓库中确实不存在**的内容，如实列出：

| # | 缺失项 | 影响 | 建议 |
|---|--------|------|------|
| 1 | **后端测试仅 2 个类**（`TicketStatusMachineTest` / `AuthServiceTest`） | Service 层、Controller 层、权限逻辑均无测试 | 优先补 `TicketService` 流转测试、`ApprovalService` 越权测试 |
| 2 | **前端零测试**（无 `*.test.js` / `*.spec.js`，`package.json` 无 test 脚本） | 工具函数、权限指令无回归保障 | 用 Vitest 补 `getMenuRoutes` 的参数化测试 |
| 3 | **无 Docker / docker-compose / CI 配置** | 部署靠手工，环境不一致 | 加 Dockerfile + GitHub Actions |
| 4 | **无 `.env` / 环境分级配置**（仅一份 `application.yml`） | 开发/生产配置混在一起 | 用 `application-{dev,prod}.yml` + 环境变量注入敏感配置 |
| 5 | **JWT 密钥在配置里派生，无 KMS/密钥轮换** | 泄露无法快速止损 | 环境变量注入 + 双密钥并存期 |
| 6 | **`backend/src/main/resources/mapper/` 为空目录** | 冗余（已全部用注解 SQL） | 删除该目录 |
| 7 | **`docs/` 无 API 接口清单文档** | 接口契约只能看代码 | 用 Knife4j 导出 OpenAPI 文档 |
| 8 | **无 `README` 的快速启动说明**（需看 `docs/环境配置指引.md`） | 新人上手成本 | README 加 5 行快速启动 |
| 9 | **无监控/日志采集配置** | 线上问题难定位 | 接入 Actuator + Micrometer 暴露指标 |
| 10 | **`dailyReport()` 只打日志，未真正发送日报** | 功能未完成 | 调用 `NoticeService` 发给管理员 |
| 11 | **无分库分表/归档策略** | 数据持续增长后有压力 | 冷热分离，历史工单归档表 |
| 12 | **无幂等控制**（提交类接口） | 重复提交产生脏数据 | Redis 幂等号 + 数据库唯一索引兜底 |

---

## 附录：快速索引

| 想了解 | 看这里 |
|--------|--------|
| 状态机的规则定义 | `backend/.../enums/TicketStatus.java` |
| 状态流转的完整实现 | `backend/.../service/TicketService.java` → `handleAction()` |
| 权限的三层判定 | `TicketService.checkActionPermission()` / `ApprovalService.audit()` / Controller 的 `@PreAuthorize` |
| 登录与令牌生成 | `backend/.../service/AuthService.java` → `login()` / `util/JwtUtils.java` |
| 每请求加载权限 | `backend/.../security/JwtAuthenticationFilter.java` |
| 数据库表结构 | `sql/taskflow.sql` |
| 前端菜单权限过滤 | `frontend/src/router/index.js` → `getMenuRoutes()` |
| 前端请求统一处理 | `frontend/src/utils/request.js` |
| 操作日志实现 | `backend/.../aspect/OperLogAspect.java` |
| 定时任务 | `backend/.../task/TicketSlaTask.java` |
| 状态机测试 | `backend/src/test/java/com/taskflow/enums/TicketStatusMachineTest.java` |
| 已知的坑与修复记录 | `docs/搭建教程.md` 的"踩过的真实坑"章节 |
| 已有面试题答案 | `docs/面试问答.md`（18 题） |
