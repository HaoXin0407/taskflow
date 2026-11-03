<template>
  <div>
    <!-- ============ 查询条件 ============ -->
    <el-card class="search-card" shadow="never">
      <el-form :model="query" inline>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="标题 / 内容 / 编号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option v-for="(v, k) in STATUS_MAP" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部类型" clearable style="width: 130px">
            <el-option v-for="(v, k) in TYPE_MAP" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priority" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="(v, k) in PRIORITY_MAP" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="范围">
          <!--
            范围筛选只在「有查看全部工单权限」时才有意义。
            普通员工受数据权限限制，本就只能看到与自己相关的工单，
            给他一个"全部"选项是误导（点了结果也不变，用户会以为功能坏了）。
            所以直接不渲染这个筛选项。
          -->
          <template v-if="canViewAll">
            <!-- @change 让点击单选后立即查询，无需再点「查询」按钮 -->
            <el-radio-group v-model="query.mine" @change="handleSearch">
              <el-radio-button :value="undefined">全部</el-radio-button>
              <el-radio-button :value="true">与我相关</el-radio-button>
            </el-radio-group>
          </template>
          <span v-else class="scope-hint">仅显示与您相关的工单</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>&nbsp;重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ============ 工单列表 ============ -->
    <el-card shadow="never">
      <div class="table-header">
        <div>
          <el-button
            v-has-perm="'ticket:add'"
            type="primary"
            @click="handleCreate"
          >
            <el-icon><Plus /></el-icon>&nbsp;创建工单
          </el-button>
        </div>
        <div class="stat-inline">
          <span>共 <strong>{{ total }}</strong> 条</span>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="ticketNo" label="工单编号" width="170">
          <template #default="{ row }">
            <el-link type="primary" @click="goDetail(row.id)">{{ row.ticketNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.isTimeout === 1" class="timeout-flag">【超时】</span>
            {{ row.title }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.type)" size="small">{{ TYPE_MAP[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="priorityTagType(row.priority)" size="small" effect="dark">
              {{ PRIORITY_MAP[row.priority] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ STATUS_MAP[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="100" />
        <el-table-column label="处理人" width="100">
          <template #default="{ row }">
            {{ row.assigneeName || '未指派' }}
          </template>
        </el-table-column>
        <el-table-column label="评论" width="70" align="center">
          <template #default="{ row }">
            <el-icon><ChatDotRound /></el-icon> {{ row.commentCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row.id)">详情</el-button>
            <el-button
              v-if="row.status === 0"
              v-has-perm="'ticket:handle'"
              link
              type="success"
              size="small"
              @click="handleAction(row, 'ACCEPT')"
            >
              受理
            </el-button>
            <el-button
              v-has-perm="'ticket:delete'"
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <!-- ============ 创建/编辑弹窗 ============ -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入工单标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button v-for="(v, k) in TYPE_MAP" :key="k" :value="k">{{ v }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="form.priority">
            <el-radio-button v-for="(v, k) in PRIORITY_MAP" :key="k" :value="Number(k)">
              {{ v }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="canViewAll" label="处理人">
          <!--
            只有管理岗（管理员/主管）可以直接指定处理人。
            普通员工不显示这个字段 —— 保持原有行为：
            提交后由有受理权限的人认领，避免绕过工单流转流程。
          -->
          <el-select v-model="form.assigneeId" placeholder="不指定则由受理人认领" clearable style="width: 100%">
            <el-option
              v-for="u in userCandidates"
              :key="u.userId"
              :label="`${u.nickname} (${u.dept || '无部门'})`"
              :value="u.userId"
            />
          </el-select>
          <div class="tip">指定后该同事会收到通知，工单直接进入其待办</div>
        </el-form-item>
        <el-form-item label="内容描述" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            placeholder="请详细描述问题现象、复现步骤等，便于处理人快速定位"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ============ 受理弹窗 ============ -->
    <el-dialog v-model="acceptVisible" title="受理工单" width="480px">
      <el-alert
        :title="`确定受理工单 ${currentRow?.ticketNo} 吗？受理后您将成为该工单的处理人。`"
        type="info"
        :closable="false"
        show-icon
      />
      <el-input
        v-model="acceptRemark"
        type="textarea"
        :rows="3"
        placeholder="备注（选填）"
        class="mt-12"
      />
      <template #footer>
        <el-button @click="acceptVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmAccept">确 定 受 理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageTicket, createTicket, deleteTicket, handleTicketAction, listEnabledUsers } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

/**
 * 是否能查看全部工单。
 * 管理员和主管（有 ticket:handle 权限）可以，普通员工只能看与自己相关的。
 * 与后端 TicketService.page() 的数据权限规则保持一致。
 */
const canViewAll = computed(
  () => userStore.isAdmin() || userStore.hasPermission('ticket:handle')
)

const STATUS_MAP = { 0: '待受理', 1: '处理中', 2: '待确认', 3: '已完成', 4: '已关闭', 5: '已驳回' }
const TYPE_MAP = { BUG: '系统缺陷', FEATURE: '功能需求', COMPLAINT: '投诉建议', OTHER: '其他' }
const PRIORITY_MAP = { 1: '紧急', 2: '高', 3: '中', 4: '低' }

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: null,
  type: null,
  priority: null,
  mine: undefined
})

// ---- 创建弹窗 ----
const dialogVisible = ref(false)
const dialogTitle = ref('创建工单')
const formRef = ref()
const form = reactive({
  id: null,
  title: '',
  content: '',
  type: 'BUG',
  priority: 3,
  assigneeId: null
})

/** 可指定的处理人候选（仅管理岗需要） */
const userCandidates = ref([])

/**
 * 加载处理人候选列表。
 * 只在管理岗需要 —— 普通员工的创建表单里没有这个字段。
 */
async function loadUserCandidates() {
  if (!canViewAll.value) return
  try {
    const r = await listEnabledUsers()
    userCandidates.value = (r.data || []).map((u) => ({
      userId: u.userId,
      nickname: u.nickname,
      dept: u.dept
    }))
  } catch (e) {
    userCandidates.value = []
    ElMessage.warning('处理人列表加载失败，可先不指定处理人提交')
  }
}

// ---- 受理弹窗 ----
const acceptVisible = ref(false)
const acceptRemark = ref('')
const currentRow = ref(null)

const formRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容描述', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageTicket(query)
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

function handleReset() {
  query.keyword = ''
  query.status = null
  query.type = null
  query.priority = null
  query.mine = undefined
  query.pageNum = 1
  loadData()
}

function goDetail(id) {
  router.push(`/ticket/detail/${id}`)
}

function handleCreate() {
  dialogTitle.value = '创建工单'
  Object.assign(form, { id: null, title: '', content: '', type: 'BUG', priority: 3, assigneeId: null })
  loadUserCandidates()
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 普通员工不传 assigneeId（表单里也没有这个字段），
    // 管理岗才会带上，实现"创建时直接指派处理人"
    const payload = { ...form }
    if (!canViewAll.value) {
      delete payload.assigneeId
    }
    await createTicket(payload)
    ElMessage.success('工单创建成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleAction(row, action) {
  currentRow.value = row
  if (action === 'ACCEPT') {
    acceptRemark.value = ''
    acceptVisible.value = true
  }
}

async function confirmAccept() {
  submitting.value = true
  try {
    await handleTicketAction(currentRow.value.id, {
      action: 'ACCEPT',
      remark: acceptRemark.value
    })
    ElMessage.success('受理成功')
    acceptVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定删除工单「${row.title}」吗？此操作不可恢复。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  )
  await deleteTicket(row.id)
  ElMessage.success('删除成功')
  loadData()
}

function statusTagType(s) {
  return { 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: 'info', 5: 'danger' }[s] || 'info'
}

function typeTagType(t) {
  return { BUG: 'danger', FEATURE: 'success', COMPLAINT: 'warning', OTHER: 'info' }[t] || 'info'
}

function priorityTagType(p) {
  return { 1: 'danger', 2: 'warning', 3: 'primary', 4: 'info' }[p] || 'info'
}

onMounted(loadData)
</script>

<style scoped>
.stat-inline {
  color: #909399;
  font-size: 13px;
}

.timeout-flag {
  color: #f56c6c;
  font-weight: 600;
}

.el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.mt-12 {
  margin-top: 12px;
}

/* 普通员工不可选「全部」时的说明文案 */
.scope-hint {
  color: #909399;
  font-size: 13px;
  line-height: 32px;
}
</style>