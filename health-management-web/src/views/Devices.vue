<template>
  <div class="devices-container">
    <AppLayout title="设备管理" subtitle="管理您的健康设备和API密钥">
      <div class="devices-content">
        <GlassCard class="p-6 mb-6" animation-delay="50ms">
          <div class="huawei-panel">
            <div>
              <div class="panel-title">
                <Smartphone class="w-5 h-5" />
                <h2>华为运动健康</h2>
              </div>
              <p>通过手机号创建华为健康设备绑定，后续完成华为授权后可同步手环、手表健康数据。</p>
            </div>
            <form class="huawei-form" @submit.prevent="bindHuawei">
              <input v-model="huaweiForm.phone" type="text" placeholder="中国大陆手机号" autocomplete="tel" />
              <input v-model="huaweiForm.deviceModel" type="text" placeholder="设备型号，例如 HUAWEI Band" />
              <button type="submit" :disabled="bindingHuawei">
                {{ bindingHuawei ? '绑定中...' : '绑定华为设备' }}
              </button>
            </form>
            <div v-if="huaweiMessage" class="huawei-message">
              <p>{{ huaweiMessage }}</p>
              <a v-if="huaweiAuthUrl" :href="huaweiAuthUrl" target="_blank" rel="noreferrer">打开华为授权</a>
            </div>
          </div>
        </GlassCard>

        <!-- 设备列表 -->
        <GlassCard class="p-6 mb-6" animation-delay="100ms">
          <div class="section-header flex flex-wrap items-center justify-between gap-4">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-pink-400 to-pink-500 flex items-center justify-center">
                <Smartphone class="w-5 h-5 text-gray-900" />
              </div>
              <h2 class="text-xl font-semibold text-gray-900">我的设备</h2>
            </div>
            <button 
              @click="showRegisterModal = true" 
              class="px-6 py-3 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 text-gray-900 font-semibold transition-all duration-300 hover:shadow-lg hover:shadow-indigo-500/30 hover:scale-[1.02] flex items-center gap-2"
            >
              <Plus class="w-5 h-5" />
              注册新设备
            </button>
          </div>

          <!-- 加载状态 -->
          <div v-if="loading" class="flex flex-col items-center justify-center py-20">
            <div class="relative">
              <div class="w-16 h-16 border-4 border-pink-50 rounded-full"></div>
              <div class="absolute inset-0 w-16 h-16 border-4 border-indigo-500 rounded-full border-t-transparent animate-spin"></div>
            </div>
            <p class="text-gray-500 mt-4">加载设备列表...</p>
          </div>

          <!-- 空状态 -->
          <div v-else-if="devices.length === 0" class="text-center py-16">
            <div class="w-20 h-20 rounded-2xl bg-gray-50 mx-auto mb-6 flex items-center justify-center animate-fade-in">
              <Smartphone class="w-10 h-10 text-gray-500" />
            </div>
            <h3 class="text-xl font-semibold text-gray-500 mb-3">暂无设备</h3>
            <p class="text-gray-500 mb-6">您还没有注册任何健康设备</p>
            <button 
              @click="showRegisterModal = true" 
              class="px-8 py-3 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 text-gray-900 font-semibold transition-all duration-300 hover:shadow-lg hover:shadow-indigo-500/30"
            >
              立即注册设备
            </button>
          </div>

          <!-- 设备网格 -->
          <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 mt-4">
            <div 
              v-for="(device, index) in devices" 
              :key="device.deviceId" 
              class="p-5 rounded-xl bg-gray-50 border border-gray-200 transition-all duration-300 hover:bg-gray-100 hover:shadow-xl hover:shadow-indigo-500/10 hover:-translate-y-1 group"
              :style="{ animationDelay: `${200 + index * 100}ms` }"
            >
              <div class="flex items-start justify-between mb-4">
                <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-pink-50 to-purple-500/20 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <Smartphone class="w-6 h-6 text-pink-400" />
                </div>
                <span 
                  class="px-3 py-1 rounded-full text-xs font-semibold flex items-center gap-1"
                  :class="getStatusClass(device.status)"
                >
                  <span class="w-1.5 h-1.5 rounded-full" :class="getStatusDotClass(device.status)"></span>
                  {{ getStatusLabel(device.status) }}
                </span>
              </div>

              <div class="mb-4">
                <h3 class="text-lg font-semibold text-gray-900 group-hover:text-pink-400 transition-colors">{{ device.deviceName }}</h3>
                <p class="text-gray-500 text-sm mt-1">{{ getDeviceTypeLabel(device.deviceType) }}</p>
              </div>

              <div class="space-y-2 mb-4">
                <div class="flex items-center justify-between text-sm">
                  <span class="text-gray-500 flex items-center gap-1">
                    <Hash class="w-4 h-4" />
                    设备ID
                  </span>
                  <span class="text-gray-600 font-mono text-xs">{{ device.deviceId }}</span>
                </div>
                <div class="flex items-center justify-between text-sm">
                  <span class="text-gray-500 flex items-center gap-1">
                    <Cpu class="w-4 h-4" />
                    型号
                  </span>
                  <span class="text-gray-600">{{ device.deviceModel || '未知' }}</span>
                </div>
                <div class="flex items-center justify-between text-sm">
                  <span class="text-gray-500 flex items-center gap-1">
                    <Calendar class="w-4 h-4" />
                    最后活跃
                  </span>
                  <span class="text-gray-600">{{ formatDate(device.lastActive) }}</span>
                </div>
              </div>

              <div class="flex gap-2">
                <button 
                  @click="showApiKey(device)" 
                  class="flex-1 py-2.5 rounded-lg bg-gray-50 border border-gray-200 text-gray-600 font-medium transition-all duration-300 hover:bg-pink-50 hover:text-pink-400 hover:border-indigo-500/30 flex items-center justify-center gap-2"
                >
                  <Key class="w-4 h-4" />
                  API密钥
                </button>
                <button
                  v-if="device.deviceType === 'huawei_health'"
                  @click="syncHuawei(device)"
                  class="flex-1 py-2.5 rounded-lg bg-gray-50 border border-gray-200 text-gray-600 font-medium transition-all duration-300 hover:bg-pink-50 hover:text-pink-400 hover:border-indigo-500/30 flex items-center justify-center gap-2"
                >
                  <RefreshCw class="w-4 h-4" />
                  同步
                </button>
                <button 
                  @click="confirmDelete(device)" 
                  class="flex-1 py-2.5 rounded-lg bg-gray-50 border border-gray-200 text-gray-600 font-medium transition-all duration-300 hover:bg-red-500/20 hover:text-red-300 hover:border-red-500/30 flex items-center justify-center gap-2"
                >
                  <Trash2 class="w-4 h-4" />
                  删除
                </button>
              </div>
            </div>
          </div>
        </GlassCard>

        <!-- 使用说明 -->
        <GlassCard class="p-6" animation-delay="300ms">
          <div class="flex items-center gap-3 mb-4">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-pink-500 to-rose-600 flex items-center justify-center">
              <BookOpen class="w-5 h-5 text-gray-900" />
            </div>
            <h2 class="text-xl font-semibold text-gray-900">接口使用说明</h2>
          </div>
          <div class="guide-content">
            <p class="text-gray-500 mb-4">注册设备后，您将获得设备ID和API密钥，用于设备数据写入。详细的使用说明和代码示例请查看：</p>
            <button 
              @click="goToApiGuide" 
              class="px-6 py-3 rounded-xl bg-gradient-to-r from-pink-500 to-rose-600 text-gray-900 font-semibold transition-all duration-300 hover:shadow-lg hover:shadow-pink-200 hover:scale-[1.02] flex items-center gap-2"
            >
              <BookOpen class="w-5 h-5" />
              查看完整接口使用指南
            </button>
          </div>
        </GlassCard>
      </div>
    </AppLayout>

    <!-- 注册设备模态框 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showRegisterModal" class="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div class="bg-white rounded-2xl p-6 w-full max-w-lg border border-gray-200 shadow-lg">
            <div class="flex items-center justify-between mb-6">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-pink-400 to-pink-500 flex items-center justify-center">
                  <Plus class="w-5 h-5 text-gray-900" />
                </div>
                <h2 class="text-xl font-semibold text-gray-900">注册新设备</h2>
              </div>
              <button 
                @click="closeRegisterModal" 
                class="p-2 rounded-lg hover:bg-gray-100 text-gray-500 hover:text-gray-900 transition-colors"
              >
                <X class="w-5 h-5" />
              </button>
            </div>

            <form @submit.prevent="registerDevice" class="space-y-4">
              <div>
                <label class="block text-sm text-gray-500 mb-2">设备名称 *</label>
                <input
                  v-model="registerForm.deviceName"
                  type="text"
                  placeholder="例如：小米手环8"
                  required
                  class="bili-input w-full px-4 py-3 rounded-xl outline-none"
                />
              </div>

              <div>
                <label class="block text-sm text-gray-500 mb-2">设备类型 *</label>
                <el-select
                  v-model="registerForm.deviceType"
                  class="bili-el-select w-full"
                  placeholder="请选择设备类型"
                  popper-class="bili-select-dropdown"
                >
                  <el-option
                    v-for="type in deviceTypes"
                    :key="type.value"
                    :label="type.label"
                    :value="type.value"
                  />
                </el-select>
              </div>

              <div>
                <label class="block text-sm text-gray-500 mb-2">设备型号</label>
                <input
                  v-model="registerForm.deviceModel"
                  type="text"
                  placeholder="例如：Xiaomi Band 8"
                  class="bili-input w-full px-4 py-3 rounded-xl outline-none"
                />
              </div>

              <div>
                <label class="block text-sm text-gray-500 mb-2">制造商</label>
                <input
                  v-model="registerForm.manufacturer"
                  type="text"
                  placeholder="例如：Xiaomi"
                  class="bili-input w-full px-4 py-3 rounded-xl outline-none"
                />
              </div>

              <div class="flex gap-3">
                <button 
                  type="button" 
                  @click="closeRegisterModal" 
                  class="flex-1 py-3 rounded-xl bg-gray-50 border border-gray-200 text-gray-600 font-medium transition-all duration-300 hover:bg-gray-100"
                >
                  取消
                </button>
                <button 
                  type="submit" 
                  :disabled="registering"
                  class="flex-1 py-3 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 text-gray-900 font-semibold transition-all duration-300 hover:shadow-lg hover:shadow-indigo-500/30 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {{ registering ? '注册中...' : '注册设备' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- API密钥显示模态框 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showApiKeyModal" class="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div class="bg-white rounded-2xl p-6 w-full max-w-lg border border-gray-200 shadow-lg">
            <div class="flex items-center justify-between mb-6">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center">
                  <Key class="w-5 h-5 text-gray-900" />
                </div>
                <h2 class="text-xl font-semibold text-gray-900">API密钥信息</h2>
              </div>
              <button 
                @click="closeApiKeyModal" 
                class="p-2 rounded-lg hover:bg-gray-100 text-gray-500 hover:text-gray-900 transition-colors"
              >
                <X class="w-5 h-5" />
              </button>
            </div>

            <div class="space-y-4 mb-6">
              <div class="p-4 rounded-xl bg-gray-50 border border-gray-200">
                <div class="flex items-center justify-between">
                  <span class="text-gray-500 text-sm">设备名称</span>
                  <span class="text-gray-900 font-medium">{{ currentDevice?.deviceName }}</span>
                </div>
              </div>
              <div class="p-4 rounded-xl bg-gray-50 border border-gray-200">
                <div class="flex items-center justify-between">
                  <span class="text-gray-500 text-sm">设备ID</span>
                  <span class="text-gray-900 font-medium font-mono text-sm">{{ currentDevice?.deviceId }}</span>
                </div>
              </div>
              <div class="p-4 rounded-xl bg-gray-50 border border-gray-200">
                <div class="flex items-center justify-between mb-3">
                  <span class="text-gray-500 text-sm">API密钥</span>
                  <div class="flex gap-2">
                    <button 
                      @click="showKey = !showKey" 
                      class="px-3 py-1.5 rounded-lg bg-white/10 text-gray-600 text-sm hover:bg-white/20 transition-colors"
                    >
                      {{ showKey ? '隐藏' : '显示' }}
                    </button>
                    <button 
                      @click="copyApiKey" 
                      class="px-3 py-1.5 rounded-lg bg-white/10 text-gray-600 text-sm hover:bg-white/20 transition-colors flex items-center gap-1"
                    >
                      <Copy class="w-4 h-4" />
                      复制
                    </button>
                  </div>
                </div>
                <div class="p-3 rounded-lg bg-black/30 font-mono text-sm" :class="showKey ? 'text-emerald-400' : 'text-gray-600'">
                  {{ showKey ? apiKey : apiKey?.replace(/./g, '*') }}
                </div>
              </div>
            </div>

            <div class="p-4 rounded-xl bg-amber-500/10 border border-amber-500/20">
              <div class="flex items-start gap-3">
                <AlertTriangle class="w-5 h-5 text-amber-400 flex-shrink-0 mt-0.5" />
                <div>
                  <p class="text-amber-400 font-semibold mb-2">重要提示</p>
                  <ul class="text-sm text-gray-500 space-y-1">
                    <li>请妥善保管API密钥，不要泄露给他人</li>
                    <li>API密钥用于设备身份验证，请勿在客户端代码中暴露</li>
                    <li>如密钥泄露，请删除设备并重新注册</li>
                  </ul>
                </div>
              </div>
            </div>

            <div class="mt-6">
              <button 
                @click="closeApiKeyModal" 
                class="w-full py-3 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 text-gray-900 font-semibold transition-all duration-300 hover:shadow-lg hover:shadow-emerald-500/30"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 删除确认模态框 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showDeleteModal" class="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div class="bg-white rounded-2xl p-6 w-full max-w-md border border-gray-200 shadow-lg">
            <div class="flex items-center justify-between mb-6">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center">
                  <AlertTriangle class="w-5 h-5 text-gray-900" />
                </div>
                <h2 class="text-xl font-semibold text-gray-900">确认删除</h2>
              </div>
              <button 
                @click="closeDeleteModal" 
                class="p-2 rounded-lg hover:bg-gray-100 text-gray-500 hover:text-gray-900 transition-colors"
              >
                <X class="w-5 h-5" />
              </button>
            </div>

            <div class="text-center py-4">
              <div class="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center mx-auto mb-4">
                <Trash2 class="w-8 h-8 text-red-400" />
              </div>
              <p class="text-gray-600 mb-2">您确定要删除设备 <strong class="text-gray-900">{{ deviceToDelete?.deviceName }}</strong> 吗？</p>
              <p class="text-red-400 text-sm">此操作不可逆，设备的API密钥将失效。</p>
            </div>

            <div class="flex gap-3 mt-6">
              <button 
                @click="closeDeleteModal" 
                class="flex-1 py-3 rounded-xl bg-gray-50 border border-gray-200 text-gray-600 font-medium transition-all duration-300 hover:bg-gray-100"
              >
                取消
              </button>
              <button 
                @click="deleteDevice" 
                :disabled="deleting"
                class="flex-1 py-3 rounded-xl bg-gradient-to-r from-red-500 to-rose-600 text-gray-900 font-semibold transition-all duration-300 hover:shadow-lg hover:shadow-red-500/30 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {{ deleting ? '删除中...' : '确认删除' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AppLayout from '../components/common/AppLayout.vue';
