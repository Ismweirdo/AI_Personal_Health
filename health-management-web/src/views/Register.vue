<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ArrowRight, Lock, Mail, Phone, ShieldCheck, User } from 'lucide-vue-next';
import { ElMessage } from 'element-plus';
import { createCaptcha, register, sendSmsCode } from '../api/auth';
import type { CaptchaChallengeResponse } from '../api/auth';

const router = useRouter();
const form = ref({
  username: '',
  email: '',
  phone: '',
  smsCode: '',
  password: '',
  confirmPassword: ''
});

const errors = ref({
  username: '',
  email: '',
  phone: '',
  smsCode: '',
  password: '',
  confirmPassword: ''
});

const isLoading = ref(false);
const sendingCode = ref(false);
const captcha = ref<CaptchaChallengeResponse | null>(null);
const selectedCaptchaOption = ref('');
const smsCountdown = ref(0);
let countdownTimer: number | undefined;

const passwordStrength = computed(() => {
  const p = form.value.password;
  if (!p) return 0;
  let strength = 0;
  if (p.length >= 8) strength += 25;
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) strength += 25;
  if (/\d/.test(p)) strength += 25;
  if (/[@$!%*?&]/.test(p)) strength += 25;
  return strength;
});

const strengthColor = computed(() => {
  if (passwordStrength.value <= 33) return 'bg-red-500';
  if (passwordStrength.value <= 66) return 'bg-yellow-500';
  return 'bg-green-500';
});

const strengthLabel = computed(() => {
  if (!form.value.password) return '未输入';
  if (passwordStrength.value <= 33) return '弱';
  if (passwordStrength.value <= 66) return '中等';
  return '强';
});

const validateForm = () => {
  let isValid = true;
  errors.value = { username: '', email: '', phone: '', smsCode: '', password: '', confirmPassword: '' };

  if (!form.value.username) {
    errors.value.username = '请输入用户名';
    isValid = false;
  } else if (form.value.username.trim().length > 50) {
    errors.value.username = '用户名长度不能超过50';
    isValid = false;
  }
  if (!form.value.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)) {
    errors.value.email = '请输入正确的邮箱';
    isValid = false;
  } else if (form.value.email.trim().length > 100) {
    errors.value.email = '邮箱长度不能超过100';
    isValid = false;
  }
  if (!/^1[3-9]\d{9}$/.test(form.value.phone)) {
    errors.value.phone = '手机号格式不正确';
    isValid = false;
  }
  if (!/^\d{6}$/.test(form.value.smsCode)) {
    errors.value.smsCode = '请输入 6 位短信验证码';
    isValid = false;
  }
  if (!form.value.password) {
    errors.value.password = '请输入密码';
    isValid = false;
  } else if (form.value.password.length > 72) {
    errors.value.password = '密码长度不能超过72';
    isValid = false;
  }
  if (!form.value.confirmPassword) {
    errors.value.confirmPassword = '请确认密码';
    isValid = false;
  } else if (form.value.password !== form.value.confirmPassword) {
    errors.value.confirmPassword = '两次输入的密码不一致';
    isValid = false;
  }
  return isValid;
};

const loadCaptcha = async () => {
  selectedCaptchaOption.value = '';
  const res = await createCaptcha();
  captcha.value = res.data;
};

const startCountdown = (seconds: number) => {
  smsCountdown.value = seconds;
  if (countdownTimer) {
    window.clearInterval(countdownTimer);
  }
  countdownTimer = window.setInterval(() => {
    smsCountdown.value -= 1;
    if (smsCountdown.value <= 0 && countdownTimer) {
      window.clearInterval(countdownTimer);
      countdownTimer = undefined;
    }
  }, 1000);
};

const handleSendCode = async () => {
  if (!/^1[3-9]\d{9}$/.test(form.value.phone)) {
    errors.value.phone = '请输入中国大陆手机号';
    return;
  }
  if (!captcha.value) {
    await loadCaptcha();
    ElMessage.info('请先选择正确图像');
    return;
  }
  if (!selectedCaptchaOption.value) {
    ElMessage.warning('请先完成人机验证');
    return;
  }

  try {
    sendingCode.value = true;
    const res = await sendSmsCode({
      phone: form.value.phone.trim(),
      purpose: 'register',
      captchaToken: captcha.value.token,
      selectedOptionId: selectedCaptchaOption.value
    });
    if (res.data.debugCode) {
      form.value.smsCode = res.data.debugCode;
      ElMessage.success(`本地验证码：${res.data.debugCode}`);
    } else {
      ElMessage.success(res.data.message || '验证码已发送');
    }
    startCountdown(res.data.resendAfterSeconds || 60);
    await loadCaptcha();
  } catch {
    await loadCaptcha();
  } finally {
    sendingCode.value = false;
  }
};

