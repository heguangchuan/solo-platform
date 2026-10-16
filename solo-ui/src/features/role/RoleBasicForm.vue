<template>
  <section>
    <h4 class="mb-3.5 text-[13px] font-medium">{{ creating ? '角色信息' : '基本信息' }}</h4>

    <div class="space-y-3">
      <div class="flex items-center gap-3">
        <Label class="w-14 shrink-0 text-xs text-muted-foreground">
          编码<span class="ml-0.5 text-destructive">*</span>
        </Label>
        <div class="flex-1">
          <Input
            v-model="form.roleCode"
            class="h-8 text-xs"
            :disabled="!creating"
            placeholder="如：ADMIN、USER"
            @blur="errors.roleCode = !form.roleCode.trim() ? '请输入角色编码' : ''"
          />
          <p v-if="errors.roleCode" class="mt-1 text-[11px] text-destructive">
            {{ errors.roleCode }}
          </p>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <Label class="w-14 shrink-0 text-xs text-muted-foreground">
          名称<span class="ml-0.5 text-destructive">*</span>
        </Label>
        <div class="flex-1">
          <Input
            v-model="form.roleName"
            class="h-8 text-xs"
            placeholder="请输入角色名称"
            @blur="errors.roleName = !form.roleName.trim() ? '请输入角色名称' : ''"
          />
          <p v-if="errors.roleName" class="mt-1 text-[11px] text-destructive">
            {{ errors.roleName }}
          </p>
        </div>
      </div>

      <div class="flex items-start gap-3">
        <Label class="mt-1.5 w-14 shrink-0 text-xs text-muted-foreground">描述</Label>
        <textarea
          v-model="form.description"
          rows="3"
          class="min-h-16 w-full flex-1 rounded-md border border-input bg-transparent px-2 py-1.5 text-xs outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/15"
          placeholder="请输入角色描述"
        />
      </div>

      <div v-if="!creating" class="flex items-center gap-3">
        <Label class="w-14 shrink-0 text-xs text-muted-foreground">状态</Label>
        <Switch
          :model-value="form.status === 1"
          @update:model-value="(v: boolean) => (form.status = v ? 1 : 0)"
        />
      </div>
    </div>

    <div class="mt-4 flex justify-end gap-2">
      <Button size="sm" variant="outline" @click="$emit('cancel')">取消</Button>
      <Button size="sm" :loading="saving" @click="$emit('save')">
        {{ creating ? '创建角色' : '保存' }}
      </Button>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { RoleRequest } from '@/types/api'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Label from '@/components/ui/label/Label.vue'

defineProps<{
  form: RoleRequest
  errors: { roleCode: string; roleName: string }
  saving: boolean
  creating: boolean
}>()

defineEmits<{ save: []; cancel: [] }>()
</script>