import GlassCard from '../components/common/GlassCard.vue';
import {
  bindHuaweiDevice,
  getUserDevices,
  registerDevice as registerDeviceApi,
  deleteDevice as deleteDeviceApi,
  syncHuaweiDevice,
  deviceTypes,
  getDeviceTypeLabel,
  getStatusLabel,
  getStatusClass
} from '../api/devices';
import type { DeviceInfoResponse, DeviceRegistrationRequest } from '../api/devices';
import { 
  Smartphone, Plus, Hash, Cpu, Calendar, Key, Trash2, 
  BookOpen, X, Copy, AlertTriangle, RefreshCw
} from 'lucide-vue-next';

const router = useRouter();

// 状态管理
const devices = ref<DeviceInfoResponse[]>([]);
const loading = ref(true);
const registering = ref(false);
const deleting = ref(false);
const bindingHuawei = ref(false);

// 模态框状态
const showRegisterModal = ref(false);
const showApiKeyModal = ref(false);
const showDeleteModal = ref(false);
const huaweiMessage = ref('');
const huaweiAuthUrl = ref('');

// 表单数据
const registerForm = ref<DeviceRegistrationRequest>({
  deviceName: '',
  deviceType: '',
  deviceModel: '',
  manufacturer: '',
  firmwareVersion: '',
  description: ''
});

