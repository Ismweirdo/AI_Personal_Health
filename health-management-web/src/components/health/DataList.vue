<script setup lang="ts">
import { ref, watch } from 'vue';
import { Calendar, Download, Edit3, FileSpreadsheet, Trash2, TrendingUp, Upload } from 'lucide-vue-next';
import { ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus';
import {
  deleteHealthData,
  downloadHealthImportTemplate,
  exportHealthData,
  getHealthDataList,
  getLabelByType,
  healthDataTypes,
  importHealthData,
  type HealthDataRequest,
  type HealthDataResponse
} from '../../api/health';
import { formatDate, formatTime } from '../../lib/utils';

const props = defineProps<{
  refreshTrigger?: number;
}>();

const emit = defineEmits<{
  deleted: [];
  edit: [item: HealthDataResponse];
  changed: [];
}>();

const dataList = ref<HealthDataResponse[]>([]);
const loading = ref(false);
const selectedType = ref('');
const startDate = ref('');
const endDate = ref('');
const fileInput = ref<HTMLInputElement | null>(null);

const loadData = async () => {
  loading.value = true;
  try {
    const res = await getHealthDataList(
      selectedType.value,
      startDate.value || undefined,
      endDate.value || undefined
    );
    dataList.value = res.data || [];
  } catch (error) {
    console.error('加载健康数据失败:', error);
  } finally {
    loading.value = false;
  }
};

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条健康数据吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });

    await deleteHealthData(id);
    ElMessage.success('删除成功');
    await loadData();
    emit('deleted');
    emit('changed');
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
    }
  }
};

const handleExport = async () => {
  const csv = await exportHealthData(selectedType.value, startDate.value || undefined, endDate.value || undefined);
  downloadText(csv, 'health-data.csv');
  ElMessage.success('导出成功');
};

const handleTemplateDownload = async () => {
  const csv = await downloadHealthImportTemplate();
  downloadText(csv, 'health-data-template.csv');
};

const openImportDialog = () => {
  fileInput.value?.click();
};

const handleImport = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  const text = await file.text();
  const rows = parseCsv(text);
  if (!rows.length) {
    ElMessage.warning('没有识别到可导入的数据');
    return;
  }

  const res = await importHealthData(rows);
  ElMessage.success(`导入完成：成功 ${res.data.successCount} 条，失败 ${res.data.failureCount} 条`);
  if (res.data.errors?.length) {
    ElMessage.warning(res.data.errors[0]);
  }
  target.value = '';
  await loadData();
  emit('changed');
};

