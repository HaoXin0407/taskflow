import router from '@/router'
import { useUserStore } from '@/store/user'

/**
 * 全局权限指令：v-has-perm="'system:user'"
 *
 * <p>用指令而不是 v-if 判断按钮，是因为指令可以直接操作 DOM，
 * 无权限的按钮直接从 DOM 中移除，比隐藏更安全。</p>
 */
export default {
  mounted(el, binding) {
    const userStore = useUserStore()
    const value = binding.value
    if (value && !userStore.hasPermission(value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}

/**
 * 另一种：v-role="'ADMIN'"
 */
export const roleDirective = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const roles = userStore.roles || []
    if (binding.value && !roles.includes(binding.value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}