const huaweiForm = ref({
  phone: '',
  deviceModel: '',
  deviceName: '华为运动健康'
});

// 当前操作设备
const currentDevice = ref<DeviceInfoResponse | null>(null);
const deviceToDelete = ref<DeviceInfoResponse | null>(null);
const apiKey = ref('');
const showKey = ref(false);

// 获取状态点颜色
const getStatusDotClass = (status: string) => {
  switch (status.toLowerCase()) {
    case 'active': return 'bg-emerald-400';
    case 'inactive':
    case 'pending_auth': return 'bg-amber-400';
    default: return 'bg-gray-400';
  }
};

// 加载设备列表
const loadDevices = async () => {
  try {
    loading.value = true;
    const response = await getUserDevices();
    devices.value = response.data || [];
  } catch (error) {
    console.error('加载设备列表失败:', error);
    ElMessage.error('加载设备列表失败，请稍后重试');
  } finally {
    loading.value = false;
  }
};

// 注册设备
const registerDevice = async () => {
  if (!registerForm.value.deviceName.trim() || !registerForm.value.deviceType) {
    ElMessage.warning('请完整填写设备名称和设备类型');
    return;
  }

  try {
    registering.value = true;
    const response = await registerDeviceApi(registerForm.value);

    ElMessage.success(response.data.message || '设备注册成功');
    closeRegisterModal();
    currentDevice.value = {
      id: 0,
      deviceId: response.data.deviceId,
      deviceName: response.data.deviceName,
      deviceType: response.data.deviceType,
      deviceModel: response.data.deviceModel,
      manufacturer: response.data.manufacturer,
      firmwareVersion: response.data.firmwareVersion,
      status: response.data.status,
      createdAt: response.data.createdAt
    };
    apiKey.value = response.data.apiKey;
    showKey.value = true;
    showApiKeyModal.value = true;
    await loadDevices();
  } catch (error: any) {
    console.error('设备注册失败:', error);
    ElMessage.error(error.response?.data?.message || '设备注册失败，请稍后重试');
  } finally {
    registering.value = false;
  }
};

