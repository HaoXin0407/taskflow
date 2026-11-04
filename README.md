# TaskFlow · 企业级智能工单流转与审批协同平台

一个难度适中、工程实践完整、经得住面试追问的 Java 全栈项目。

**技术栈**：Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Spring Security + JWT + Vue3 + Vite + Element Plus + ECharts

---

## 快速开始

### 1. 初始化数据库

```bash
mysql -uroot -p < sql/taskflow.sql
```

### 2. 配置数据库密码与 JWT 密钥

项目采用**配置分离**，仓库里不含任何真实密码。

**方式一：环境变量（推荐）**

```bash
# Linux / macOS
export DB_USERNAME=root
export DB_PASSWORD=你的MySQL密码
export TASKFLOW_JWT_SECRET=$(openssl rand -base64 48)
```

```powershell
# Windows PowerShell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
$env:TASKFLOW_JWT_SECRET="你的随机长字符串"
```

**方式二：本地配置文件**

```bash
cd backend/src/main/resources
cp application-local.example.yml application-local.yml
# 编辑 application-local.yml 填入真实值
```

启动时激活 local 环境：`--spring.profiles.active=local`

> `application-local.yml` 已被 `.gitignore` 忽略，不会误提交。
> **不要把真实密码直接写进 `application.yml` 再提交** —— Git 的提交历史不可逆，
> 即使后来删除，密码依然能被翻出来。

### 3. 启动后端

用 IDEA 打开 `backend` 目录，等待 Maven 依赖下载完成，
右键 `TaskFlowApplication.java` → Run。

或用命令行：

```bash
cd backend
./mvnw spring-boot:run
```

看到这段就是成功：

```
====================================================
  TaskFlow 后端启动成功！
  接口地址: http://localhost:8080/api
  接口文档: http://localhost:8080/api/doc.html
====================================================
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173

### 5. 登录

| 账号 | 密码 | 权限 |
|------|------|------|
| `admin` | 123456 | 超级管理员，全部功能 |
| `manager` | 123456 | 部门主管，工单受理 + 审批 |
| `zhangsan` | 123456 | 普通员工，仅创建与查看自己的工单 |
| `lisi` | 123456 | 普通员工 |
| `wangwu` | 123456 | 普通员工 |

建议三个账号都登一遍，体会权限差异。

---

## 核心业务：工单状态机

系统最核心的设计。6 个状态、7 个动作，合法流转关系集中定义在枚举里：

```
待受理 ──受理──> 处理中 ──提交──> 待确认 ──确认──> 已完成 ──关闭──> 已关闭
   │                │  ↑           │  ↑                                  │
   └──关闭──> 已关闭  │  └─驳回─────┘  └─驳回──────────────────────────────┤
                    │                                              重开 │
                    └──转派──>（状态不变，仅换处理人）                       │
                                                     已关闭 ←─重开────────┘
```

**设计要点**：

- 状态与允许动作的映射写在 `TicketStatus` 枚举，Service 层统一调 `canDo()` 校验
- 每个动作绑定目标状态写在 `TicketAction` 枚举，调用方无需传 targetStatus
- 所有流转必须经过 `TicketService.handleAction` 一个入口
- 每次流转都写一条 `wf_ticket_flow` 记录，全链路可追溯

---

## 功能清单

| 模块 | 功能 |
|------|------|
| 认证 | 登录、注册、修改密码、JWT 无状态鉴权 |
| 工单 | 创建、修改、删除、受理、转派、提交、确认、驳回、关闭、重开、评论、时间线 |
| 审批 | 发起审批、多级串行审批、通过/驳回、撤销、审批链可视化 |
| 看板 | 核心指标、状态分布、类型分布、30 天趋势、处理人排行、审批分布（ECharts） |
| 通知 | 站内信、未读角标、单条/全部已读 |
| 系统 | 用户管理、角色分配、操作日志（密码重置） |

---

## 文档

- [`PROJECT_OVERVIEW.md`](PROJECT_OVERVIEW.md) —— **项目理解文档**：架构、6 条业务链路、核心模块解析、13 条设计决策、13 条风险、18 个面试问答
- [`docs/搭建教程.md`](docs/搭建教程.md) —— 从零搭建的完整步骤、7 个真实踩坑记录
- [`docs/面试问答.md`](docs/面试问答.md) —— 18 个高频面试问题的答法与踩坑点
- [`docs/环境配置指引.md`](docs/环境配置指引.md) —— 环境依赖与常见问题

---

## 技术亮点

1. **状态机**：状态流转关系集中管理在枚举，非法流转在入口拦截，配参数化单元测试
2. **三层权限体系**：功能权限（`@PreAuthorize`）、数据权限（角色能力）、业务权限（数据归属）分离
3. **RBAC 权限体系**：五张表实现菜单级 + 按钮级权限，前后端双重校验
4. **JWT 无状态认证**：每次请求重查权限，保证权限变更立即生效
5. **多级串行审批**：审批链数据化存储，扩展审批层级无需改代码
6. **AOP 操作日志**：注解 + 环绕通知，业务代码零侵入，日志失败不影响主流程
7. **统一响应与全局异常**：分类型异常处理，兜底 handler 防信息泄露
8. **MyBatis-Plus 进阶用法**：逻辑删除、分页插件、全表更新拦截
9. **N+1 查询规避**：评论数用一次聚合查询批量获取
10. **定时任务**：SLA 超时扫描、工单日报
11. **配置分离**：数据库密码与 JWT 密钥走环境变量，仓库零敏感信息
12. **调试接口**：`/api/debug/permission` 模拟前端菜单过滤，快速定位权限问题

---

## 项目结构

```
taskflow/
├── sql/taskflow.sql          # 建库建表 + 初始化数据
├── backend/                  # Spring Boot 后端（75 个 Java 文件）
│   ├── mvnw / .mvn/          # Maven Wrapper，无需预装 Maven
│   └── src/
│       ├── main/java/com/taskflow/
│       │   ├── common/      # 统一响应、错误码、全局异常
│       │   ├── config/      # 安全、MyBatis、属性绑定
│       │   ├── controller/  # 8 个 HTTP 入口
│       │   ├── dto/ entity/ vo/   # 三类数据模型
│       │   ├── enums/       # 状态机与业务枚举
│       │   ├── mapper/      # 12 个 Mapper
│       │   ├── security/    # JWT 过滤器与权限工具
│       │   ├── service/     # 7 个业务服务
│       │   └── task/        # SLA 定时任务
│       └── test/            # 状态机与认证单元测试
├── frontend/                 # Vue3 + Vite + Element Plus + ECharts
├── docs/                     # 搭建教程 + 面试问答 + 环境配置
└── PROJECT_OVERVIEW.md       # 项目理解文档
```

---

## 已知限制

诚实列出，避免误导使用者：

- 无接口幂等控制，重复提交会产生重复单据
- JWT 密钥无轮换机制，生产环境应改用 KMS 注入
- 无登录失败次数限制 / 验证码 / 账号锁定
- 无接口限流与熔断
- 前端零测试，后端仅 2 个测试类（状态机 + 认证）
- 未提供 Docker / CI 部署配置
- 调试接口 `/api/debug/permission` **需要登录后调用**（不返回敏感字段，仅 id/昵称/部门）。
  若部署到公网，建议给 `DebugController` 加 `@Profile("dev")` 彻底关闭