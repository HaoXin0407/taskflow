<template>
  <div>
    <el-card class="search-card" shadow="never">
      <el-form :model="query" inline>
        <el-form-item label="状态">
          <!--
            必须加 @change 触发查询。
            el-radio-group 只修改 v-model 绑定的值，不会自动重新请求数据，
            少了这个事件，用户点了按钮却什么都没发生（表现为"点击无响应"）。
          -->
          <el-radio-group v-model="query.isRead" @change="handleSearch">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button :value="0">未读</el-radio-button>
            <el-radio-button :value="1">已读</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button type="success" @click="handleReadAll">
            <el-icon><Select /></el-icon>&nbsp;全部已读
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-badge v-if="row.isRead === 0" is-dot type="danger" />
            <span v-else class="read-flag">已读</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="通知标题" min-width="220">
          <template #default="{ row }">
            <span :class="{ 'unread-title': row.isRead === 0 }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="通知内容" min-width="320" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="TYPE_MAP[row.type]?.type || 'info'">
              {{ TYPE_MAP[row.type]?.name || row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="通知时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.isRead === 0" link type="primary" size="small" @click="handleRead(row)">
              标记已读
            </el-button>
            <span v-else class="text-muted">-</span>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pageNotice, markRead, markAllRead } from '@/api'

const TYPE_MAP = {
  SYSTEM: { name: '系统', type: 'info' },
  TICKET: { name: '工单', type: 'primary' },
  APPROVAL: { name: '审批', type: 'success' }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  isRead: undefined
})

async function loadData() {
  loading.value = true
  try {
    const res = await pageNotice(query)
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

async function handleRead(row) {
  await markRead(row.id)
  ElMessage.success('已标记为已读')
  loadData()
}

async function handleReadAll() {
  await markAllRead()
  ElMessage.success('全部通知已标记为已读')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.unread-title {
  font-weight: 600;
  color: #303133;
}

.read-flag,
.text-muted {
  color: #c0c4cc;
  font-size: 12px;
}
</style>