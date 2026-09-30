<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { loginApi, getAuthMenusApi, getAuthPermissionsApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { dynamicRouteConfig } from '@/router'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const formRef = ref<FormInstance>()

const isDev = import.meta.env.DEV
const year = new Date().getFullYear()

const REMEMBER_KEY = 'login:remembered-username'
const remembered = localStorage.getItem(REMEMBER_KEY)

const form = reactive({
  username: remembered ?? '',
  password: '',
})
const remember = ref(remembered !== null)

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 3, max: 32, message: '长度在 3 到 32 个字符', trigger: 'blur' },
  ],
}

// 强制改密对话框
const forceChangeVisible = ref(false)

async function loadAuthAndEnter() {
  const [menuRes, permissionRes] = await Promise.all([
    getAuthMenusApi(),
    getAuthPermissionsApi(),
  ])
  const menus = menuRes.data
  if (menus?.length) {
    dynamicRouteConfig.registerAll(menus)
  }
  userStore.setPermissions(permissionRes.data)
  ElMessage.success('欢迎回来')
  router.push('/')
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const loginRes = await loginApi(form)
    userStore.loginSuccess(loginRes.data)

    if (remember.value) {
      localStorage.setItem(REMEMBER_KEY, form.username)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }

    // 命中强制改密：阻止进入主页面，弹出改密对话框
    if (loginRes.data.user?.pwdResetRequired === 1) {
      forceChangeVisible.value = true
      return
    }

    await loadAuthAndEnter()
  } catch {
    // 校验失败或登录失败：拦截器统一提示
  } finally {
    loading.value = false
  }
}

/**
 * 强制改密成功：清掉 token 让用户用新密码重新登录，
 * 这样能保证密码、Token、首屏权限的一致性。
 */
function onPasswordChanged() {
  userStore.logout()
  form.password = ''
  ElMessage.info('密码已更新，请使用新密码重新登录')
}
</script>

<template>
  <div class="login-wrapper">
    <div class="bg-grid"></div>
    <div class="bg-glow bg-glow-1"></div>
    <div class="bg-glow bg-glow-2"></div>

    <div class="login-card">
      <!-- 左屏：品牌展示 -->
      <div class="login-brand">
        <div class="brand-content">
          <div class="brand-logo">
            <svg viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="60" cy="60" r="52" stroke="rgba(45,212,191,0.35)" stroke-width="1.5" />
              <circle cx="60" cy="60" r="40" stroke="rgba(45,212,191,0.15)" stroke-width="1" />
              <!-- 摄像机 + 镜头 -->
              <rect x="32" y="47" width="42" height="28" rx="5" stroke="#2dd4bf" stroke-width="2.5" />
              <path d="M74 56 L88 49 V73 L74 66 Z" stroke="#2dd4bf" stroke-width="2.5" stroke-linejoin="round" />
              <circle cx="53" cy="61" r="7" stroke="#5eead4" stroke-width="2" />
              <circle cx="53" cy="61" r="2.5" fill="#2dd4bf" />
              <!-- 信号波 -->
              <path d="M45 38 Q53 33 61 38" stroke="rgba(45,212,191,0.6)" stroke-width="1.8" stroke-linecap="round" />
              <path d="M39 33 Q53 24 67 33" stroke="rgba(45,212,191,0.3)" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </div>
          <h1 class="brand-title">Solo 视频监控平台</h1>
          <p class="brand-desc">GB/T 28181 国标视频接入与运维管理</p>

          <ul class="brand-features">
            <li>
              <span class="feature-dot"></span>
              国标设备接入 · 注册鉴权 · 心跳保活
            </li>
            <li>
              <span class="feature-dot"></span>
              实时点播 · 主子码流 · 历史回放与下载
            </li>
            <li>
              <span class="feature-dot"></span>
              云台控制 · 预置位 · 巡航
            </li>
            <li>
              <span class="feature-dot"></span>
              国标级联 · 流媒体分发与负载均衡
            </li>
          </ul>
        </div>

        <div class="brand-bottom-line"></div>
      </div>

      <!-- 右屏：登录表单 -->
      <div class="login-form-area">
        <div class="form-inner">
          <div class="form-header">
            <h2 class="form-title">欢迎登录</h2>
            <p class="form-subtitle">Solo 视频监控平台管理端</p>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            size="large"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                placeholder="用户名"
                prefix-icon="User"
                :autofocus="true"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <div class="form-options">
              <el-checkbox v-model="remember">记住用户名</el-checkbox>
            </div>

            <el-form-item>
              <el-button
                type="primary"
                :loading="loading"
                class="login-btn"
                @click="handleLogin"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>

          <div v-if="isDev" class="login-footer">
            <el-alert
              title="演示账号：admin / admin123"
              type="info"
              :closable="false"
              show-icon
            />
          </div>

          <div class="copyright">
            © {{ year }} Solo Platform · All rights reserved.
          </div>
        </div>
      </div>
    </div>

    <ChangePasswordDialog
      v-model="forceChangeVisible"
      forced
      @success="onPasswordChanged"
    />
  </div>
