const TOKEN_KEY = 'taskflow_token'

/**
 * Token 读写工具。
 * <p>用 localStorage 而非 sessionStorage：关掉浏览器再打开依然保持登录，
 * 但刷新页面不会丢失（sessionStorage 刷新会丢）。</p>
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  return localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  return localStorage.removeItem(TOKEN_KEY)
}