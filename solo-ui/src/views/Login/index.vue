<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginApi, getAuthMenusApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { dynamicRouteConfig } from '@/router'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const activeTab = ref('password')

const form = reactive({
  username: '',
  password: '',
  remember: false,
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
    // 1. 登录
    const loginRes = await loginApi(form)
    userStore.loginSuccess(loginRes.data)

    // 2. 拉取用户菜单树
    const menuRes = await getAuthMenusApi()
    const menus = menuRes.data
    if (menus?.length) {
      dynamicRouteConfig.registerAll(menus)
    }

    ElMessage.success('欢迎回来，' + (loginRes.data.user.nickname || loginRes.data.user.username))
    router.push('/')
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

// 粒子背景动画 — 纯 CSS 实现
function startParticles() {
  const container = document.querySelector('.particles-bg')
  if (!container) return
  for (let i = 0; i < 50; i++) {
    const particle = document.createElement('div')
    particle.className = 'particle'
    const size = Math.random() * 4 + 2
    particle.style.width = size + 'px'
    particle.style.height = size + 'px'
    particle.style.left = Math.random() * 100 + '%'
    particle.style.animationDuration = (Math.random() * 15 + 10) + 's'
    particle.style.animationDelay = (Math.random() * 10).toFixed(2) + 's'
    particle.style.opacity = (Math.random() * 0.5 + 0.1).toFixed(2)
    container.appendChild(particle)
  }
}

function stopParticles() {
  const container = document.querySelector('.particles-bg')
  container?.querySelectorAll('.particle').forEach((p) => p.remove())
}

onMounted(() => {
  startParticles()
})

onUnmounted(() => {
  stopParticles()
})
</script>

<template>
  <div class="login-wrapper">
    <!-- 动态粒子背景 -->
    <div class="particles-bg"></div>
    <div class="glow glow-1"></div>
    <div class="glow glow-2"></div>
    <div class="glow glow-3"></div>

    <div class="login-body">
      <!-- 左侧品牌展示区 -->
      <div class="login-brand">
        <div class="brand-content">
          <div class="brand-logo">
            <svg viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" style="stop-color:#667eea" />
                  <stop offset="100%" style="stop-color:#764ba2" />
                </linearGradient>
              </defs>
              <circle cx="60" cy="60" r="55" stroke="url(#logoGrad)" stroke-width="4" fill="none" />
              <text x="60" y="72" text-anchor="middle" fill="white" font-size="48" font-weight="bold" font-family="Arial">S</text>
            </svg>
          </div>
          <h1 class="brand-title">Solo 后台管理系统</h1>
          <p class="brand-desc">基于 Spring Boot + Vue 3 构建的现代化权限管理平台</p>
          <div class="brand-features">
            <div class="feature-item">
              <el-icon :size="20"><Shield /></el-icon>
              <span>RBAC 权限控制</span>
            </div>
            <div class="feature-item">
              <el-icon :size="20"><Connection /></el-icon>
              <span>JWT 安全认证</span>
            </div>
            <div class="feature-item">
              <el-icon :size="20"><Monitor /></el-icon>
              <span>实时数据统计</span>
            </div>
            <div class="feature-item">
              <el-icon :size="20"><Cpu /></el-icon>
              <span>前后端分离架构</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧登录表单区 -->
      <div class="login-form-area">
        <el-card class="login-card" shadow="never">
          <div class="login-header">
            <h2 class="login-title">欢迎回来</h2>
            <p class="login-subtitle">请登录您的管理员账户</p>
          </div>

          <el-tabs v-model="activeTab" class="login-tabs">
            <el-tab-pane label="账号密码登录" name="password">
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
                    placeholder="请输入用户名"
                    prefix-icon="User"
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
                  <el-checkbox v-model="form.remember">记住我</el-checkbox>
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
            </el-tab-pane>
          </el-tabs>

          <div class="login-footer">
            <el-alert
              title="演示账号：admin / admin123"
              type="info"
              :closable="false"
              show-icon
              class="demo-account"
            />
          </div>
        </el-card>

        <div class="copyright">
          © 2025 Solo Platform · Powered by Vue 3 + Spring Boot
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-wrapper {
  position: relative;
  display: flex;
  align-items: stretch;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.particles-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  animation: float linear infinite;
}

@keyframes float {
  0% { transform: translateY(0) rotate(0deg); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translateY(-100vh) rotate(720deg); opacity: 0; }
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  z-index: 0;
  pointer-events: none;
}

.glow-1 {
  width: 500px;
  height: 500px;
  background: rgba(102, 126, 234, 0.3);
  top: -150px;
  right: -100px;
  animation: pulse-glow 6s ease-in-out infinite alternate;
}

.glow-2 {
  width: 400px;
  height: 400px;
  background: rgba(118, 75, 162, 0.25);
  bottom: -100px;
  left: -50px;
  animation: pulse-glow 8s ease-in-out infinite alternate-reverse;
}

.glow-3 {
  width: 300px;
  height: 300px;
  background: rgba(72, 187, 120, 0.2);
  top: 50%;
  left: 40%;
  animation: pulse-glow 7s ease-in-out infinite alternate;
}

@keyframes pulse-glow {
  0% { transform: scale(1); opacity: 0.5; }
  100% { transform: scale(1.3); opacity: 0.8; }
}

.login-body {
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  height: 100%;
}

.login-brand {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.9) 0%, rgba(118, 75, 162, 0.9) 100%);
  backdrop-filter: blur(10px);
  position: relative;
  overflow: hidden;
}

.login-brand::before {
  content: '';
  position: absolute;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
  top: -200px;
  left: -200px;
}

.login-brand::after {
  content: '';
  position: absolute;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.03);
  bottom: -100px;
  right: -100px;
}

.brand-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: #fff;
}

.brand-logo svg {
  width: 100px;
  height: 100px;
  margin-bottom: 24px;
  filter: drop-shadow(0 4px 20px rgba(0, 0, 0, 0.2));
}

.brand-title {
  font-size: 36px;
  font-weight: 800;
  margin: 0 0 12px;
  letter-spacing: 2px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
}

.brand-desc {
  font-size: 16px;
  opacity: 0.85;
  margin: 0 0 40px;
  line-height: 1.6;
}

.brand-features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  max-width: 400px;
  margin: 0 auto;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  backdrop-filter: blur(4px);
  font-size: 14px;
  transition: all 0.3s;
}

.feature-item:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
}

.login-form-area {
  width: 520px;
  min-width: 480px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: #f7f8fc;
}

.login-card {
  width: 100%;
  max-width: 400px;
  border: none;
  border-radius: 20px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
}

:deep(.el-card__body) {
  padding: 40px 32px 24px;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 8px;
}

.login-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.login-tabs {
  margin-top: 8px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  letter-spacing: 8px;
  border-radius: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  transition: all 0.3s;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.45);
}

.login-footer {
  margin-top: 20px;
}

.demo-account {
  margin: 0;
}

:deep(.demo-account .el-alert__content) {
  font-size: 13px;
}

.copyright {
  margin-top: 32px;
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  letter-spacing: 1px;
}

@media (max-width: 1024px) {
  .login-brand {
    display: none;
  }

  .login-form-area {
    width: 100%;
    min-width: unset;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  .login-card {
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(10px);
  }
}
</style>
