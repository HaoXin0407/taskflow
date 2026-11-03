-- ===================================================================
--  TaskFlow 企业级智能工单流转与审批协同平台 —— 数据库脚本
--  MySQL 8.0+  执行方式：mysql -uroot -p < taskflow.sql
--  注意：脚本可重复执行，会先删除旧库
-- ===================================================================

DROP DATABASE IF EXISTS `taskflow`;
CREATE DATABASE `taskflow` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `taskflow`;

-- -------------------------------------------------------------------
-- 1. 用户表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`      VARCHAR(50)  NOT NULL COMMENT '登录账号',
  `password`      VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加密）',
  `nickname`      VARCHAR(50)  NOT NULL COMMENT '昵称',
  `email`         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `avatar`        VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  `dept`          VARCHAR(50)  DEFAULT NULL COMMENT '部门',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
  `remark`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `last_login_at` DATETIME     DEFAULT NULL COMMENT '最后登录时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -------------------------------------------------------------------
-- 2. 角色表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code`   VARCHAR(50) NOT NULL COMMENT '角色标识，如 ADMIN',
  `role_name`   VARCHAR(50) NOT NULL COMMENT '角色名称',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- -------------------------------------------------------------------
-- 3. 权限（菜单）表 —— 前端动态路由 + 后端接口鉴权都基于这张表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父级ID，0为顶级',
  `perm_code`   VARCHAR(100) NOT NULL COMMENT '权限标识，如 system:user:list',
  `perm_name`   VARCHAR(50) NOT NULL COMMENT '权限名称',
  `perm_type`   CHAR(1)     NOT NULL DEFAULT 'C' COMMENT '类型 M目录 C菜单 F按钮',
  `path`        VARCHAR(100) DEFAULT NULL COMMENT '前端路由地址',
  `component`   VARCHAR(150) DEFAULT NULL COMMENT '前端组件路径',
  `icon`        VARCHAR(50)  DEFAULT NULL COMMENT '菜单图标',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `visible`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示 1是 0否',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限菜单表';

-- -------------------------------------------------------------------
-- 4. 用户-角色 关联表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- -------------------------------------------------------------------
-- 5. 角色-权限 关联表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT,
  `role_id`       BIGINT NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
  KEY `idx_perm` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- -------------------------------------------------------------------