const bindHuawei = async () => {
  if (!/^1[3-9]\d{9}$/.test(huaweiForm.value.phone)) {
    ElMessage.warning('请输入中国大陆手机号');
    return;
  }
  try {
    bindingHuawei.value = true;
    const response = await bindHuaweiDevice({
      phone: huaweiForm.value.phone.trim(),
      deviceName: huaweiForm.value.deviceName,
      deviceModel: huaweiForm.value.deviceModel.trim() || undefined
    });
    huaweiMessage.value = `${response.data.message}${response.data.nextAction ? ' ' + response.data.nextAction : ''}`;
    huaweiAuthUrl.value = response.data.authorizationUrl || '';
    ElMessage.success('华为设备绑定已创建');
    await loadDevices();
  } catch (error: any) {
    console.error('华为设备绑定失败:', error);
    ElMessage.error(error.response?.data?.message || '华为设备绑定失败');
  } finally {
    bindingHuawei.value = false;
  }
};

const syncHuawei = async (device: DeviceInfoResponse) => {
  try {
    const response = await syncHuaweiDevice(device.deviceId);
    ElMessage.info(response.data.message || '同步请求已处理');
    if (response.data.nextAction) {
      huaweiMessage.value = response.data.nextAction;
    }
    await loadDevices();
  } catch (error: any) {
    console.error('华为设备同步失败:', error);
    ElMessage.error(error.response?.data?.message || '华为设备同步失败');
  }
};

