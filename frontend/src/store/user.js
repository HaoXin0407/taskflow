import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getUserInfo } from '@/api'
import { getToken, setToken, removeToken } from '@/utils/auth'

/** localStorage 中缓存权限信息的 key */
const PERM_CACHE_KEY = 'taskflow_perms'
const INFO_CACHE_KEY = 'taskflow_userinfo'

/**
 * 读取本地缓存（刷新页面后 Pinia 状态会丢失，用它恢复，避免侧边栏空白闪烁）
 */
function readCache(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : fallback
  } catch (e) {
    return fallback
  }
}

/**
 * 用户状态管理（Pinia）。
 *
 * <p>面试可讲：为什么用 Pinia 而不是 Vuex？
 * Pinia 是 Vue3 官方推荐，API 更简洁（不需要 mutations），对 TS 友好、体积小。</p>
 *
 * <p>为什么要有 localStorage 缓存？
 * 刷新页面时 Pinia 会重置，如果不缓存，用户会看到登录信息闪烁、侧边栏短暂消失。
 * 缓存后在 router.beforeEach 里先恢复，再异步校准。</p>
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const userInfo = ref(readCache(INFO_CACHE_KEY, {}))
  const roles = ref(readCache(PERM_CACHE_KEY, {}).roles || [])
  const permissions = ref(readCache(PERM_CACHE_KEY, {}).permissions || [])

  /** 登录 */
  async function login(loginForm) {
    const res = await loginApi(loginForm)
    token.value = res.data.token
    setToken(res.data.token)

    userInfo.value = {
      userId: res.data.userId,
      username: res.data.username,
      nickname: res.data.nickname,
      dept: res.data.dept,
      avatar: res.data.avatar
    }
    roles.value = res.data.roles || []
    permissions.value = res.data.permissions || []
    persist()
    return res
  }

  /** 刷新用户信息（页面刷新后恢复状态） */
  async function fetchUserInfo() {
    const res = await getUserInfo()
    userInfo.value = res.data
    persist()
    return res
  }

  /** 退出登录 */
  function logout() {
    token.value = ''
    userInfo.value = {}
    roles.value = []
    permissions.value = []
    removeToken()
    localStorage.removeItem(PERM_CACHE_KEY)
    localStorage.removeItem(INFO_CACHE_KEY)
  }

  /** 是否为管理员 */
  function isAdmin() {
    return roles.value.includes('ADMIN')
  }

  /**
   * 按钮级权限判断：v-has-perm="'ticket:add'" 指令底层调它。
   */
  function hasPermission(code) {
    if (isAdmin()) return true
    return permissions.value.includes(code)
  }

  /** 持久化到 localStorage */
  function persist() {
    localStorage.setItem(PERM_CACHE_KEY, JSON.stringify({
      roles: roles.value,
      permissions: permissions.value
    }))
    localStorage.setItem(INFO_CACHE_KEY, JSON.stringify(userInfo.value))
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    login,
    fetchUserInfo,
    logout,
    isAdmin,
    hasPermission
  }
})