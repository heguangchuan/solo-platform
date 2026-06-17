<script setup lang="ts">
import { ref } from 'vue'

const dictValue = ref('')
const dictValueNum = ref(1)
const dictMultiValue = ref<string[]>([])
</script>

<template>
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

  <el-card shadow="never" class="test-card" style="margin-top: 16px">
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
</template>

<style scoped>
.result-text {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
