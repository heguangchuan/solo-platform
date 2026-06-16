<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePasswordApi } from '@/api/auth'

const props = defineProps<{
  modelValue: boolean
  /** 强制模式：不可关闭、不可取消，仅修改成功才会关闭 */
  forced?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => (visible.value = v))
watch(visible, (v) => emit('update:modelValue', v))

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

/**
 * 强密码：长度 8-32，至少包含大写、小写、数字、特殊字符 4 类中的 3 类，不含空白。
 */
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
  if (value === form.oldPassword) return callback(new Error('新密码不能与原密码相同'))
  callback()
}

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, validator: strongPasswordValidator, trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_, value, cb) => (value === form.newPassword ? cb() : cb(new Error('两次输入的密码不一致'))),
      trigger: 'blur',
    },
  ],
}

function reset() {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  formRef.value?.clearValidate()
}

watch(visible, (v) => {
  if (v) reset()
})

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    await changePasswordApi({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功，请使用新密码登录')
    emit('success')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

function close() {
  if (props.forced) return
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="forced ? '首次登录请修改默认密码' : '修改密码'"
    width="460px"
    :close-on-click-modal="!forced"
    :close-on-press-escape="!forced"
    :show-close="!forced"
    align-center
  >
    <el-alert
      v-if="forced"
      type="warning"
      :closable="false"
      show-icon
      title="检测到您仍在使用默认密码，为账号安全请先修改密码后再继续操作。"
      style="margin-bottom: 16px"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @keyup.enter="submit">
      <el-form-item label="原始密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入原密码" autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新建密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password placeholder="8-32 位，含大小写/数字/特殊字符 ≥3 类" autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入新密码" autocomplete="new-password" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-if="!forced" @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>
