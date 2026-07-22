<script lang="ts">
export default { name: 'SystemOnline' }
</script>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Logout } from '@element-plus/icons-vue'
import type { OnlineUser } from '@/types/api'
import { getOnlineListApi, kickOnlineApi } from '@/api/online'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<OnlineUser[]>([])
const searchForm = ref<{ username?: string; nickname?: string }>({})

async function fetchData() {
  loading.value = true
  try {
    const res = await getOnlineListApi(searchForm.value)
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchData()
}

function handleReset() {
  searchForm.value = {}
  fetchData()
}

async function handleKick(row: OnlineUser) {
  if (row.username === userStore.user?.username) {
    ElMessage.warning('不能强制下线当前登录账号')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要强制下线用户 "${row.nickname || row.username}" 吗？该用户的下一次请求将返回 401。`,
      '提示',
      { type: 'warning' },
    )
    await kickOnlineApi(row.username)
    ElMessage.success('已强制下线')
    fetchData()
  } catch {
    // cancelled or handled
  }
}

/**
 * 简化 User-Agent 显示：只展示浏览器/系统的关键片段
 */
function shortenUA(ua: string): string {
  if (!ua) return '—'
  const browser =
    /Edg\/[\d.]+/.exec(ua)?.[0] ||
    /Chrome\/[\d.]+/.exec(ua)?.[0] ||
    /Firefox\/[\d.]+/.exec(ua)?.[0] ||
    /Safari\/[\d.]+/.exec(ua)?.[0] ||
    'Unknown'
  const os =
    /Windows NT [\d.]+/.exec(ua)?.[0]?.replace('Windows NT ', 'Win ') ||
    /Mac OS X [\d_.]+/.exec(ua)?.[0]?.replace(/_/g, '.').replace('Mac OS X ', 'macOS ') ||
    /Android [\d.]+/.exec(ua)?.[0] ||
    /iPhone OS [\d_]+/.exec(ua)?.[0]?.replace(/_/g, '.') ||
    /Linux/.exec(ua)?.[0] ||
    ''
  return os ? `${browser} · ${os}` : browser
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="用户账号">
          <el-input v-model="searchForm.username" placeholder="请输入用户账号" clearable />
        </el-form-item>
        <el-form-item label="用户昵称">
          <el-input v-model="searchForm.nickname" placeholder="请输入用户昵称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'system:online:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <span class="online-count">
          当前在线：<strong>{{ tableData.length }}</strong> 人
        </span>
        <el-button @click="fetchData">
          <el-icon class="btn-icon"><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 400px)">
        <el-table-column prop="username" label="用户账号" min-width="140" />
        <el-table-column prop="nickname" label="用户昵称" min-width="140">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column prop="loginIp" label="登录地址" min-width="140">
          <template #default="{ row }">{{ row.loginIp || '—' }}</template>
        </el-table-column>
        <el-table-column prop="clientSource" label="客户来源" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.clientSource" size="small">{{ row.clientSource }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="客户标识" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip :content="row.userAgent || '—'" placement="top">
              <span>{{ shortenUA(row.userAgent) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="loginTime" label="登录时间" min-width="170" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="强制下线">
              <el-button
                v-permission="'system:online:kick'"
                type="danger"
                link
                :icon="Logout"
                :disabled="row.username === userStore.user?.username"
                @click="handleKick(row)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.search-card {
  margin-bottom: 0;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.online-count {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.online-count strong {
  color: var(--el-color-primary);
  margin: 0 4px;
  font-size: 16px;
}

.btn-icon {
  margin-right: 4px;
  vertical-align: -2px;
}
</style>