-- 6. 工单主表（业务核心）
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `wf_ticket`;
CREATE TABLE `wf_ticket` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工单ID',
  `ticket_no`     VARCHAR(40)  NOT NULL COMMENT '工单编号 TF+yyyyMMdd+随机',
  `title`         VARCHAR(100) NOT NULL COMMENT '工单标题',
  `content`       TEXT         NOT NULL COMMENT '工单内容描述',
  `type`          VARCHAR(20)  NOT NULL DEFAULT 'OTHER' COMMENT '工单类型 BUG/FEATURE/COMPLAINT/OTHER',
  `priority`      TINYINT      NOT NULL DEFAULT 2 COMMENT '优先级 1紧急 2高 3中 4低',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0待受理 1处理中 2待确认 3已完成 4已关闭 5已驳回',
  `creator_id`    BIGINT       NOT NULL COMMENT '创建人ID',
  `assignee_id`   BIGINT       DEFAULT NULL COMMENT '当前处理人ID',
  `dept`          VARCHAR(50)  DEFAULT NULL COMMENT '归属部门',
  `is_timeout`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否超时 1是 0否',
  `deadline`      DATETIME     DEFAULT NULL COMMENT 'SLA 处理截止时间',
  `finish_at`     DATETIME     DEFAULT NULL COMMENT '完成时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`),
  KEY `idx_status` (`status`),
  KEY `idx_assignee` (`assignee_id`),
  KEY `idx_creator` (`creator_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单主表';

-- -------------------------------------------------------------------
-- 7. 工单流转记录表（状态机每次变更都留痕）
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `wf_ticket_flow`;
CREATE TABLE `wf_ticket_flow` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `ticket_id`    BIGINT      NOT NULL COMMENT '工单ID',
  `from_status`  TINYINT     DEFAULT NULL COMMENT '变更前状态，NULL表示创建',
  `to_status`    TINYINT     NOT NULL COMMENT '变更后状态',
  `action`       VARCHAR(20) NOT NULL COMMENT '动作 CREATE/ACCEPT/SUBMIT/CONFIRM/REJECT/CLOSE/REOPEN/COMMENT',
  `remark`       VARCHAR(500) DEFAULT NULL COMMENT '操作备注',
  `operator_id`  BIGINT      NOT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人昵称（冗余，避免连表）',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_ticket` (`ticket_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单流转记录表';

-- -------------------------------------------------------------------
-- 8. 工单评论表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `wf_ticket_comment`;
CREATE TABLE `wf_ticket_comment` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `ticket_id`  BIGINT      NOT NULL COMMENT '工单ID',
  `content`    VARCHAR(1000) NOT NULL COMMENT '评论内容',
  `user_id`    BIGINT      NOT NULL COMMENT '评论人ID',
  `user_name`  VARCHAR(50) DEFAULT NULL COMMENT '评论人昵称',
  `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_ticket` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单评论表';

-- -------------------------------------------------------------------
-- 9. 审批单主表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `ap_approval`;
CREATE TABLE `ap_approval` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '审批单ID',
  `approval_no`  VARCHAR(40) NOT NULL COMMENT '审批单号 AP+yyyyMMdd+随机',
  `title`        VARCHAR(100) NOT NULL COMMENT '审批标题',
  `biz_type`     VARCHAR(20) NOT NULL DEFAULT 'LEAVE' COMMENT '业务类型 LEAVE/OVERTIME/PURCHASE/REOPEN',
  `biz_id`       BIGINT      DEFAULT NULL COMMENT '关联业务ID，如工单ID',
  `reason`       VARCHAR(500) DEFAULT NULL COMMENT '申请事由',
  `amount`       DECIMAL(12,2) DEFAULT NULL COMMENT '金额（报销/采购类用）',
  `applicant_id` BIGINT      NOT NULL COMMENT '申请人ID',
  `current_node` INT         NOT NULL DEFAULT 1 COMMENT '当前节点序号',
  `total_node`   INT         NOT NULL DEFAULT 1 COMMENT '总节点数',
  `status`       TINYINT     NOT NULL DEFAULT 0 COMMENT '状态 0审批中 1已通过 2已驳回 3已撤销',
  `deleted`      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_no` (`approval_no`),
  KEY `idx_applicant` (`applicant_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批单主表';

-- -------------------------------------------------------------------
-- 10. 审批节点表（一条审批单有多个节点，串行流转）
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `ap_approval_node`;
CREATE TABLE `ap_approval_node` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `approval_id` BIGINT      NOT NULL COMMENT '审批单ID',
  `node_index`  INT         NOT NULL COMMENT '节点序号，从1开始',
  `node_name`   VARCHAR(50) NOT NULL COMMENT '节点名称，如 直属主管审批',
  `approver_id` BIGINT      NOT NULL COMMENT '审批人ID',
  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '状态 0待审批 1通过 2驳回',
  `comment`     VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
  `approve_time` DATETIME   DEFAULT NULL COMMENT '审批时间',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_approval` (`approval_id`),
  KEY `idx_approver_status` (`approver_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点表';

-- -------------------------------------------------------------------
-- 11. 站内通知表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT      NOT NULL COMMENT '接收人ID',
  `title`       VARCHAR(100) NOT NULL COMMENT '通知标题',
  `content`     VARCHAR(500) NOT NULL COMMENT '通知内容',
  `type`        VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' COMMENT '类型 SYSTEM/TICKET/APPROVAL',
  `is_read`     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否已读 1是 0否',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';

-- -------------------------------------------------------------------
-- 12. 操作日志表（由 AOP 自动写入）
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `title`       VARCHAR(50) DEFAULT NULL COMMENT '模块标题',
  `business_type` VARCHAR(20) DEFAULT NULL COMMENT '业务类型 INSERT/UPDATE/DELETE/OTHER',
  `method`      VARCHAR(200) DEFAULT NULL COMMENT '方法名',
  `request_method` VARCHAR(10) DEFAULT NULL COMMENT 'HTTP 请求方式',
  `oper_id`     BIGINT      DEFAULT NULL COMMENT '操作人ID',
  `oper_name`   VARCHAR(50) DEFAULT NULL COMMENT '操作人昵称',
  `oper_url`    VARCHAR(200) DEFAULT NULL COMMENT '请求URL',
  `oper_ip`     VARCHAR(64) DEFAULT NULL COMMENT '操作IP',
  `oper_param`  VARCHAR(2000) DEFAULT NULL COMMENT '请求参数',
  `json_result` VARCHAR(2000) DEFAULT NULL COMMENT '返回结果',
  `status`      TINYINT     DEFAULT 1 COMMENT '状态 1成功 0失败',
  `error_msg`   VARCHAR(2000) DEFAULT NULL COMMENT '错误信息',
  `cost_time`   BIGINT      DEFAULT 0 COMMENT '耗时(ms)',
  `oper_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_oper_time` (`oper_time`),
  KEY `idx_oper_id` (`oper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';


-- ===================================================================
--  初始化数据
--  说明：密码先写入明文 123456，应用首次启动时会自动升级为 BCrypt 密文
-- ===================================================================

-- 用户：明文密码统一 123456
INSERT INTO `sys_user` (`id`,`username`,`password`,`nickname`,`email`,`dept`,`status`,`remark`) VALUES
(1,'admin','123456','系统管理员','admin@taskflow.com','技术部',1,'超级管理员，拥有全部权限'),
(2,'manager','123456','部门主管','manager@taskflow.com','技术部',1,'负责工单审批'),
(3,'zhangsan','123456','张三','zhangsan@taskflow.com','研发部',1,'普通员工'),
(4,'lisi','123456','李四','lisi@taskflow.com','测试部',1,'普通员工'),
(5,'wangwu','123456','王五','wangwu@taskflow.com','研发部',1,'普通员工');

-- 角色
INSERT INTO `sys_role` (`id`,`role_code`,`role_name`,`remark`) VALUES
(1,'ADMIN','超级管理员','拥有系统全部权限'),
(2,'MANAGER','部门主管','负责工单受理与审批'),
(3,'USER','普通员工','提交并跟踪自己的工单');

-- 权限菜单
INSERT INTO `sys_permission` (`id`,`parent_id`,`perm_code`,`perm_name`,`perm_type`,`path`,`component`,`icon`,`sort`) VALUES
(1 ,0,'dashboard','数据看板','M','/dashboard',NULL,'DataAnalysis',1),
(2 ,0,'ticket','工单中心','M','/ticket',NULL,'Tickets',2),
(3 ,0,'approval','审批中心','M','/approval',NULL,'Stamp',3),
(4 ,0,'notice','通知中心','M','/notice',NULL,'Bell',4),
(5 ,0,'system','系统管理','M','/system',NULL,'Setting',5),

(10,2,'ticket:list','工单列表','C','/ticket/list','ticket/List','List',1),
(11,2,'ticket:add','创建工单','F',NULL,NULL,NULL,2),
(12,2,'ticket:handle','处理工单','F',NULL,NULL,NULL,3),
(13,2,'ticket:delete','删除工单','F',NULL,NULL,NULL,4),

(20,3,'approval:list','审批列表','C','/approval/list','approval/List','List',1),
(21,3,'approval:submit','发起审批','F',NULL,NULL,NULL,2),
(22,3,'approval:audit','审批操作','F',NULL,NULL,NULL,3),

(30,4,'notice:list','通知列表','C','/notice/list','notice/List','Bell',1),

(40,5,'system:user','用户管理','C','/system/user','system/User','User',1),
(41,5,'system:role','角色管理','C','/system/role','system/Role','Avatar',2),
(42,5,'system:log','操作日志','C','/system/log','system/Log','Document',3);

-- 用户角色
INSERT INTO `sys_user_role` (`user_id`,`role_id`) VALUES
(1,1),(2,2),(3,3),(4,3),(5,3);

-- ============================================================
-- 角色权限映射
-- ------------------------------------------------------------
-- 用「按 perm_code 反查 id」的子查询写法，而不是硬编码数字。
-- 原因：硬编码 id 时极容易写错列数或位数（比如漏写 role_id 变成 (2)），
-- 而这种错误在导入时只报「Column count doesn't match」，
-- 整条 INSERT 全部失败，导致该角色一个权限都没有，排查成本很高。
-- 按 perm_code 关联既可读、天然防错，改权限编号也不用改这里。
-- ============================================================

-- 管理员：全部权限
INSERT INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT 1, id FROM `sys_permission`;

-- 主管：看板、工单(全部)、审批(发起+审核)、通知 —— 不能进系统管理
INSERT INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT 2, id FROM `sys_permission`
WHERE perm_code IN (
  'dashboard','ticket','approval','notice',
  'ticket:list','ticket:add','ticket:handle',
  'approval:list','approval:submit','approval:audit',
  'notice:list'
);

-- 普通员工：看板、查看/创建工单、查看审批、发起审批、通知 —— 不能审核、不能进系统管理
INSERT INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT 3, id FROM `sys_permission`
WHERE perm_code IN (
  'dashboard','ticket','approval','notice',
  'ticket:list','ticket:add',
  'approval:list','approval:submit',
  'notice:list'
);

-- 通知示例
INSERT INTO `sys_notice` (`user_id`,`title`,`content`,`type`,`is_read`) VALUES
(1,'系统初始化完成','TaskFlow 平台已成功部署，欢迎使用！','SYSTEM',0),
(1,'欢迎使用数据看板','您可以在数据看板查看工单的整体统计与趋势分析。','SYSTEM',0);

-- 通知管理员的待办（1号=未读）
INSERT INTO `sys_notice` (`user_id`,`title`,`content`,`type`,`is_read`) VALUES
(3,'您有 1 条待处理工单','工单 TF202610140001 正在等待您的处理。','TICKET',0);

-- 演示工单数据
INSERT INTO `wf_ticket` (`ticket_no`,`title`,`content`,`type`,`priority`,`status`,`creator_id`,`assignee_id`,`dept`,`deadline`) VALUES
('TF202610140001','登录页面偶尔无法提交','用户在登录页点击登录按钮无响应，偶发网络超时后出现，浏览器为 Chrome 120。','BUG',1,1,3,2,'研发部',DATE_ADD(NOW(), INTERVAL 2 DAY)),
('TF202610140002','希望增加批量导出功能','希望工单列表支持按筛选条件批量导出为 Excel，方便做月度统计。','FEATURE',2,0,4,NULL,'测试部',DATE_ADD(NOW(), INTERVAL 7 DAY)),
('TF202610140003','测试环境数据无法清空','测试环境下无法一键清空历史工单数据，希望提供清理功能。','COMPLAINT',2,2,5,1,'研发部',DATE_ADD(NOW(), INTERVAL 3 DAY)),
('TF202610140004','数据库查询性能优化建议','工单列表在数据量超过10万时查询明显变慢，建议优化。','OTHER',3,3,5,1,'研发部',DATE_ADD(NOW(), INTERVAL 30 DAY));

-- 工单流转记录
INSERT INTO `wf_ticket_flow` (`ticket_id`,`from_status`,`to_status`,`action`,`remark`,`operator_id`,`operator_name`) VALUES
(1,NULL,0,'CREATE','提交工单',3,'张三'),
(1,0,1,'ACCEPT','已受理，开始排查',2,'部门主管'),
(2,NULL,0,'CREATE','提交工单',4,'李四'),
(3,NULL,0,'CREATE','提交工单',5,'王五'),
(3,0,1,'ACCEPT','已受理',1,'系统管理员'),
(3,1,2,'SUBMIT','已修复，提交验证',1,'系统管理员'),
(4,NULL,0,'CREATE','提交工单',5,'王五'),
(4,0,1,'ACCEPT','已受理',1,'系统管理员'),
(4,1,2,'SUBMIT','已完成优化',1,'系统管理员'),
(4,2,3,'CONFIRM','验证通过，工单关闭',5,'王五');

-- 评论
INSERT INTO `wf_ticket_comment` (`ticket_id`,`content`,`user_id`,`user_name`) VALUES
(1,'我在 Safari 浏览器上也遇到过，麻烦一起看看。',4,'李四'),
(1,'初步判断是网关超时设置过短，正在调整。',2,'部门主管'),
(3,'复现了，是删除逻辑漏了软删除条件。',1,'系统管理员');

-- 审批单示例
INSERT INTO `ap_approval` (`approval_no`,`title`,`biz_type`,`biz_id`,`reason`,`applicant_id`,`current_node`,`total_node`,`status`) VALUES
('AP202610140001','加班申请-10月14日','OVERTIME',NULL,'因版本上线需要，10月14日加班至22:00。',3,1,2,0),
('AP202610140002','测试环境清理申请','PURCHASE',3,'需要授权清理测试环境历史工单数据。',5,1,1,1);

INSERT INTO `ap_approval_node` (`approval_id`,`node_index`,`node_name`,`approver_id`,`status`,`comment`,`approve_time`) VALUES
(1,1,'直属主管审批',2,0,NULL,NULL),
(1,2,'部门总监审批',1,0,NULL,NULL),
(2,1,'直属主管审批',2,1,'同意',DATE_SUB(NOW(), INTERVAL 1 DAY));