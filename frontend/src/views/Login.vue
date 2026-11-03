<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <el-icon :size="42" color="#409eff"><Platform /></el-icon>
        <h1>TaskFlow</h1>
        <p>企业级智能工单流转与审批协同平台</p>
      </div>

      <el-tabs v-model="activeTab" class="login-tabs">
        <!-- ============ 登录 ============ -->
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginRef" :model="loginForm" :rules="loginRules" size="large">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" clearable>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                show-password
                @keyup.enter="handleLogin"
              >
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              size="large"
              :loading="loading"
              @click="handleLogin"
            >
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form>

          <!-- 演示账号快捷填充 -->
          <div class="demo-accounts">
            <p class="demo-title">演示账号（点击快速填充，密码均为 123456）：</p>
            <div class="demo-list">
              <el-tag
                v-for="acc in demoAccounts"
                :key="acc.username"
                class="demo-tag"
                :type="acc.type"
                effect="plain"
                @click="fillAccount(acc.username)"
              >
                {{ acc.label }}
              </el-tag>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 注册 ============ -->
        <el-tab-pane label="注册" name="register">
          <el-form ref="registerRef" :model="registerForm" :rules="registerRules" size="large">
            <el-form-item prop="username">
              <el-input v-model="registerForm.username" placeholder="用户名（字母开头，3-20位）" clearable />
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="昵称" clearable />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="密码（6-20位）" show-password />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="确认密码"
                show-password
              />
            </el-form-item>
            <el-form-item prop="email">
              <el-input v-model="registerForm.email" placeholder="邮箱（选填）" clearable />
            </el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              size="large"
              :loading="loading"
              @click="handleRegister"
            >
              注 册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { register } from '@/api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)
const loginRef = ref()
const registerRef = ref()

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  email: ''
})

const demoAccounts = [
  { label: '管理员 admin', username: 'admin', type: 'danger' },
  { label: '主管 manager', username: 'manager', type: 'warning' },
  { label: '员工 zhangsan', username: 'zhangsan', type: 'success' }
]

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,19}$/,
      message: '需以字母开头，可含字母数字下划线，长度3-20',
      trigger: 'blur'
    }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function fillAccount(username) {
  loginForm.username = username
  loginForm.password = '123456'
}

async function handleLogin() {
  const valid = await loginRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(loginForm)
    ElMessage.success('登录成功')
    // 登录后跳回原来想访问的页面
    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  const valid = await registerRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register(registerForm)
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.username = registerForm.username
    loginForm.password = registerForm.password
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3c88 0%, #2a5298 50%, #409eff 100%);
}

.login-box {
  width: 420px;
  padding: 36px 40px 28px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}

.login-header {
  text-align: center;
  margin-bottom: 20px;

  h1 {
    margin: 10px 0 6px;
    font-size: 28px;
    color: #1f2d3d;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: #909399;
  }
}

.login-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 20px;
  }
}

.login-btn {
  width: 100%;
  margin-top: 6px;
}

.demo-accounts {
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px dashed #e4e7ed;
}

.demo-title {
  font-size: 12px;
  color: #909399;
  margin: 0 0 8px;
}

.demo-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.demo-tag {
  cursor: pointer;
}
</style>