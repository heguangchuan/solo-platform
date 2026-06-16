<script lang="ts">
export default { name: 'Profile' }
</script>
<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  getProfileApi,
  updateProfileApi,
  getMyRolesApi,
  changePasswordApi,
} from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { SysRole } from '@/types/api'

const userStore = useUserStore()
const activeTab = ref<'basic' | 'password' | 'roles'>('basic')

/* ============ 基本资料 ============ */
const profileLoading = ref(false)
const profileSubmitting = ref(false)
const profileFormRef = ref<FormInstance>()
const profile = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  status: 1,
  createTime: '',
})

const profileRules: FormRules = {
  nickname: [{ required: true, message: '请输入用户昵称', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur',
    },
  ],
}

async function loadProfile() {
  profileLoading.value = true
  try {
    const res = await getProfileApi()
    const u = res.data
    profile.username = u.username
    profile.nickname = u.nickname || ''
    profile.email = u.email || ''
    profile.phone = u.phone || ''
    profile.avatar = u.avatar || ''
    profile.status = u.status
    // createTime 接口不返回，直接留空；展示「未知」
    profile.createTime = (u as any).createTime || ''
    // 同步到 store，刷新右上角昵称
    userStore.setUser({ ...userStore.user!, ...u })
  } finally {
    profileLoading.value = false
  }
}

async function handleSaveProfile() {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate()
  profileSubmitting.value = true
  try {
    await updateProfileApi({
      nickname: profile.nickname,
      email: profile.email,
      phone: profile.phone,
      avatar: profile.avatar,
    })
    ElMessage.success('个人资料已更新')
    await loadProfile()
  } finally {
    profileSubmitting.value = false
  }
}

/* ============ 修改密码 ============ */
const pwdSubmitting = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function strongPasswordValidator(_: any, value: string, callback: (e?: Error) => void) {
  if (!value) return callback(new Error('请输入新密码'))
  if (value.length < 8 || value.length > 32) return callback(new Error('密码长度必须在 8-32 位之间'))
  if (/\s/.test(value)) return callback(new Error('密码不能包含空格'))
  let kinds = 0
  if (/[A-Z]/.test(value)) kinds++
  if (/[a-z]/.test(value)) kinds++
  if (/\d/.test(value)) kinds++
  if (/[^A-Za-z0-9]/.test(value)) kinds++
  if (kinds < 3) return callback(new Error('需包含大写、小写、数字、特殊字符中的至少 3 类'))
  if (value === pwdForm.oldPassword) return callback(new Error('新密码不能与原密码相同'))
  callback()
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, validator: strongPasswordValidator, trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_, value, cb) => (value === pwdForm.newPassword ? cb() : cb(new Error('两次输入的密码不一致'))),
      trigger: 'blur',
    },
  ],
}

