<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginApi, getAuthMenusApi, getAuthPermissionsApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { dynamicRouteConfig } from '@/router'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' },
  ],
}

async function handleLogin() {
  loading.value = true
  try {
    const loginRes = await loginApi(form)
    userStore.loginSuccess(loginRes.data)

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
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrapper">
    <!-- 页面整体居中卡片容器 -->
    <div class="book-container">
      <!-- 左屏：品牌展示 -->
      <div class="login-brand">
        <div class="deco-circle deco-circle-1"></div>
        <div class="deco-circle deco-circle-2"></div>
        <div class="particles">
          <span v-for="n in 15" :key="n" class="particle" :style="{
            left: (n * 6.7) + '%',
            animationDelay: (n * 0.4) + 's',
            animationDuration: (10 + n * 0.3) + 's',
          }"></span>
        </div>

        <div class="brand-content">
          <div class="brand-logo">
            <svg viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" style="stop-color:#fff" stop-opacity="0.9" />
                  <stop offset="100%" style="stop-color:#fff" stop-opacity="0.6" />
                </linearGradient>
                <linearGradient id="borderGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" style="stop-color:rgba(255,255,255,0.8)" />
                  <stop offset="100%" style="stop-color:rgba(255,255,255,0.2)" />
                </linearGradient>
              </defs>
              <circle cx="60" cy="60" r="52" stroke="url(#borderGrad)" stroke-width="2" fill="none" />
              <circle cx="60" cy="60" r="40" stroke="url(#borderGrad)" stroke-width="1" fill="none" opacity="0.3" />
              <text x="60" y="68" text-anchor="middle" fill="url(#logoGrad)" font-size="42" font-weight="700" font-family="'SF Pro Display', Arial, sans-serif">Solo</text>
            </svg>
          </div>
          <h1 class="brand-title">权限管理平台</h1>
          <p class="brand-desc">Spring Boot + Vue 3<br />完整 RBAC 权限管理方案</p>
          <div class="brand-stats">
            <div class="stat-item">
              <div class="stat-value">Spring Boot</div>
              <div class="stat-label">后端</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">Vue 3</div>
              <div class="stat-label">前端</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">RBAC</div>
              <div class="stat-label">权限</div>
            </div>
          </div>
        </div>

        <div class="bottom-line"></div>
      </div>

      <!-- 右屏：登录表单 -->
      <div class="login-form-area">
        <div class="form-inner">
          <div class="form-header">
            <div class="form-logo">
              <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect width="40" height="40" rx="10" fill="url(#formLogoGrad)" />
                <text x="20" y="27" text-anchor="middle" fill="white" font-size="18" font-weight="700">S</text>
                <defs>
                  <linearGradient id="formLogoGrad" x1="0" y1="0" x2="40" y2="40">
                    <stop offset="0%" style="stop-color:#667eea" />
                    <stop offset="100%" style="stop-color:#764ba2" />
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <h2 class="form-title">欢迎回来</h2>
            <p class="form-subtitle">登录您的管理员账户</p>
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
              <el-checkbox>记住我</el-checkbox>
            </div>

            <el-form-item>
              <el-button
                type="primary"
                :loading="loading"
                class="login-btn"
                @click="handleLogin"
              >
                <el-icon v-if="!loading" :size="18"><ArrowRight /></el-icon>
                登 录
              </el-button>
            </el-form-item>
          </el-form>

          <div class="login-footer">
            <el-alert
              title="演示账号：admin / admin123"
              type="info"
              :closable="false"
              show-icon
            />
          </div>

          <div class="copyright">
            © 2025 Solo Platform · All rights reserved.
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100vh;
  background: #e8eaf0;
  overflow: hidden;
}

/* ===== 居中卡片（书本效果） ===== */
.book-container {
  width: 960px;
  max-width: calc(100vw - 40px);
  height: 600px;
  max-height: calc(100vh - 40px);
  display: flex;
  border-radius: 20px;
  overflow: hidden;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.1);
}

/* ===== 左屏 ===== */
.login-brand {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 40%, #0f3460 100%);
  overflow: hidden;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.deco-circle-1 {
  width: 500px;
  height: 500px;
  top: -200px;
  right: -200px;
  animation: spin-slow 30s linear infinite;
}

.deco-circle-2 {
  width: 350px;
  height: 350px;
  bottom: -100px;
  left: -100px;
  animation: spin-slow 25s linear infinite reverse;
}

@keyframes spin-slow {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  bottom: -10px;
  width: 3px;
  height: 3px;
  background: rgba(102, 126, 234, 0.5);
  border-radius: 50%;
  animation: float-up linear infinite;
}

@keyframes float-up {
  0% {
    transform: translateY(0) scale(0);
    opacity: 0;
  }
  10% {
    opacity: 1;
    transform: translateY(-80%) scale(1);
  }
  90% {
    opacity: 0.5;
  }
  100% {
    transform: translateY(-110%) scale(0.5);
    opacity: 0;
  }
}

.bottom-line {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #667eea, #764ba2, #f093fb, #667eea);
  background-size: 300% 100%;
  animation: gradient-move 6s linear infinite;
}

@keyframes gradient-move {
  0% { background-position: 0% 50%; }
  100% { background-position: 300% 50%; }
}

.brand-content {
  position: relative;
  z-index: 2;
  text-align: center;
  color: #fff;
  padding: 32px;
}

.brand-logo svg {
  width: 110px;
  height: 110px;
  margin-bottom: 24px;
  filter: drop-shadow(0 4px 20px rgba(102, 126, 234, 0.3));
  animation: logo-float 3s ease-in-out infinite alternate;
}

@keyframes logo-float {
  0% { transform: translateY(0); }
  100% { transform: translateY(-8px); }
}

.brand-title {
  font-size: 26px;
  font-weight: 700;
  margin: 0 0 10px;
  letter-spacing: 3px;
}

.brand-desc {
  font-size: 13px;
  opacity: 0.5;
  margin: 0 0 36px;
  line-height: 1.8;
  white-space: pre-line;
}

.brand-stats {
  display: flex;
  gap: 28px;
  justify-content: center;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #667eea, #f093fb);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat-label {
  font-size: 11px;
  opacity: 0.4;
  margin-top: 4px;
}

/* ===== 右屏 ===== */
.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: #fff;
}

.form-inner {
  width: 100%;
  max-width: 320px;
}

.form-header {
  text-align: center;
  margin-bottom: 32px;
}

.form-logo {
  width: 40px;
  height: 40px;
  margin: 0 auto 16px;
}

.form-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 6px;
}

.form-subtitle {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

.form-options {
  margin-bottom: 8px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 8px;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  transition: all 0.3s;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.35);
}

.login-footer {
  margin-top: 20px;
}

:deep(.login-footer .el-alert__content) {
  font-size: 12px;
}

.copyright {
  margin-top: 28px;
  text-align: center;
  color: #d0d0d0;
  font-size: 11px;
  letter-spacing: 0.5px;
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .book-container {
    flex-direction: column;
    width: calc(100vw - 32px);
    height: auto;
    max-height: 90vh;
  }

  .login-brand {
    display: none;
  }

  .login-form-area {
    flex: 1;
    padding: 40px 24px;
  }
}
</style>
