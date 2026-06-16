<script lang="ts">
export default { name: 'TestManage' }
</script>
<script setup lang="ts">
import { ref } from 'vue'
import type { SysFile } from '@/types/api'

const activeTab = ref('upload')
const uploadUrl = ref('')
const uploadFile = ref<SysFile | null>(null)

function resetUploadResult() {
  uploadUrl.value = ''
  uploadFile.value = null
}

const dictValue = ref('')
const dictValueNum = ref(1)
const dictMultiValue = ref<string[]>([])
</script>

<template>
  <div class="test-manage">
    <el-card shadow="hover" class="overview-card">
      <div class="page-header">
        <div>
          <h2>测试管理</h2>
          <p>用于集中验证前端自定义组件，当前包含上传组件测试，后续补充字典组件测试。</p>
        </div>
        <el-tag type="primary">组件功能测试</el-tag>
      </div>
    </el-card>

    <el-card shadow="hover" class="content-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="上传组件测试" name="upload">
          <el-card shadow="never" class="test-card">
            <template #header>通用文件上传</template>
            <FileUpload
              v-model="uploadUrl"
              v-model:file="uploadFile"
              button-text="选择文件上传"
              tip="使用自定义 FileUpload 组件测试上传、回显和移除"
              @remove="resetUploadResult"
            />

            <el-descriptions v-if="uploadFile" :column="1" border class="result-box">
              <el-descriptions-item label="文件名">{{ uploadFile.originalName }}</el-descriptions-item>
              <el-descriptions-item label="文件类型">{{ uploadFile.ext || '—' }}</el-descriptions-item>
              <el-descriptions-item label="文件大小">{{ uploadFile.size }} B</el-descriptions-item>
              <el-descriptions-item label="访问地址">{{ uploadFile.url }}</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="请上传一个文件" />
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="字典组件测试" name="dict">
          <el-card shadow="never" class="test-card">
            <template #header>字典下拉组件 DictSelect</template>

            <el-form label-width="140px">
              <el-form-item label="单选（字符串值）">
                <DictSelect
                  v-model="dictValue"
                  dict-type="sys_file_types"
                  placeholder="请选择文件类型"
                  clearable
                  style="width: 240px"
                />
                <span class="result-text">选中值：{{ dictValue || '—' }}</span>
              </el-form-item>

              <el-form-item label="单选（数值值）">
                <DictSelect
                  v-model="dictValueNum"
                  dict-type="sys_file_types"
                  value-type="number"
                  placeholder="请选择文件类型"
                  clearable
                  style="width: 240px"
                />
                <span class="result-text">选中值：{{ dictValueNum }}（{{ typeof dictValueNum }}）</span>
              </el-form-item>

              <el-form-item label="多选">
                <DictSelect
                  v-model="dictMultiValue"
                  dict-type="sys_file_types"
                  multiple
                  placeholder="请选择文件类型"
                  style="width: 320px"
                />
                <span class="result-text">选中值：{{ dictMultiValue.join(', ') || '—' }}</span>
              </el-form-item>

              <el-form-item label="禁用状态">
                <DictSelect
                  v-model="dictValue"
                  dict-type="sys_file_types"
                  disabled
                  style="width: 240px"
                />
              </el-form-item>
            </el-form>
          </el-card>

          <el-card shadow="never" class="test-card">
            <template #header>字典标签组件 DictTag</template>

            <el-form label-width="140px">
              <el-form-item label="单个值">
                <DictTag dict-type="sys_file_types" value="1" />
                <span class="result-text">值：1 → 标签：mp4</span>
              </el-form-item>

              <el-form-item label="多个值">
                <DictTag dict-type="sys_file_types" :value="['1', '2']" />
                <span class="result-text">值：['1', '2']</span>
              </el-form-item>

              <el-form-item label="数值型值">
                <DictTag dict-type="sys_file_types" :value="2" />
                <span class="result-text">值：2 → 标签：png</span>
              </el-form-item>

              <el-form-item label="不同尺寸">
                <DictTag dict-type="sys_file_types" value="1" size="small" />
                <DictTag dict-type="sys_file_types" value="1" size="default" style="margin: 0 8px" />
                <DictTag dict-type="sys_file_types" value="1" size="large" />
              </el-form-item>

              <el-form-item label="plain 效果">
                <DictTag dict-type="sys_file_types" value="1" plain />
              </el-form-item>

              <el-form-item label="未知值 fallback">
                <DictTag dict-type="sys_file_types" value="99" fallback="未知类型" />
                <DictTag dict-type="sys_file_types" value="99" :show-unknown="false" style="margin-left: 8px" />
                <span class="result-text">show-unknown=false 时不显示未知值</span>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.test-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-card {
  margin-bottom: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.page-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.content-card {
  min-height: 360px;
}

.test-card {
  min-height: 260px;
}

.result-box {
  margin-top: 16px;
}

.result-text {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
