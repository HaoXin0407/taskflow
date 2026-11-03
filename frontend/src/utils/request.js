import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getToken, removeToken } from '@/utils/auth'

/**
 * Axios 实例封装。
 *
 * 面试常问：「为什么不用 axios 默认实例，要自己封装？」
 * 答：统一处理三件事 —— 1) 请求拦截器自动带 token；2) 响应拦截器统一解包 Result；
 *    3) 401 自动跳登录、错误消息统一提示。业务代码里就只需写 api.get/post。
 */
const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' }
})

// ---------------- 请求拦截器 ----------------
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ---------------- 响应拦截器 ----------------
service.interceptors.response.use(
  (response) => {
    const res = response.data

    // 文件流等二进制响应直接放行
    if (response.config.responseType === 'blob') {
      return res
    }

    // 后端统一返回 { code, msg, data }
    if (res.code === 200) {
      return res
    }

    // 401：登录过期
    if (res.code === 401 || res.code === 1003 || res.code === 1004) {
      ElMessage.error(res.msg || '登录已过期，请重新登录')
      removeToken()
      // 避免在登录页重复跳转
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
      return Promise.reject(new Error(res.msg))
    }

    // 其他业务错误统一弹提示
    ElMessage.error(res.msg || '操作失败')
    return Promise.reject(new Error(res.msg || '操作失败'))
  },
  (error) => {
    let msg = '网络异常，请稍后重试'
    if (error.code === 'ECONNABORTED') {
      msg = '请求超时，请检查网络'
    } else if (error.response) {
      const status = error.response.status
      const statusMap = {
        400: '请求参数有误',
        401: '未登录或登录已过期',
        403: '没有访问权限',
        404: '请求的资源不存在',
        500: '服务器内部错误',
        502: '网关异常',
        503: '服务不可用'
      }
      msg = statusMap[status] || `请求失败（${status}）`
      // 500 说明后端挂了，提示更明确
      if (status >= 500) {
        msg = '后端服务异常，请确认 Spring Boot 已启动（端口 8080）'
      }
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service