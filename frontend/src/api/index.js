import request from '@/utils/request'

// ============ 认证 ============
export function login(data) {
  return request.post('/auth/login', data)
}

export function register(data) {
  return request.post('/auth/register', data)
}

export function getUserInfo() {
  return request.get('/auth/me')
}

export function changePassword(data) {
  return request.post('/auth/password', data)
}

// ============ 看板 ============
export function getDashboard() {
  return request.get('/dashboard')
}

// ============ 工单 ============
export function pageTicket(params) {
  return request.get('/ticket/page', { params })
}

export function getTicket(id) {
  return request.get(`/ticket/${id}`)
}

export function createTicket(data) {
  return request.post('/ticket', data)
}

export function updateTicket(data) {
  return request.put('/ticket', data)
}

export function deleteTicket(id) {
  return request.delete(`/ticket/${id}`)
}

export function handleTicketAction(id, data) {
  return request.post(`/ticket/${id}/action`, data)
}

export function addComment(id, content) {
  return request.post(`/ticket/${id}/comment`, { content })
}

// ============ 审批 ============
export function pageApproval(params) {
  return request.get('/approval/page', { params })
}

export function getApproval(id) {
  return request.get(`/approval/${id}`)
}

export function submitApproval(data) {
  return request.post('/approval', data)
}

export function auditApproval(data) {
  return request.post('/approval/audit', data)
}

export function cancelApproval(id) {
  return request.post(`/approval/${id}/cancel`)
}

export function getTodoCount() {
  return request.get('/approval/todo/count')
}

// ============ 通知 ============
export function pageNotice(params) {
  return request.get('/notice/page', { params })
}

export function getUnreadCount() {
  return request.get('/notice/unread')
}

export function markRead(id) {
  return request.post(`/notice/${id}/read`)
}

export function markAllRead() {
  return request.post('/notice/read-all')
}

// ============ 系统管理 ============
export function pageUser(params) {
  return request.get('/system/user/page', { params })
}

export function getUserRoles(id) {
  return request.get(`/system/user/${id}/roles`)
}

export function createUser(data) {
  return request.post('/system/user', data)
}

export function updateUser(data) {
  return request.put('/system/user', data)
}

export function deleteUser(id) {
  return request.delete(`/system/user/${id}`)
}

export function resetPassword(id, password) {
  return request.post(`/system/user/${id}/reset`, { password })
}

/**
 * 查询启用中的用户（选择审批人/指派人用）。
 *
 * <p>注意：这里用的是 /common/users 而不是 /system/user/list ——
 * 后者属于「用户管理」模块，需要 system:user 权限，只有管理员能访问。
 * 发起审批是所有员工的日常操作，如果依赖管理接口，
 * 普通员工会因为403 导致审批人下拉框「无数据」，进而无法发起审批。</p>
 */
export function listEnabledUsers() {
  return request.get('/common/users')
}

export function listEnabledRoles() {
  return request.get('/system/user/roles')
}

export function pageOperLog(params) {
  return request.get('/system/log/page', { params })
}