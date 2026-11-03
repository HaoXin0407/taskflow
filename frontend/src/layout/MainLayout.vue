<template>
  <el-container class="layout-container">
    <!-- ============ 左侧菜单 ============ -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <el-icon :size="24"><Platform /></el-icon>
        <span v-show="!collapsed" class="logo-text">TaskFlow</span>
      </div>

      <el-menu
        :default-active="$route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#1f2937"
        text-color="#c0c4cc"
        active-text-color="#409eff"
        router
      >
        <template v-for="item in menuRoutes" :key="item.path">
          <el-menu-item :index="'/' + item.path">
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <template #title>
              <span>{{ item.meta.title }}</span>
              <el-badge
                v-if="item.path === 'approval/list' && todoCount > 0"
                :value="todoCount"
                class="todo-badge"
              />
            </template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- ============ 顶栏 ============ -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- 未读通知铃铛 -->
          <el-tooltip content="通知中心">
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
              <el-icon class="header-icon" @click="goNotice">
                <Bell />
              </el-icon>
            </el-badge>
          </el-tooltip>

          <!-- 用户下拉 -->
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="30" class="avatar">
                {{ (userStore.userInfo.nickname || 'U').charAt(0) }}
              </el-avatar>
              <span class="username">{{ userStore.userInfo.nickname || userStore.userInfo.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><Setting /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- ============ 内容区 ============ -->
      <el-main class="main">
        <router-view v-slot="{ Component, route }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMenuRoutes } from '@/router'
import { useUserStore } from '@/store/user'
import { getUnreadCount, getTodoCount } from '@/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const unreadCount = ref(0)
const todoCount = ref(0)

/**
 * 根据权限过滤出可见菜单。
 * 路由已全部注册，这里只决定侧边栏显示哪些菜单项。
 */
const menuRoutes = computed(() => getMenuRoutes(userStore.roles, userStore.permissions))

function goNotice() {
  router.push('/notice/list')
}

async function handleCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

async function refreshBadges() {
  try {
    const [unread, todo] = await Promise.all([getUnreadCount(), getTodoCount()])
    unreadCount.value = unread.data || 0
    todoCount.value = todo.data || 0
  } catch (e) {
    // 角标刷新失败不影响主流程，静默处理
  }
}

onMounted(() => {
  // 刷新页面后：先用 localStorage 缓存渲染，再向服务端校准权限
  if (!userStore.userInfo.username) {
    userStore.fetchUserInfo().catch(() => {})
  }
  refreshBadges()
  // 每 60 秒刷新一次角标，实现消息提醒
  setInterval(refreshBadges, 60000)
})
</script>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
}

.aside {
  background-color: #1f2937;
  transition: width 0.28s;
  overflow-x: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  background-color: #111827;
}

.logo-text {
  white-space: nowrap;
}

.aside :deep(.el-menu) {
  border-right: none;
}

.aside :deep(.el-menu-item:hover) {
  background-color: #263445 !important;
}

.aside :deep(.el-menu-item.is-active) {
  background-color: #409eff !important;
}

.header {
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 20px;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}

.header-icon {
  font-size: 18px;
  cursor: pointer;
  color: #606266;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
}

.avatar {
  background-color: #409eff;
}

.username {
  font-size: 14px;
}

.main {
  background-color: #f0f2f5;
  padding: 16px;
  overflow-y: auto;
}

.todo-badge {
  margin-left: 8px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>