// 显示API密钥
const showApiKey = (device: DeviceInfoResponse) => {
  currentDevice.value = device;
  apiKey.value = `HMS-${device.deviceId.replace(/-/g, '').substring(0, 28).toUpperCase()}`;
  showKey.value = false;
  showApiKeyModal.value = true;
};

// 复制API密钥
const copyApiKey = async () => {
  try {
    await navigator.clipboard.writeText(apiKey.value);
    ElMessage.success('API密钥已复制到剪贴板');
  } catch (error) {
    ElMessage.error('复制失败，请手动复制');
  }
};

// 确认删除设备
const confirmDelete = (device: DeviceInfoResponse) => {
  deviceToDelete.value = device;
  showDeleteModal.value = true;
};

// 删除设备
const deleteDevice = async () => {
  if (!deviceToDelete.value) return;

  try {
    deleting.value = true;
    await deleteDeviceApi(deviceToDelete.value.deviceId);

    ElMessage.success('设备删除成功');
    closeDeleteModal();
    await loadDevices();
  } catch (error: any) {
    console.error('设备删除失败:', error);
    ElMessage.error(error.response?.data?.message || '设备删除失败，请稍后重试');
  } finally {
    deleting.value = false;
  }
};

// 关闭模态框
const closeRegisterModal = () => {
  showRegisterModal.value = false;
  registerForm.value = {
    deviceName: '',
    deviceType: '',
    deviceModel: '',
    manufacturer: '',
    firmwareVersion: '',
    description: ''
  };
};

