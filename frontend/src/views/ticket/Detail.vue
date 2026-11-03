<template>
  <div v-loading="loading">
    <el-page-header class="page-header" @back="goBack">
      <template #content>
        <span class="header-title">{{ ticket.ticketNo }}</span>
      </template>
      <template #extra>
        <el-button @click="goBack">返 回</el-button>
      </template>
    </el-page-header>

    <el-row :gutter="16" class="mt-16">
      <!-- ============ 左侧：详情 + 评论 ============ -->
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>工单详情</span>
              <div>
                <el-tag :type="statusTagType" size="small">{{ ticket.statusDesc }}</el-tag>
                <el-tag v-if="ticket.isTimeout === 1" type="danger" size="small" class="ml-6">
                  已超时
                </el-tag>
              </div>
            </div>
          </template>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="工单标题" :span="2">
              <span class="title-text">{{ ticket.title }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="工单类型">
              <el-tag :type="typeTagType" size="small">{{ ticket.typeDesc }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="优先级">
              <el-tag type="danger" size="small">{{ ticket.priorityDesc }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建人">{{ ticket.creatorName }}</el-descriptions-item>
            <el-descriptions-item label="处理人">
              {{ ticket.assigneeName || '未指派' }}
            </el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ ticket.dept }}</el-descriptions-item>
            <el-descriptions-item label="处理时限">
              <span :class="{ 'text-danger': isTimeout }">{{ ticket.deadline }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ ticket.createTime }}</el-descriptions-item>
            <el-descriptions-item label="内容描述" :span="2">
              <div class="content-text">{{ ticket.content }}</div>
            </el-descriptions-item>
          </el-descriptions>

          <!-- ============ 操作区：动态显示当前状态允许的动作 ============ -->
          <div class="action-bar">
            <el-button
              v-for="act in availableActions"
              :key="act.code"
              :type="act.type"
              :icon="act.icon"
              @click="openAction(act)"
            >
              {{ act.label }}
            </el-button>
            <el-empty v-if="!availableActions.length" description="当前状态无可执行操作" :image-size="60" />
          </div>
        </el-card>

        <!-- ============ 评论 ============ -->
        <el-card shadow="never" class="mt-16">
          <template #header>
            <div class="card-header">
              <span>评论交流（{{ comments.length }}）</span>
            </div>
          </template>

          <div v-if="!comments.length" class="empty-comments">暂无评论</div>

          <div v-for="c in comments" :key="c.id" class="comment-item">
            <el-avatar :size="34" class="comment-avatar">
              {{ (c.userName || 'U').charAt(0) }}
            </el-avatar>
            <div class="comment-body">
              <div class="comment-meta">
                <span class="comment-name">{{ c.userName }}</span>
                <span class="comment-time">{{ c.createTime }}</span>
              </div>
              <div class="comment-content">{{ c.content }}</div>
            </div>
          </div>

          <div class="comment-input">
            <el-input
              v-model="commentText"
              type="textarea"
              :rows="3"
              placeholder="输入评论内容，与处理人沟通..."
              maxlength="1000"
              show-word-limit
            />
            <el-button type="primary" class="comment-btn" @click="submitComment">
              发表评论
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- ============ 右侧：流转时间线 ============ -->
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>流转轨迹</span>
              <el-tag size="small" type="info">{{ flows.length }} 条记录</el-tag>
            </div>
          </template>

          <el-timeline>
            <el-timeline-item
              v-for="flow in flows"
              :key="flow.id"
              :timestamp="flow.createTime"
              placement="top"
              :type="timelineType(flow.toStatus)"
              :color="timelineColor(flow.toStatus)"
            >
              <div class="timeline-card">
                <div class="timeline-title">
                  {{ flow.actionDesc }}
                  <el-tag size="small" :type="statusTagTypeOf(flow.toStatus)" class="ml-4">
                    {{ flow.statusDesc }}
                  </el-tag>
                </div>
                <div class="timeline-operator">操作人：{{ flow.operatorName }}</div>
                <div v-if="flow.remark" class="timeline-remark">{{ flow.remark }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 动作弹窗 ============ -->
    <el-dialog v-model="actionVisible" :title="currentAction?.label" width="520px">
      <el-form label-width="80px">
        <el-form-item label="工单">
          <span>{{ ticket.ticketNo }} · {{ ticket.title }}</span>
        </el-form-item>
        <el-form-item v-if="currentAction?.code === 'TRANSFER'" label="新处理人" required>
          <el-select v-model="transferUserId" placeholder="请选择处理人" style="width: 100%">
            <!--
              字段名必须是 userId：/common/users 接口返回的就是 userId。
              之前误用 u.id，导致选项值为 undefined，表现为"选不上人"，
              进而 confirmAction 里的校验直接拦下，转派失败。
            -->
            <el-option
              v-for="u in users"
              :key="u.userId"
              :label="`${u.nickname} (${u.dept || '未分配部门'})`"
              :value="u.userId"
            />
          </el-select>
          <div v-if="!users.length" class="tip">正在加载人员列表…</div>
        </el-form-item>
        <el-form-item :label="currentAction?.code === 'SUBMIT' ? '处理说明' : '备注'">
          <el-input
            v-model="actionRemark"
            type="textarea"
            :rows="4"
            :placeholder="
              currentAction?.code === 'SUBMIT'
                ? '请说明处理结果，提交后将由提单人确认验收'
                : '请输入备注（选填）'
            "
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmAction">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTicket, handleTicketAction, addComment, listEnabledUsers } from '@/api'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const ticketId = route.params.id

const loading = ref(false)
const submitting = ref(false)
const ticket = ref({})
const flows = ref([])
const comments = ref([])
const users = ref([])
const commentText = ref('')

const actionVisible = ref(false)
const currentAction = ref(null)
const actionRemark = ref('')
const transferUserId = ref(null)

const isTimeout = computed(() => ticket.value.isTimeout === 1)

/**
 * 根据工单当前状态 + 当前用户角色，计算「我」能执行哪些动作。
 *
 * 这是前后端权限的双重校验：后端 TicketService 会再校验一遍，
 * 前端这样写只是为了不显示用户点了会报错的无权限按钮。
 */
const availableActions = computed(() => {
  const s = ticket.value.status
  const isAdmin = userStore.isAdmin()
  const canHandle = isAdmin || userStore.hasPermission('ticket:handle')
  const isAssignee = ticket.value.assigneeId === userStore.userInfo.userId
  const isCreator = ticket.value.creatorId === userStore.userInfo.userId
  const list = []

  const push = (code, label, type, icon) => list.push({ code, label, type, icon })

  switch (s) {
    case 0: // 待受理
      if (canHandle) push('ACCEPT', '受理工单', 'success', 'Select')
      if (canHandle) push('CLOSE', '关闭工单', 'info', 'CircleClose')
      break
    case 1: // 处理中
      if (isAssignee || isAdmin) push('SUBMIT', '提交处理结果', 'primary', 'Promotion')
      if (canHandle) push('TRANSFER', '转派他人', 'warning', 'Switch')
      if (isAssignee || isAdmin) push('REJECT', '驳回', 'danger', 'CloseBold')
      break
    case 2: // 待确认
      if (isCreator || isAdmin) push('CONFIRM', '确认验收', 'success', 'CircleCheck')
      if (isCreator || isAdmin) push('REJECT', '驳回打回', 'danger', 'CloseBold')
      break
    case 3: // 已完成
      if (canHandle) push('CLOSE', '关闭归档', 'info', 'CircleClose')
      if (canHandle) push('REOPEN', '重新打开', 'warning', 'RefreshLeft')
      break
    case 4: // 已关闭
    case 5: // 已驳回
      if (canHandle) push('REOPEN', '重新打开', 'warning', 'RefreshLeft')
      break
  }
  return list
})

const statusTagType = computed(() => statusTagTypeOf(ticket.value.status))
const typeTagType = computed(() => {
  return { BUG: 'danger', FEATURE: 'success', COMPLAINT: 'warning', OTHER: 'info' }[
    ticket.value.type
  ] || 'info'
})

async function loadData() {
  loading.value = true
  try {
    const res = await getTicket(ticketId)
    ticket.value = res.data.ticket || {}
    flows.value = res.data.flows || []
    comments.value = res.data.comments || []
  } finally {
    loading.value = false
  }
}

function openAction(act) {
  currentAction.value = act
  actionRemark.value = ''
  transferUserId.value = null

  // 转派时需要下拉候选人。
  // 注意：每次打开都重新加载。原实现是 `if (!users.value.length)` 才请求，
  // 一旦首次请求失败（比如 403），候选列表就永远为空且不再重试，
  // 表现为"新处理人下拉框里什么都没有"，转派必然失败。
  if (act.code === 'TRANSFER') {
    loadTransferCandidates()
  }
  actionVisible.value = true
}

/**
 * 加载可转派的候选人。
 */
async function loadTransferCandidates() {
  try {
    const r = await listEnabledUsers()
    users.value = (r.data || [])
      .map((u) => ({ userId: u.userId, nickname: u.nickname, dept: u.dept }))
      // 不能转派给自己
      .filter((u) => u.userId !== userStore.userInfo.userId)
  } catch (e) {
    users.value = []
    ElMessage.warning('人员列表加载失败，无法转派，请稍后重试')
  }
}

async function confirmAction() {
  if (currentAction.value.code === 'TRANSFER' && !transferUserId.value) {
    ElMessage.warning('请选择新的处理人')
    return
  }

  submitting.value = true
  try {
    await handleTicketAction(ticketId, {
      action: currentAction.value.code,
      remark: actionRemark.value,
      assigneeId: transferUserId.value
    })
    ElMessage.success(`${currentAction.value.label}成功`)
    actionVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function submitComment() {
  if (!commentText.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  await addComment(ticketId, commentText.value.trim())
  ElMessage.success('评论成功')
  commentText.value = ''
  loadData()
}

function goBack() {
  router.push('/ticket/list')
}

function statusTagTypeOf(s) {
  return { 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: 'info', 5: 'danger' }[s] || 'info'
}

function timelineType(s) {
  return { 0: 'primary', 1: 'primary', 2: 'warning', 3: 'success', 4: 'info', 5: 'danger' }[s] || 'primary'
}

function timelineColor(s) {
  return { 0: '#909399', 1: '#409eff', 2: '#e6a23c', 3: '#67c23a', 4: '#c0c4cc', 5: '#f56c6c' }[s] || '#409eff'
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.page-header {
  background: #fff;
  padding: 16px 20px;
  border-radius: 6px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.mt-16 {
  margin-top: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title-text {
  font-weight: 600;
  font-size: 15px;
}

.content-text {
  white-space: pre-wrap;
  line-height: 1.7;
}

.action-bar {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.empty-comments {
  text-align: center;
  color: #909399;
  padding: 24px 0;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}

.comment-avatar {
  background-color: #409eff;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.comment-name {
  font-weight: 600;
  font-size: 14px;
}

.comment-time {
  font-size: 12px;
  color: #909399;
}

.comment-content {
  font-size: 14px;
  line-height: 1.6;
  color: #606266;
}

.comment-input {
  margin-top: 16px;
}

.comment-btn {
  margin-top: 10px;
  float: right;
}

.timeline-card {
  background: #f8f9fb;
  padding: 10px 12px;
  border-radius: 6px;
}

.timeline-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.timeline-operator {
  font-size: 12px;
  color: #909399;
}

.timeline-remark {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.text-danger {
  color: #f56c6c;
  font-weight: 600;
}

.ml-4 {
  margin-left: 4px;
}

.ml-6 {
  margin-left: 6px;
}

.action-bar :deep(.el-empty) {
  padding: 0;
}
</style>