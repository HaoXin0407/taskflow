<template>
  <div>
    <el-card class="search-card" shadow="never">
      <el-tabs v-model="query.scope" @tab-change="handleSearch">
        <el-tab-pane name="mine">
          <template #label>我发起的</template>
        </el-tab-pane>
        <el-tab-pane name="todo">
          <template #label>
            <span>
              待我审批
              <el-badge v-if="todoCount > 0" :value="todoCount" class="ml-4" />
            </span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="done" label="我处理过的" />
        <el-tab-pane v-if="userStore.isAdmin()" name="all" label="全部单据" />
      </el-tabs>

      <el-form :model="query" inline style="margin-top: 8px">
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="标题 / 单号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(v, k) in STATUS_MAP" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.bizType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(v, k) in TYPE_MAP" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button
            v-has-perm="'approval:submit'"
            type="success"
            @click="handleCreate"
          >
            <el-icon><Plus /></el-icon>&nbsp;发起审批
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="approvalNo" label="审批单号" width="170" />
        <el-table-column prop="title" label="审批标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="业务类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ TYPE_MAP[row.bizType] || row.bizType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            {{ row.amount ? '¥' + row.amount : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="applicantName" label="申请人" width="100" />
        <el-table-column label="审批进度" width="130" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="progressPercent(row)"
              :status="progressStatus(row)"
              :format="() => `${row.currentNode}/${row.totalNode}`"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ STATUS_MAP[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleDetail(row)">详情</el-button>
            <template v-if="row.canAudit">
              <el-button link type="success" size="small" @click="openAudit(row, true)">通过</el-button>
              <el-button link type="danger" size="small" @click="openAudit(row, false)">驳回</el-button>
            </template>
            <el-button
              v-else-if="row.status === 0 && row.applicantId === userStore.userInfo.userId"
              link
              type="warning"
              size="small"
              @click="handleCancel(row)"
            >
              撤销
            </el-button>
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

    <!-- ============ 发起审批 ============ -->
    <el-dialog v-model="submitVisible" title="发起审批" width="600px" destroy-on-close>
      <el-form ref="submitRef" :model="submitForm" :rules="submitRules" label-width="90px">
        <el-form-item label="审批标题" prop="title">
          <el-input v-model="submitForm.title" placeholder="如：10月14日加班申请" maxlength="100" />
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-radio-group v-model="submitForm.bizType">
            <el-radio-button v-for="(v, k) in TYPE_MAP" :key="k" :value="k">{{ v }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="submitForm.bizType === 'PURCHASE'" label="金额">
          <el-input-number v-model="submitForm.amount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="审批人" prop="approverIds">
          <el-select
            v-model="submitForm.approverIds"
            multiple
            placeholder="按点击顺序即为审批顺序，可设置多级审批"
            style="width: 100%"
          >
            <!--
              字段名说明：/common/users 接口返回的是 userId（不是 id），
              用错字段名会导致选项值为 undefined，表现为「选不上人」。
              自己不能作为审批人（后端 ApprovalService.submit 也会校验），故 disabled。
            -->
            <el-option
              v-for="u in approverCandidates"
              :key="u.userId"
              :label="`${u.nickname} (${u.dept || '无部门'})`"
              :value="u.userId"
              :disabled="u.userId === userStore.userInfo.userId"
            />
          </el-select>
          <div class="tip">
            提示：可多选形成多级串行审批，如「主管 → 总监」；不能选择自己
          </div>
        </el-form-item>
        <el-form-item label="申请事由" prop="reason">
          <el-input
            v-model="submitForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请详细说明申请原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSubmit">提 交 审 批</el-button>
      </template>
    </el-dialog>

    <!-- ============ 审批操作 ============ -->
    <el-dialog
      v-model="auditVisible"
      :title="auditPass ? '通过审批' : '驳回审批'"
      width="520px"
    >
      <el-alert
        :title="`审批单：${currentRow?.approvalNo} ${currentRow?.title}`"
        :type="auditPass ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <el-form label-width="80px" class="mt-12">
        <el-form-item label="审批意见">
          <el-input
            v-model="auditComment"
            type="textarea"
            :rows="4"
            :placeholder="auditPass ? '同意（选填）' : '请填写驳回原因（必填）'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取 消</el-button>
        <el-button
          :type="auditPass ? 'success' : 'danger'"
          :loading="submitting"
          @click="confirmAudit"
        >
          {{ auditPass ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 审批详情 ============ -->
    <el-drawer v-model="detailVisible" title="审批详情" size="560px">
      <template v-if="detailData.approval">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="审批单号">{{ detailData.approval.approvalNo }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detailData.approval.title }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detailData.approval.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detailData.approval.status)" size="small">
              {{ detailData.approval.statusDesc }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="事由">{{ detailData.approval.reason }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detailData.approval.createTime }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="node-title">审批链（共 {{ detailData.nodes?.length || 0 }} 级）</h4>
        <el-steps direction="vertical" :active="-1" finish-status="success">
          <el-step
            v-for="node in detailData.nodes"
            :key="node.id"
            :title="node.nodeName"
            :description="`审批人：${node.approverName || '未知'}`"
            :status="nodeStepStatus(node)"
          >
            <template #extra>
              <div v-if="node.status !== 0" class="node-result">
                <el-tag :type="node.status === 1 ? 'success' : 'danger'" size="small">
                  {{ node.status === 1 ? '已通过' : '已驳回' }}
                </el-tag>
                <span class="node-time">{{ node.approveTime }}</span>
                <div v-if="node.comment" class="node-comment">意见：{{ node.comment }}</div>
              </div>
              <div v-else>
                <el-tag type="warning" size="small">待审批</el-tag>
              </div>
            </template>
          </el-step>
        </el-steps>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageApproval,
  submitApproval,
  auditApproval,
  cancelApproval,
  getApproval,
  listEnabledUsers,
  getTodoCount
} from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const STATUS_MAP = { 0: '审批中', 1: '已通过', 2: '已驳回', 3: '已撤销' }
const TYPE_MAP = {
  LEAVE: '请假',
  OVERTIME: '加班',
  PURCHASE: '采购报销',
  REOPEN: '重开申请'
}

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const todoCount = ref(0)
const approverCandidates = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  scope: 'mine',
  keyword: '',
  status: null,
  bizType: null
})

// 提交弹窗
const submitVisible = ref(false)
const submitRef = ref()
const submitForm = reactive({
  title: '',
  bizType: 'LEAVE',
  reason: '',
  amount: 0,
  approverIds: []
})

// 审批弹窗
const auditVisible = ref(false)
const auditPass = ref(true)
const auditComment = ref('')
const currentRow = ref(null)

// 详情抽屉
const detailVisible = ref(false)
const detailData = ref({})

const submitRules = {
  title: [{ required: true, message: '请输入审批标题', trigger: 'blur' }],
  bizType: [{ required: true, message: '请选择业务类型', trigger: 'change' }],
  approverIds: [
    { type: 'array', required: true, message: '请至少选择一位审批人', trigger: 'change' }
  ],
  reason: [{ required: true, message: '请填写申请事由', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageApproval(query)
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadTodoCount() {
  try {
    const res = await getTodoCount()
    todoCount.value = res.data || 0
  } catch (e) {}
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

/**
 * 加载审批人候选。
 *
 * <p>每次打开发起审批弹窗都重新拉取，而不是只拉一次 ——
 * 早期实现是 `if (!approverCandidates.value.length)` 才请求，
 * 一旦首次请求失败（比如 403），候选列表就永远为空且不再重试，
 * 用户只能看到「无数据」却不知道为什么。</p>
 */
async function loadApproverCandidates() {
  try {
    const r = await listEnabledUsers()
    // 接口返回字段是 userId，这里统一成 userId 供模板使用
    approverCandidates.value = (r.data || []).map((u) => ({
      userId: u.userId,
      nickname: u.nickname,
      dept: u.dept,
      username: u.username
    }))
  } catch (e) {
    approverCandidates.value = []
    // 拦截器已统一弹错误提示，这里只做兜底，避免弹窗里空着没提示
    ElMessage.warning('审批人列表加载失败，请检查权限或稍后重试')
  }
}

function handleCreate() {
  Object.assign(submitForm, {
    title: '',
    bizType: 'LEAVE',
    reason: '',
    amount: 0,
    approverIds: []
  })
  loadApproverCandidates()
  submitVisible.value = true
}

async function confirmSubmit() {
  const valid = await submitRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await submitApproval(submitForm)
    ElMessage.success('审批提交成功')
    submitVisible.value = false
    query.scope = 'mine'
    query.pageNum = 1
    loadData()
    loadTodoCount()
  } finally {
    submitting.value = false
  }
}

function openAudit(row, pass) {
  currentRow.value = row
  auditPass.value = pass
  auditComment.value = ''
  auditVisible.value = true
}

async function confirmAudit() {
  if (!auditPass.value && !auditComment.value.trim()) {
    ElMessage.warning('驳回时必须填写原因')
    return
  }

  submitting.value = true
  try {
    await auditApproval({
      approvalId: currentRow.value.id,
      pass: auditPass.value,
      comment: auditComment.value
    })
    ElMessage.success(auditPass.value ? '已通过' : '已驳回')
    auditVisible.value = false
    loadData()
    loadTodoCount()
  } finally {
    submitting.value = false
  }
}

async function handleCancel(row) {
  await ElMessageBox.confirm('确定撤销该审批单吗？', '提示', { type: 'warning' })
  await cancelApproval(row.id)
  ElMessage.success('已撤销')
  loadData()
}

async function handleDetail(row) {
  const res = await getApproval(row.id)
  detailData.value = res.data || {}
  detailVisible.value = true
}

function progressPercent(row) {
  if (row.status !== 0) return 100
  return Math.round(((row.currentNode - 1) / row.totalNode) * 100)
}

function progressStatus(row) {
  if (row.status === 1) return 'success'
  if (row.status === 2) return 'exception'
  return undefined
}

function statusTagType(s) {
  return { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }[s] || 'info'
}

function nodeStepStatus(node) {
  if (node.status === 1) return 'success'
  if (node.status === 2) return 'error'
  return 'process'
}

onMounted(() => {
  loadData()
  loadTodoCount()
})
</script>

<style scoped>
.el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.mt-12 {
  margin-top: 12px;
}

.tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

.node-title {
  margin: 20px 0 14px;
  font-size: 15px;
  font-weight: 600;
}

.node-result {
  margin-top: 4px;
}

.node-time {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.node-comment {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
}

.ml-4 {
  margin-left: 4px;
}
</style>