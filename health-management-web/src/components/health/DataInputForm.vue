<script setup lang="ts">
import { ref, watch } from 'vue';
import { Plus, Check } from 'lucide-vue-next';
import { ElMessage, ElSelect, ElOption } from 'element-plus';
import { addHealthData, healthDataTypes, getUnitByType, updateHealthData, type HealthDataResponse } from '../../api/health';

const props = defineProps<{
  editingData?: HealthDataResponse | null;
}>();

const emit = defineEmits<{
  success: [];
  cancel: [];
}>();

const recordType = ref('steps');
const value = ref('');
const unit = ref('步');
const recordDate = ref(new Date().toISOString().split('T')[0]);
const notes = ref('');
const isSubmitting = ref(false);
const isSuccess = ref(false);

// 当类型改变时更新单位
watch(recordType, (newType) => {
  unit.value = getUnitByType(newType);
}, { immediate: true });

watch(() => props.editingData, (data) => {
  if (!data) {
    recordType.value = 'steps';
    value.value = '';
    unit.value = '步';
    recordDate.value = new Date().toISOString().split('T')[0];
    notes.value = '';
    return;
  }
  recordType.value = data.type;
  value.value = String(data.value);
  unit.value = data.unit;
  recordDate.value = data.recordDate.split(' ')[0];
  notes.value = data.notes || '';
}, { immediate: true });

const handleSubmit = async () => {
  if (!value.value || isNaN(Number(value.value))) {
    ElMessage.warning('请输入有效的数值');
    return;
  }

  try {
    isSubmitting.value = true;
    const payload = {
      type: recordType.value,
      value: Number(value.value),
      unit: unit.value,
      recordDate: recordDate.value,
      notes: notes.value || undefined
    };

    if (props.editingData?.id) {
      await updateHealthData(props.editingData.id, payload);
    } else {
      await addHealthData(payload);
    }
    
    isSuccess.value = true;
    ElMessage.success(props.editingData ? '健康数据更新成功' : '健康数据记录成功');
    
    // 重置表单
    value.value = '';
    notes.value = '';
    
    // 通知父组件
    emit('success');
    
    setTimeout(() => {
      isSuccess.value = false;
    }, 2000);
  } catch (error) {
    console.error('提交健康数据失败:', error);
  } finally {
    isSubmitting.value = false;
  }
};

const handleCancel = () => {
  emit('cancel');
};
</script>

<template>
  <div class="data-input-form space-y-5">
    <div class="flex items-center justify-between">
      <h3 class="text-lg font-semibold text-gray-900 mb-4">{{ props.editingData ? '编辑健康数据' : '记录健康数据' }}</h3>
      <button
        v-if="props.editingData"
        @click="handleCancel"
        class="text-sm text-gray-500 hover:text-gray-900 transition-colors"
      >
        取消编辑
      </button>
    </div>
    
    <!-- 数据类型选择 -->
    <div class="space-y-2">
      <label class="block text-sm font-medium text-gray-500">数据类型</label>
      <el-select
        v-model="recordType"
        class="bili-el-select w-full"
        popper-class="bili-select-dropdown"
      >
        <el-option 
          v-for="t in healthDataTypes" 
          :key="t.value" 
          :label="t.label" 
          :value="t.value"
        />
      </el-select>
    </div>

    <!-- 数值输入 -->
    <div class="space-y-2">
      <label class="block text-sm font-medium text-gray-500">数值</label>
      <div class="relative">
        <input
          v-model="value"
          type="number"
          step="0.1"
          class="bili-input w-full px-4 py-3 pr-20 rounded-xl outline-none"
          placeholder="请输入数值"
          required
        />
        <span class="absolute right-4 top-1/2 -translate-y-1/2 text-gray-500 text-sm">
          {{ unit }}
        </span>
      </div>
    </div>

    <!-- 记录日期 -->
    <div class="space-y-2">
      <label class="block text-sm font-medium text-gray-500">记录日期</label>
      <input
        v-model="recordDate"
        type="date"
        class="bili-input w-full px-4 py-3 rounded-xl outline-none"
        required
      />
    </div>

    <!-- 备注 -->
    <div class="space-y-2">
      <label class="block text-sm font-medium text-gray-500">备注（选填）</label>
      <textarea
        v-model="notes"
        rows="2"
        class="bili-input w-full px-4 py-3 rounded-xl outline-none resize-none"
        placeholder="添加备注信息..."
      ></textarea>
    </div>

    <!-- 提交按钮 -->
    <button
      @click="handleSubmit"
      class="bili-button w-full py-4 rounded-xl flex items-center justify-center space-x-2 mt-6"
      :disabled="isSubmitting"
    >
      <Check v-if="isSuccess" class="w-5 h-5 text-green-400" />
      <Plus v-else-if="!isSubmitting" class="w-5 h-5" />
      <span v-if="isSubmitting" class="animate-pulse">保存中...</span>
      <span v-else-if="isSuccess">保存成功！</span>
      <span v-else>{{ props.editingData ? '保存修改' : '记录健康数据' }}</span>
    </button>
  </div>
</template>

<style scoped>
</style>