async function handleChangePassword() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate()
  pwdSubmitting.value = true
  try {
    await changePasswordApi({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    userStore.logout()
    setTimeout(() => {
      window.location.href = '/login'
    }, 600)
  } finally {
    pwdSubmitting.value = false
  }
}

/* ============ 我的角色 ============ */
const rolesLoading = ref(false)
const myRoles = ref<SysRole[]>([])

async function loadMyRoles() {
  rolesLoading.value = true
  try {
    const res = await getMyRolesApi()
    myRoles.value = res.data
  } finally {
    rolesLoading.value = false
  }
}

const avatarFallback = computed(() => {
  const name = profile.nickname || profile.username
  return name ? name.charAt(0).toUpperCase() : 'U'
})

onMounted(() => {
  loadProfile()
  loadMyRoles()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="profile-card">
      <div class="profile-layout">
        <!-- 左侧 -->
        <div class="profile-side">
          <div class="avatar-block">
            <el-avatar v-if="profile.avatar" :size="96" :src="profile.avatar" />
            <el-avatar v-else :size="96" class="avatar-fallback">{{ avatarFallback }}</el-avatar>
          </div>
          <div class="profile-summary">
            <div class="profile-name">{{ profile.nickname || profile.username || '未命名用户' }}</div>
            <div class="profile-username">@{{ profile.username }}</div>
            <el-tag v-if="profile.status === 1" type="success" size="small">账号已启用</el-tag>
            <el-tag v-else type="danger" size="small">账号已禁用</el-tag>
          </div>

          <el-divider />

          <ul class="profile-meta">
            <li>
              <span class="meta-label">用户账号</span>
              <span class="meta-value">{{ profile.username || '—' }}</span>
            </li>
            <li>
              <span class="meta-label">邮箱地址</span>
              <span class="meta-value">{{ profile.email || '—' }}</span>
            </li>
            <li>
              <span class="meta-label">手机号码</span>
              <span class="meta-value">{{ profile.phone || '—' }}</span>
            </li>
            <li>
              <span class="meta-label">所属角色</span>
              <span class="meta-value">
                <template v-if="myRoles.length">
                  <el-tag
                    v-for="r in myRoles"
                    :key="r.id"
                    type="primary"
                    size="small"
                    class="role-tag"
                  >
                    {{ r.roleName }}
                  </el-tag>
                </template>
                <span v-else>—</span>
              </span>
            </li>
          </ul>
        </div>

        <!-- 右侧 -->
        <div class="profile-main">
          <el-tabs v-model="activeTab">
            <!-- 基本资料 -->
            <el-tab-pane label="基本资料" name="basic">
              <el-form
                ref="profileFormRef"
                :model="profile"
                :rules="profileRules"
                label-width="100px"
                v-loading="profileLoading"
                class="profile-form"
              >
                <el-form-item label="用户账号">
                  <el-input :model-value="profile.username" disabled />
                </el-form-item>
                <el-form-item label="用户昵称" prop="nickname">
                  <el-input v-model="profile.nickname" placeholder="请输入用户昵称" />
                </el-form-item>
                <el-form-item label="邮箱地址" prop="email">
                  <el-input v-model="profile.email" placeholder="请输入邮箱地址" />
                </el-form-item>
                <el-form-item label="手机号码" prop="phone">
                  <el-input v-model="profile.phone" placeholder="请输入手机号码" />
                </el-form-item>
                <el-form-item label="头像地址">
                  <el-input v-model="profile.avatar" placeholder="请输入头像URL（可选）" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="profileSubmitting" @click="handleSaveProfile">
                    保存修改
                  </el-button>
                  <el-button @click="loadProfile">重置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 修改密码 -->
            <el-tab-pane label="修改密码" name="password">
              <el-alert
                type="warning"
                :closable="false"
                show-icon
                title="修改密码后将自动退出，请使用新密码重新登录。"
                style="margin-bottom: 16px"
              />
              <el-form
                ref="pwdFormRef"
                :model="pwdForm"
                :rules="pwdRules"
                label-width="100px"
                class="profile-form"
                @keyup.enter="handleChangePassword"
              >
                <el-form-item label="原始密码" prop="oldPassword">
                  <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" autocomplete="current-password" />
                </el-form-item>
                <el-form-item label="新建密码" prop="newPassword">
                  <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="8-32 位，含大小写/数字/特殊字符 ≥3 类" autocomplete="new-password" />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" autocomplete="new-password" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="pwdSubmitting" @click="handleChangePassword">
                    确认修改
                  </el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 我的角色 -->
            <el-tab-pane label="我的角色" name="roles">
              <el-table v-loading="rolesLoading" :data="myRoles" border stripe>
                <el-table-column prop="roleCode" label="角色编码" width="160" />
                <el-table-column prop="roleName" label="角色名称" width="160" />
                <el-table-column prop="description" label="角色描述" min-width="200" />
                <el-table-column label="角色状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                      {{ row.status === 1 ? '启用' : '禁用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="授予时间" min-width="180" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.profile-card {
  width: 100%;
}

.profile-layout {
  display: flex;
  gap: 24px;
  align-items: stretch;
}

.profile-side {
  width: 300px;
  flex-shrink: 0;
  padding: 8px 16px 8px 8px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.avatar-block {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}

.avatar-fallback {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 32px;
  font-weight: 600;
}

.profile-summary {
  text-align: center;
  margin-top: 12px;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.profile-username {
  margin: 4px 0 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.profile-meta {
  list-style: none;
  margin: 0;
  padding: 0;
}

.profile-meta li {
  display: flex;
  align-items: flex-start;
  font-size: 13px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.profile-meta li:last-child {
  border-bottom: none;
}

.meta-label {
  flex-shrink: 0;
  width: 80px;
  color: var(--el-text-color-secondary);
}

.meta-value {
  flex: 1;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.role-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}

.profile-main {
  flex: 1;
  min-width: 0;
}

.profile-form {
  max-width: 640px;
  padding-top: 8px;
}
</style>