</template>

<style scoped>
.login-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100vh;
  background: #06110f;
  overflow: hidden;
}

/* 深青底 + 细网格，暗示监控/调度场景 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(45, 212, 191, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(45, 212, 191, 0.05) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: radial-gradient(ellipse at center, #000 35%, transparent 78%);
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
}

.bg-glow-1 {
  width: 520px;
  height: 520px;
  top: -180px;
  left: -120px;
  background: rgba(15, 118, 110, 0.2);
}

.bg-glow-2 {
  width: 460px;
  height: 460px;
  bottom: -200px;
  right: -100px;
  background: rgba(20, 184, 166, 0.12);
}

/* ===== 居中卡片 ===== */
.login-card {
  position: relative;
  z-index: 1;
  width: 920px;
  max-width: calc(100vw - 40px);
  min-height: 540px;
  display: flex;
  border-radius: 14px;
  overflow: hidden;
  box-shadow:
    0 24px 70px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(45, 212, 191, 0.12);
}

/* ===== 左屏 ===== */
.login-brand {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  padding: 48px 44px;
  background: linear-gradient(155deg, #0a1f1c 0%, #0f3d38 65%, #0f766e 100%);
}

.brand-content {
  position: relative;
  z-index: 2;
  color: #fff;
}

.brand-logo svg {
  width: 84px;
  height: 84px;
  margin-bottom: 24px;
}

.brand-title {
  font-size: 25px;
  font-weight: 700;
  margin: 0 0 10px;
  letter-spacing: 2px;
  color: #f1f5f9;
}

.brand-desc {
  font-size: 13px;
  color: rgba(153, 246, 228, 0.75);
  margin: 0 0 34px;
  letter-spacing: 0.5px;
}

.brand-features {
  list-style: none;
  margin: 0;
  padding: 0;
}

.brand-features li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: rgba(226, 232, 240, 0.82);
  line-height: 2.4;
}

.feature-dot {
  flex-shrink: 0;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #2dd4bf;
  box-shadow: 0 0 8px rgba(45, 212, 191, 0.8);
}

.brand-bottom-line {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: linear-gradient(90deg, rgba(20, 184, 166, 0.9), rgba(20, 184, 166, 0));
}

/* ===== 右屏 ===== */
.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 44px;
  background: #fff;
}

.form-inner {
  width: 100%;
  max-width: 320px;
}

.form-header {
  margin-bottom: 30px;
}

.form-title {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 6px;
}

.form-subtitle {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
}

.form-options {
  margin-bottom: 12px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
  border-radius: 8px;
}

.login-footer {
  margin-top: 18px;
}

:deep(.login-footer .el-alert__content) {
  font-size: 12px;
}

.copyright {
  margin-top: 28px;
  text-align: center;
  color: #cbd5e1;
  font-size: 11px;
  letter-spacing: 0.5px;
}

/* ===== 响应式：窄屏保留紧凑品牌条，不再整块隐藏 ===== */
@media (max-width: 860px) {
  .login-card {
    flex-direction: column;
    width: calc(100vw - 32px);
    min-height: 0;
  }

  .login-brand {
    padding: 24px 28px;
  }

  .brand-logo svg {
    width: 44px;
    height: 44px;
    margin-bottom: 10px;
  }

  .brand-title {
    font-size: 18px;
    margin-bottom: 4px;
  }

  .brand-desc {
    margin-bottom: 0;
  }

  .brand-features {
    display: none;
  }

  .login-form-area {
    padding: 32px 28px 40px;
  }
}
</style>
