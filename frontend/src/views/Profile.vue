<template>
  <div>
    <el-row :gutter="16">
      <!-- ============ 个人信息 ============ -->
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>个人信息</template>

          <div class="user-header">
            <el-avatar :size="64" class="big-avatar">
              {{ (userStore.userInfo.nickname || 'U').charAt(0) }}
            </el-avatar>
            <h3 class="user-name">{{ userStore.userInfo.nickname }}</h3>
            <p class="user-dept">{{ userStore.userInfo.dept }}</p>
          </div>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">
              {{ userStore.userInfo.username }}
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag v-for="r in userStore.roles" :key="r" size="small" class="mr-4">
                {{ ROLE_MAP[r] || r }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="权限数">
              {{ userStore.permissions.length }} 项
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- ============ 修改密码 ============ -->
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <template #header>修改密码</template>

          <el-form
            ref="pwdRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-width="100px"
            style="max-width: 460px"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="submitPassword">
                确认修改
              </el-button>
              <el-button @click="resetForm">重 置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- ============ 我的权限清单 ============ -->
        <el-card shadow="never" class="mt-16">
          <template #header>我的权限清单</template>
          <el-alert
            type="info"
            :closable="false"
            title="以下权限由后端根据角色计算并返回，前端据此控制菜单与按钮显示"
            style="margin-bottom: 14px"
          />
          <el-tag
            v-for="perm in userStore.permissions"
            :key="perm"
            size="small"
            class="perm-tag"
          >
            {{ perm }}
          </el-tag>
          <el-empty v-if="!userStore.permissions.length" description="暂无权限" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword, getUserInfo } from '@/api'
import { useUserStore } from '@/store/user'
import { removeToken } from '@/utils/auth'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

const ROLE_MAP = { ADMIN: '超级管理员', MANAGER: '部门主管', USER: '普通员工' }

const submitting = ref(false)
const pwdRef = ref()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function submitPassword() {
  const valid = await pwdRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    // 密码变更后强制重新登录
    removeToken()
    userStore.logout()
    setTimeout(() => router.push('/login'), 800)
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  pwdRef.value?.resetFields()
}

onMounted(() => {
  if (!userStore.userInfo.username) {
    getUserInfo()
      .then((r) => {
        userStore.userInfo = r.data
      })
      .catch(() => {})
  }
})
</script>

<style scoped>
.user-header {
  text-align: center;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid #ebeef5;
}

.big-avatar {
  background-color: #409eff;
  font-size: 26px;
}

.user-name {
  margin: 10px 0 4px;
  font-size: 17px;
}

.user-dept {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.mt-16 {
  margin-top: 16px;
}

.perm-tag {
  margin: 0 6px 6px 0;
}

.mr-4 {
  margin-right: 4px;
}
</style>