const closeApiKeyModal = () => {
  showApiKeyModal.value = false;
  currentDevice.value = null;
  apiKey.value = '';
  showKey.value = false;
};

const closeDeleteModal = () => {
  showDeleteModal.value = false;
  deviceToDelete.value = null;
};

// 跳转到API指南
const goToApiGuide = () => {
  router.push('/api-guide');
};

// 格式化日期
const formatDate = (dateStr?: string) => {
  if (!dateStr) return '从未活跃';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

// 组件挂载时加载数据
onMounted(() => {
  loadDevices();
});
</script>

<style scoped>
.huawei-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 1.1fr);
  gap: 1.2rem;
  align-items: start;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  color: var(--ink-stone);
}

.panel-title h2 {
  font-size: 1.2rem;
  font-weight: 700;
}

.huawei-panel p {
  margin-top: 0.65rem;
  color: var(--ink-muted);
  line-height: 1.8;
}

.huawei-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 0.7rem;
}

.huawei-form input {
  min-width: 0;
  border: 1px solid var(--ink-line);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.9);
  padding: 0.75rem 0.85rem;
  color: var(--ink-stone);
  outline: none;
}

.huawei-form input:focus {
  border-color: var(--ink-pine);
  box-shadow: 0 0 0 3px rgba(59, 107, 87, 0.12);
}

.huawei-form button {
  border-radius: 8px;
  background: var(--ink-pine);
  padding: 0.75rem 1rem;
  color: #fffdf6;
  font-weight: 700;
  white-space: nowrap;
}

.huawei-form button:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.huawei-message {
  grid-column: 1 / -1;
  border: 1px solid rgba(121, 104, 78, 0.18);
  border-radius: 8px;
  background: rgba(247, 243, 232, 0.55);
  padding: 0.85rem 1rem;
}

.huawei-message p {
  margin: 0;
}

.huawei-message a {
  margin-top: 0.55rem;
  display: inline-block;
  color: var(--ink-pine-dark);
  font-weight: 700;
}

.modal-enter-active,
.modal-leave-active {
  transition: all 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from > div,
.modal-leave-to > div {
  transform: scale(0.9) translateY(20px);
}

@media (max-width: 920px) {
  .huawei-panel,
  .huawei-form {
    grid-template-columns: 1fr;
  }
}
</style>