const handleRegister = async () => {
  if (!validateForm()) return;

  try {
    isLoading.value = true;
    const res = await register({
      username: form.value.username.trim(),
      email: form.value.email.trim(),
      password: form.value.password,
      phone: form.value.phone.trim(),
      smsCode: form.value.smsCode.trim()
    });

    ElMessage.success('注册成功');
    localStorage.setItem('token', res.data.token);
    localStorage.setItem('userId', res.data.userId.toString());
    localStorage.setItem('username', res.data.username);

    router.push('/');
  } catch {
    // Error handled by interceptor
  } finally {
    isLoading.value = false;
  }
};

loadCaptcha();
</script>

<template>
  <div class="register-page">
    <form class="register-card" @submit.prevent="handleRegister">
      <div class="mb-7 text-center">
        <div class="seal-mark">康</div>
        <h1>创建账户</h1>
        <p>建立您的个人健康档案</p>
      </div>

      <label class="form-field">
        <span>用户名</span>
        <div class="input-wrap" :class="{ 'input-error': errors.username }">
          <User class="h-5 w-5" />
          <input v-model="form.username" type="text" placeholder="请输入用户名" autocomplete="username" />
        </div>
        <p v-if="errors.username" class="error-text">{{ errors.username }}</p>
      </label>

      <label class="form-field">
        <span>邮箱</span>
        <div class="input-wrap" :class="{ 'input-error': errors.email }">
          <Mail class="h-5 w-5" />
          <input v-model="form.email" type="email" placeholder="请输入邮箱" autocomplete="email" />
        </div>
        <p v-if="errors.email" class="error-text">{{ errors.email }}</p>
      </label>

      <label class="form-field">
        <span>手机号</span>
        <div class="input-wrap" :class="{ 'input-error': errors.phone }">
          <Phone class="h-5 w-5" />
          <input v-model="form.phone" type="text" placeholder="11 位手机号" autocomplete="tel" />
        </div>
        <p v-if="errors.phone" class="error-text">{{ errors.phone }}</p>
      </label>

      <div class="captcha-panel">
        <div class="captcha-title">
          <ShieldCheck class="h-4 w-4" />
          <span>{{ captcha?.prompt || '加载人机验证中' }}</span>
          <button type="button" @click="loadCaptcha">换一组</button>
        </div>
        <div class="captcha-options">
          <button
            v-for="option in captcha?.options || []"
            :key="option.id"
            type="button"
            :class="{ active: selectedCaptchaOption === option.id }"
            @click="selectedCaptchaOption = option.id"
          >
            <img :src="option.imageData" :alt="option.alt" />
          </button>
        </div>
      </div>

      <label class="form-field">
        <span>短信验证码</span>
        <div class="input-wrap code-wrap" :class="{ 'input-error': errors.smsCode }">
          <ShieldCheck class="h-5 w-5" />
          <input v-model="form.smsCode" type="text" placeholder="6 位验证码" autocomplete="one-time-code" />
          <button
            type="button"
            class="send-code"
            :disabled="sendingCode || smsCountdown > 0"
            @click="handleSendCode"
          >
            {{ smsCountdown > 0 ? `${smsCountdown}s` : sendingCode ? '发送中' : '发送' }}
          </button>
        </div>
        <p v-if="errors.smsCode" class="error-text">{{ errors.smsCode }}</p>
      </label>

      <label class="form-field">
        <span>密码</span>
        <div class="input-wrap" :class="{ 'input-error': errors.password }">
          <Lock class="h-5 w-5" />
          <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="new-password" />
        </div>
        <div class="mt-2">
          <div class="mb-1 flex items-center justify-between text-xs text-[var(--ink-muted)]">
            <span>强度：{{ strengthLabel }}</span>
            <span>{{ passwordStrength }}%</span>
          </div>
          <div class="h-1.5 overflow-hidden rounded-full bg-[var(--ink-mist)]">
            <div class="h-full transition-all duration-300" :class="strengthColor" :style="{ width: `${passwordStrength}%` }"></div>
          </div>
          <p class="mt-1 text-xs text-[var(--ink-light)]">建议至少 8 位，包含大小写字母、数字或特殊字符。</p>
        </div>
        <p v-if="errors.password" class="error-text">{{ errors.password }}</p>
      </label>

      <label class="form-field">
        <span>确认密码</span>
        <div class="input-wrap" :class="{ 'input-error': errors.confirmPassword }">
          <Lock class="h-5 w-5" />
          <input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" autocomplete="new-password" />
        </div>
        <p v-if="errors.confirmPassword" class="error-text">{{ errors.confirmPassword }}</p>
      </label>

      <button class="bili-button mt-3 flex w-full items-center justify-center gap-2 px-4 py-3" type="submit" :disabled="isLoading">
        <span>{{ isLoading ? '注册中...' : '注册并进入' }}</span>
        <ArrowRight v-if="!isLoading" class="h-5 w-5" />
      </button>

      <p class="auth-link">
        已有账号？
        <router-link to="/login">返回登录</router-link>
      </p>
    </form>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1rem;
  overflow-x: hidden;
  background:
    linear-gradient(180deg, rgba(255, 253, 246, 0.86), rgba(247, 243, 232, 0.96)),
    repeating-linear-gradient(90deg, rgba(62, 58, 53, 0.03) 0 1px, transparent 1px 22px),
    var(--ink-paper);
}

