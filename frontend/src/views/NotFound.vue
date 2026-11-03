<template>
  <el-result icon="info" title="404" sub-title="抱歉，您访问的页面不存在">
    <template #extra>
      <el-button type="primary" @click="$router.push('/dashboard')">返回首页</el-button>
    </template>
  </el-result>
</template>

<script setup></script>