const parseCsv = (text: string): HealthDataRequest[] => {
  const lines = text.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
  if (lines.length <= 1) return [];
  return lines.slice(1).map((line) => {
    const cells = line.match(/(".*?"|[^",\s]+)(?=\s*,|\s*$)/g)?.map(item => item.replace(/^"|"$/g, '').replace(/""/g, '"')) || [];
    return {
      type: cells[0],
      value: Number(cells[1]),
      unit: cells[2] || '',
      recordDate: cells[3],
      notes: cells[4]
    };
  }).filter(item => item.type && !Number.isNaN(item.value));
};

const downloadText = (content: string, filename: string) => {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
};

const getTypeColor = (type: string) => {
  const colors: Record<string, string> = {
    steps: 'bg-blue-500',
    heart_rate: 'bg-red-500',
    sleep: 'bg-purple-500',
    weight: 'bg-green-500',
    blood_pressure: 'bg-yellow-500',
    blood_sugar: 'bg-orange-500'
  };
  return colors[type] || 'bg-gray-500';
};

watch(() => props.refreshTrigger, loadData, { immediate: true });
watch([selectedType, startDate, endDate], loadData);
</script>

<template>
  <div class="data-list">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-gray-900 flex items-center gap-2">
        <TrendingUp class="w-5 h-5 text-ink-pine" />
        健康数据记录
      </h3>
      
      <div class="flex flex-wrap items-center gap-3">
        <el-select
          v-model="selectedType"
          class="bili-el-select text-sm w-40"
          clearable
          placeholder="全部类型"
          popper-class="glass-select-dropdown"
        >
          <el-option
            v-for="t in healthDataTypes"
            :key="t.value"
            :label="t.label"
            :value="t.value"
          />
        </el-select>
        <input v-model="startDate" type="date" class="glass-input px-3 py-2 rounded-lg text-sm" />
        <input v-model="endDate" type="date" class="glass-input px-3 py-2 rounded-lg text-sm" />
        <button class="action-button" @click="handleTemplateDownload">
          <FileSpreadsheet class="w-4 h-4" />
          模板
        </button>
        <button class="action-button" @click="openImportDialog">
          <Upload class="w-4 h-4" />
          导入
        </button>
        <button class="action-button" @click="handleExport">
          <Download class="w-4 h-4" />
          导出
        </button>
        <input ref="fileInput" type="file" accept=".csv" class="hidden" @change="handleImport" />
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="text-center py-8">
      <div class="animate-spin w-8 h-8 border-2 border-ink-pine border-t-transparent rounded-full mx-auto"></div>
      <p class="text-gray-500 mt-2">加载中...</p>
    </div>

    <!-- 空状态 -->
    <div v-else-if="dataList.length === 0" class="text-center py-8">
      <div class="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
        <Calendar class="w-8 h-8 text-gray-500" />
      </div>
      <p class="text-gray-500">暂无健康数据</p>
      <p class="text-gray-500 text-sm mt-1">开始记录您的第一条健康数据吧</p>
    </div>

    <!-- 数据列表 -->
    <div v-else class="space-y-3 max-h-96 overflow-y-auto">
      <div
        v-for="item in dataList"
        :key="item.id"
        class="glass-card p-4 rounded-xl hover:bg-white/5 transition-colors group"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <!-- 类型标签 -->
            <div 
              class="w-10 h-10 rounded-lg flex items-center justify-center text-gray-900 text-sm font-bold"
              :class="getTypeColor(item.type)"
            >
              {{ getLabelByType(item.type).charAt(0) }}
            </div>
            
            <div>
              <div class="flex items-center gap-2">
                <span class="text-gray-900 font-medium">
                  {{ getLabelByType(item.type) }}
                </span>
                <span class="text-2xl font-bold text-gray-900">
                  {{ item.value }}
                  <span class="text-sm text-gray-500 font-normal">{{ item.unit }}</span>
                </span>
              </div>
              <div class="flex items-center gap-2 text-sm text-gray-500 mt-1">
                <Calendar class="w-3 h-3" />
                <span>{{ formatDate(item.recordDate) }}</span>
                <span>{{ formatTime(item.createdAt) }}</span>
              </div>
            </div>
          </div>
          
          <div class="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
            <button @click="emit('edit', item)" class="p-2 text-gray-500 hover:text-ink-pine hover:bg-[rgba(59,107,87,0.08)] rounded-lg transition-all">
              <Edit3 class="w-4 h-4" />
            </button>
            <button @click="handleDelete(item.id)" class="p-2 text-gray-500 hover:text-ink-cinnabar hover:bg-[rgba(163,74,58,0.08)] rounded-lg transition-all">
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>
        
        <!-- 备注 -->
        <p v-if="item.notes" class="mt-2 text-sm text-gray-500 pl-13">
          {{ item.notes }}
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.data-list {
  width: 100%;
}

.glass-card {
  background: rgba(255, 253, 246, 0.82);
  border: 1px solid rgba(121, 104, 78, 0.18);
  backdrop-filter: none;
}

.action-button {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.85rem;
  border-radius: 0.5rem;
  background: rgba(255, 253, 246, 0.82);
  border: 1px solid rgba(121, 104, 78, 0.18);
  color: var(--ink-stone);
}

/* 滚动条样式 */
.max-h-96::-webkit-scrollbar {
  width: 6px;
}

.max-h-96::-webkit-scrollbar-track {
  background: rgba(239, 231, 217, 0.8);
  border-radius: 3px;
}

.max-h-96::-webkit-scrollbar-thumb {
  background: rgba(62, 58, 53, 0.24);
  border-radius: 3px;
}

.max-h-96::-webkit-scrollbar-thumb:hover {
  background: rgba(62, 58, 53, 0.38);
}

.pl-13 {
  padding-left: 3.25rem;
}
</style>