.register-card {
  width: min(100%, 520px);
  min-width: 0;
  border: 1px solid rgba(121, 104, 78, 0.24);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.94);
  box-shadow: 0 24px 60px rgba(62, 58, 53, 0.12);
  padding: 2rem;
}

.seal-mark {
  display: inline-flex;
  width: 3rem;
  height: 3rem;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(163, 74, 58, 0.42);
  border-radius: 8px;
  background: rgba(163, 74, 58, 0.08);
  color: var(--ink-cinnabar);
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: 1.35rem;
  font-weight: 700;
}

h1 {
  margin-top: 1rem;
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--ink-stone);
}

.register-card p {
  color: var(--ink-muted);
}

.form-field {
  display: block;
  margin-bottom: 1rem;
}

.form-field > span {
  display: block;
  margin-bottom: 0.42rem;
  color: var(--ink-muted);
  font-size: 0.92rem;
}

.input-wrap {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  border: 1px solid var(--ink-line);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.88);
  padding: 0 0.85rem;
  color: var(--ink-muted);
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.input-wrap:focus-within {
  border-color: var(--ink-pine);
  box-shadow: 0 0 0 3px rgba(59, 107, 87, 0.12);
}

.input-error {
  border-color: var(--ink-cinnabar);
}

.input-wrap input {
  min-width: 0;
  flex: 1;
  border: 0;
  background: transparent;
  padding: 0.78rem 0;
  color: var(--ink-stone);
  outline: none;
}

.input-wrap input::placeholder {
  color: var(--ink-light);
}

.code-wrap input {
  letter-spacing: 0.08rem;
}

.send-code {
  flex: 0 0 auto;
  border-left: 1px solid rgba(121, 104, 78, 0.22);
  padding-left: 0.8rem;
  color: var(--ink-pine-dark);
  font-weight: 600;
  white-space: nowrap;
}

.send-code:disabled {
  color: var(--ink-light);
  cursor: not-allowed;
}

.captcha-panel {
  margin: 0.2rem 0 1rem;
  border: 1px solid rgba(121, 104, 78, 0.2);
  border-radius: 8px;
  background: rgba(247, 243, 232, 0.45);
  padding: 0.85rem;
}

.captcha-title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--ink-muted);
  font-size: 0.88rem;
}

.captcha-title button {
  margin-left: auto;
  color: var(--ink-pine-dark);
  font-size: 0.82rem;
}

.captcha-options {
  margin-top: 0.7rem;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.55rem;
}

.captcha-options button {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border: 1px solid rgba(121, 104, 78, 0.2);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.88);
  padding: 0.18rem;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.captcha-options button.active {
  border-color: var(--ink-pine);
  box-shadow: 0 0 0 3px rgba(59, 107, 87, 0.12);
}

.captcha-options img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.error-text {
  margin-top: 0.35rem;
  color: var(--ink-cinnabar) !important;
  font-size: 0.78rem;
}

.auth-link {
  margin-top: 1.3rem;
  text-align: center;
  font-size: 0.95rem;
}

.auth-link a {
  color: var(--ink-pine-dark);
  font-weight: 600;
}

@media (max-width: 420px) {
  .register-page {
    padding: 1.25rem 0.75rem;
  }

  .register-card {
    padding: 1.5rem;
  }
}
</style>
