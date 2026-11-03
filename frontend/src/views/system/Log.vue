<template>
  <div>
    <el-card class="search-card" shadow="never">
      <el-form :model="query" inline>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="操作人 / 模块 / 接口"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这些日志由后端 AOP 切面自动记录，无需任何业务代码干预"
        style="margin-bottom: 14px"
      />

      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="title" label="模块" width="120" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="businessType(row.businessType)" size="small">
              {{ BUSINESS_MAP[row.businessType] || row.businessType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operName" label="操作人" width="100" />
        <el-table-column prop="operUrl" label="请求接口" min-width="200" show-overflow-tooltip />
        <el-table-column prop="operIp" label="IP 地址" width="130" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costTime" label="耗时(ms)" width="90" align="center" />
        <el-table-column prop="operTime" label="操作时间" width="165" />
        <el-table-column label="详情" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">查看</el-button>
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

    <el-drawer v-model="detailVisible" title="操作日志详情" size="620px">
      <el-descriptions :column="1" border v-if="current">
        <el-descriptions-item label="模块">{{ current.title }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">
          {{ BUSINESS_MAP[current.businessType] || current.businessType }}
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ current.operName }}</el-descriptions-item>
        <el-descriptions-item label="请求方式">
          <el-tag size="small">{{ current.requestMethod }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="请求地址">{{ current.operUrl }}</el-descriptions-item>
        <el-descriptions-item label="方法名">{{ current.method }}</el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ current.operIp }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ current.costTime }} ms</el-descriptions-item>
        <el-descriptions-item label="时间">{{ current.operTime }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <pre class="json-block">{{ current.operParam }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="返回结果">
          <pre class="json-block">{{ current.jsonResult }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="current.errorMsg" label="错误信息">
          <span class="error-text">{{ current.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { pageOperLog } from '@/api'

const BUSINESS_MAP = {
  INSERT: '新增',
  UPDATE: '修改',
  DELETE: '删除',
  QUERY: '查询',
  AUDIT: '审批',
  OTHER: '其它'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const detailVisible = ref(false)
const current = ref(null)

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: null })

async function loadData() {
  loading.value = true
  try {
    const res = await pageOperLog(query)
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

function showDetail(row) {
  current.value = row
  detailVisible.value = true
}

function businessType(t) {
  return { INSERT: 'success', UPDATE: 'warning', DELETE: 'danger', AUDIT: 'primary' }[t] || 'info'
}

onMounted(loadData)
</script>

<style scoped>
.el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.json-block {
  margin: 0;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 220px;
  overflow-y: auto;
}

.error-text {
  color: #f56c6c;
}
</style>