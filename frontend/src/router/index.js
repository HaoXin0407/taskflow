import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

/**
 * 路由配置。
 *
 * <p><b>设计要点</b></p>
 * <ul>
 *   <li>所有需要权限的页面都是 {@link MainLayout} 的 children，
 *       这样切换页面时布局（侧边栏、顶栏）不会重新渲染</li>
 *   <li>权限控制分两层：
 *       <b>菜单层</b>（MainLayout 根据 permissions 过滤侧边栏，不显示无权限菜单）+
 *       <b>接口层</b>（后端 @PreAuthorize 真正拦截）</li>
 *   <li>路由守卫处理「未登录跳登录」和「标题设置」</li>
 * </ul>
 *
 * <p><b>为什么不用 addRoute 动态注册？</b></p>
 * <p>动态注册要求每个业务页面都写成 MainLayout 的 children，
 * 但 children 里嵌套两层（布局 → 页面）时路径拼接容易出错，
 * 而且刷新页面时动态注册的路由会丢失，必须重新计算。
 * 本项目规模下，把路由静态注册、菜单按权限过滤，是更稳妥的选择 ——
 * <b>真正的安全边界在后端接口鉴权</b>，前端隐藏菜单只是体验优化。</p>
 */

/** MainLayout 下的业务页面路由 */
export const businessRoutes = [
  {
    path: 'dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '数据看板', icon: 'DataAnalysis', permissions: [] }
  },
  {
    path: 'ticket/list',
    name: 'TicketList',
    component: () => import('@/views/ticket/List.vue'),
    meta: { title: '工单列表', icon: 'Tickets', permissions: ['ticket:list'] }
  },
  {
    path: 'ticket/detail/:id',
    name: 'TicketDetail',
    component: () => import('@/views/ticket/Detail.vue'),
    // 从列表页跳转，不显示在侧边栏
    meta: { title: '工单详情', hidden: true }
  },
  {
    path: 'approval/list',
    name: 'ApprovalList',
    component: () => import('@/views/approval/List.vue'),
    meta: { title: '审批中心', icon: 'Stamp', permissions: ['approval:list'] }
  },
  {
    path: 'notice/list',
    name: 'NoticeList',
    component: () => import('@/views/notice/List.vue'),
    meta: { title: '通知中心', icon: 'Bell', permissions: ['notice:list'] }
  },
  {
    path: 'system/user',
    name: 'SystemUser',
    component: () => import('@/views/system/User.vue'),
    meta: { title: '用户管理', icon: 'User', permissions: ['system:user'] }
  },
  {
    path: 'system/log',
    name: 'SystemLog',
    component: () => import('@/views/system/Log.vue'),
    meta: { title: '操作日志', icon: 'Document', permissions: ['system:log'] }
  },
  {
    path: 'profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { title: '个人中心', icon: 'Setting', hidden: true }
  }
]

/**
 * 静态路由表。
 * <p>业务页面统一挂在 MainLayout 下，保证布局不重复渲染。</p>
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: businessRoutes
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/Forbidden.vue'),
    meta: { title: '无权访问' }
  },
  {
    // 404 兜底必须放在最后
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/**
 * 路由守卫。
 *
 * <p>刷新页面时 Pinia 状态会丢失，但权限已缓存在 localStorage，
 * 侧边栏能正常渲染；这里只负责登录态检查与标题设置。</p>
 */
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - TaskFlow` : 'TaskFlow'

  const token = getToken()

  // 未登录：只允许访问登录页
  if (!token) {
    if (to.path === '/login') {
      next()
    } else {
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
    return
  }

  // 已登录还去登录页：踢回首页
  if (to.path === '/login') {
    next('/dashboard')
    return
  }

  next()
})

export default router

/**
 * 根据用户权限过滤出可见菜单（供 MainLayout 生成侧边栏）。
 * 路由本身已全部注册，这里只控制「显示哪些菜单项」。
 */
export function getMenuRoutes(userRoles, userPermissions) {
  const isAdmin = userRoles?.includes('ADMIN')
  return businessRoutes
    .filter((r) => !r.meta.hidden)
    .filter((r) => {
      const perms = r.meta.permissions
      if (!perms || !perms.length) return true   // 无权限要求，所有人可见
      if (isAdmin) return true                     // 超管全放行
      return perms.some((p) => userPermissions?.includes(p))
    })
}