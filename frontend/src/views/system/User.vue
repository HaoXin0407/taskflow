<template>
  <div>
    <el-card class="search-card" shadow="never">
      <el-form :model="query" inline>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="用户名 / 昵称 / 邮箱"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button type="success" @click="handleCreate">
            <el-icon><Plus /></el-icon>&nbsp;新增用户
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="用户名" width="130">
          <template #default="{ row }">
            {{ row.username }}
            <el-tag v-if="isMe(row)" size="small" type="primary" class="ml-4">我</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="dept" label="部门" width="110" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="160" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="handleReset(row)">重置密码</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <!-- ============ 新增/编辑 ============ -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="580px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '留空表示不修改密码' : '初始密码，至少 6 位'"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.dept" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%" placeholder="请选择角色">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSubmit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageUser,
  createUser,
  updateUser,
  deleteUser,
  resetPassword,
  getUserRoles,
  listEnabledRoles
} from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const roles = ref([])

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: null })
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  username: '',
  password: '',
  nickname: '',
  dept: '',
  email: '',
  phone: '',
  status: 1,
  roleIds: []
})

const formRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,19}$/, message: '需以字母开头，3-20位', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [
    {
      validator: (rule, value, callback) => {
        if (!form.id && (!value || value.length < 6)) {
          callback(new Error('新增用户时密码至少 6 位'))
        } else if (value && value.length < 6) {
          callback(new Error('密码至少 6 位'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageUser(query)
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleCreate() {
  Object.assign(form, {
    id: null, username: '', password: '', nickname: '',
    dept: '', email: '', phone: '', status: 1, roleIds: []
  })
  dialogVisible.value = true
}

async function handleEdit(row) {
  Object.assign(form, {
    id: row.id, username: row.username, password: '', nickname: row.nickname,
    dept: row.dept, email: row.email, phone: row.phone, status: row.status, roleIds: []
  })
  // 查出该用户已分配的角色
  const res = await getUserRoles(row.id)
  form.roleIds = res.data || []
  dialogVisible.value = true
}

async function confirmSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (form.id) {
      await updateUser(form)
      ElMessage.success('修改成功')
    } else {
      await createUser(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定删除用户「${row.nickname}」吗？删除后该用户将无法登录。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleReset(row) {
  const { value } = await ElMessageBox.prompt(
    `为用户「${row.nickname}」设置新密码（至少 6 位）：`,
    '重置密码',
    {
      confirmButtonText: '确定重置',
      inputValue: '123456',
      inputPattern: /^.{6,20}$/,
      inputErrorMessage: '密码长度 6-20 位'
    }
  )
  await resetPassword(row.id, value)
  ElMessage.success('密码已重置')
}

function isMe(row) {
  return row.id === userStore.userInfo.userId
}

onMounted(() => {
  loadData()
  listEnabledRoles()
    .then((r) => {
      roles.value = r.data || []
    })
    .catch(() => {})
})
</script>

<style scoped>
.el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.ml-4 {
  margin-left: 4px;
}
</style>