<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowRight, Eye, EyeOff, Lock, Phone, ShieldCheck, User } from 'lucide-vue-next';
import { ElMessage } from 'element-plus';
import { createCaptcha, login, loginWithPhoneCode, sendSmsCode } from '../api/auth';
import type { CaptchaChallengeResponse } from '../api/auth';

const router = useRouter();
const route = useRoute();
const loginMode = ref<'password' | 'sms'>('password');
const account = ref('');
const password = ref('');
const phone = ref('');
const smsCode = ref('');
const isLoading = ref(false);
const sendingCode = ref(false);
const showPassword = ref(false);
const captcha = ref<CaptchaChallengeResponse | null>(null);
const selectedCaptchaOption = ref('');
const smsCountdown = ref(0);
let countdownTimer: number | undefined;

const saveAuth = (token: string, userId: number, username: string) => {
  localStorage.setItem('token', token);
  localStorage.setItem('userId', userId.toString());
  localStorage.setItem('username', username);
};

const goAfterLogin = () => {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
  router.push(redirect || '/');
};

const handleLogin = async () => {
  if (loginMode.value === 'sms') {
    await handlePhoneLogin();
    return;
  }
  if (!account.value || !password.value) {
    ElMessage.warning('请输入账号和密码');
    return;
  }

  try {
    isLoading.value = true;
    const res = await login({
      account: account.value,
      password: password.value
    });

    ElMessage.success('登录成功');
    saveAuth(res.data.token, res.data.userId, res.data.username);

    goAfterLogin();
  } catch {
    // Error handled by interceptor
  } finally {
    isLoading.value = false;
  }
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
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    ElMessage.warning('请输入中国大陆手机号');
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
      phone: phone.value.trim(),
      purpose: 'login',
      captchaToken: captcha.value.token,
      selectedOptionId: selectedCaptchaOption.value
    });
    if (res.data.debugCode) {
      smsCode.value = res.data.debugCode;
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

const handlePhoneLogin = async () => {
  if (!/^1[3-9]\d{9}$/.test(phone.value) || !/^\d{6}$/.test(smsCode.value)) {
    ElMessage.warning('请输入手机号和 6 位验证码');
    return;
  }
  try {
    isLoading.value = true;
    const res = await loginWithPhoneCode({
      phone: phone.value.trim(),
      smsCode: smsCode.value.trim()
    });
    ElMessage.success('登录成功');
    saveAuth(res.data.token, res.data.userId, res.data.username);
    goAfterLogin();
  } catch {
    // Error handled by interceptor
  } finally {
    isLoading.value = false;
  }
};

loadCaptcha();
</script>

<template>
  <div class="auth-page">
    <section class="auth-shell">
      <div class="auth-intro">
        <div class="seal-mark">康</div>
        <h1>健康管家</h1>
        <p>记录、目标、提醒与健康报告集中管理，让日常健康信息更清楚。</p>
        <div class="intro-lines">
          <span>健康数据</span>
          <span>目标追踪</span>
          <span>提醒通知</span>
        </div>
      </div>

      <form class="auth-card" @submit.prevent="handleLogin">
        <div class="mode-tabs">
          <button type="button" :class="{ active: loginMode === 'password' }" @click="loginMode = 'password'">密码</button>
          <button type="button" :class="{ active: loginMode === 'sms' }" @click="loginMode = 'sms'">验证码</button>
        </div>

        <label v-if="loginMode === 'password'" class="form-field">
          <span>账号</span>
          <div class="input-wrap">
            <User class="h-5 w-5" />
            <input
              v-model="account"
              type="text"
              placeholder="用户名、邮箱或手机号"
              autocomplete="username"
              required
            />
          </div>
        </label>

        <label v-if="loginMode === 'password'" class="form-field">
          <span>密码</span>
          <div class="input-wrap">
            <Lock class="h-5 w-5" />
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              autocomplete="current-password"
              required
            />
            <button
              type="button"
              class="password-toggle"
              @click="showPassword = !showPassword"
              title="显示或隐藏密码"
            >
              <EyeOff v-if="showPassword" class="h-5 w-5" />
              <Eye v-else class="h-5 w-5" />
            </button>
          </div>
        </label>

        <template v-else>
          <label class="form-field">
            <span>手机号</span>
            <div class="input-wrap">
              <Phone class="h-5 w-5" />
              <input
                v-model="phone"
                type="text"
                placeholder="中国大陆手机号"
                autocomplete="tel"
                required
              />
            </div>
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
            <div class="input-wrap code-wrap">
              <ShieldCheck class="h-5 w-5" />
              <input
                v-model="smsCode"
                type="text"
                placeholder="6 位验证码"
                autocomplete="one-time-code"
                required
              />
              <button
                type="button"
                class="send-code"
                :disabled="sendingCode || smsCountdown > 0"
                @click="handleSendCode"
              >
                {{ smsCountdown > 0 ? `${smsCountdown}s` : sendingCode ? '发送中' : '发送' }}
              </button>
            </div>
          </label>
        </template>

        <button class="bili-button mt-2 flex w-full items-center justify-center gap-2 px-4 py-3" type="submit" :disabled="isLoading">
          <span>{{ isLoading ? '登录中...' : '登录' }}</span>
          <ArrowRight v-if="!isLoading" class="h-5 w-5" />
        </button>

        <p class="auth-link">
          还没有账号？
          <router-link to="/register">立即注册</router-link>
        </p>

        <div class="demo-note">
          测试账号：testuser / Test123456
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
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

.auth-shell {
  width: min(100%, 960px);
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(360px, 1fr);
  gap: 2rem;
  align-items: center;
}

.auth-intro {
  min-width: 0;
  padding: 2rem;
  color: var(--ink-stone);
}

.seal-mark {
  display: inline-flex;
  width: 3.1rem;
  height: 3.1rem;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(163, 74, 58, 0.42);
  border-radius: 8px;
  background: rgba(163, 74, 58, 0.08);
  color: var(--ink-cinnabar);
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: 1.45rem;
  font-weight: 700;
}

.auth-intro h1 {
  margin-top: 1.4rem;
  font-size: 2.5rem;
  font-weight: 700;
  letter-spacing: 0;
}

.auth-intro p {
  margin-top: 1rem;
  max-width: 25rem;
  color: var(--ink-muted);
  line-height: 1.9;
  overflow-wrap: anywhere;
}

.intro-lines {
  margin-top: 2rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.intro-lines span {
  border: 1px solid rgba(121, 104, 78, 0.24);
  border-radius: 999px;
  background: rgba(255, 253, 246, 0.62);
  padding: 0.45rem 0.85rem;
  color: var(--ink-muted);
  font-size: 0.9rem;
}

.auth-card {
  min-width: 0;
  border: 1px solid rgba(121, 104, 78, 0.24);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.94);
  box-shadow: 0 24px 60px rgba(62, 58, 53, 0.12);
  padding: 2.15rem 2rem 2rem;
}

.mode-tabs {
  margin-bottom: 1.45rem;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.35rem;
  border: 1px solid rgba(121, 104, 78, 0.2);
  border-radius: 8px;
  background: rgba(247, 243, 232, 0.58);
  padding: 0.3rem;
}

.mode-tabs button {
  border-radius: 6px;
  padding: 0.68rem 0.75rem;
  color: var(--ink-muted);
  font-weight: 600;
  transition: background 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.mode-tabs button.active {
  background: rgba(255, 253, 246, 0.95);
  color: var(--ink-pine-dark);
  box-shadow: 0 4px 16px rgba(62, 58, 53, 0.08);
}

.form-field {
  display: block;
  margin-bottom: 1.1rem;
}

.form-field > span {
  display: block;
  margin-bottom: 0.45rem;
  color: var(--ink-muted);
  font-size: 0.92rem;
}

.input-wrap {
  position: relative;
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

.input-wrap input {
  min-width: 0;
  flex: 1;
  border: 0;
  background: transparent;
  padding: 0.86rem 0;
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
  margin: -0.2rem 0 1.1rem;
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

.password-toggle {
  color: var(--ink-muted);
  transition: color 0.16s ease;
}

.password-toggle:hover {
  color: var(--ink-pine-dark);
}

.auth-link {
  margin-top: 1.35rem;
  text-align: center;
  font-size: 0.95rem;
}

.auth-link a {
  color: var(--ink-pine-dark);
  font-weight: 600;
}

.demo-note {
  margin-top: 1.2rem;
  border-top: 1px solid rgba(121, 104, 78, 0.16);
  padding-top: 1rem;
  color: var(--ink-light);
  font-size: 0.88rem;
  text-align: center;
}

@media (max-width: 820px) {
  .auth-shell {
    grid-template-columns: 1fr;
    gap: 1rem;
  }

  .auth-intro {
    padding: 0.5rem 0.2rem;
    text-align: center;
  }

  .auth-card {
    padding: 1.6rem 1.35rem 1.45rem;
  }

  .auth-intro p {
    margin-left: auto;
    margin-right: auto;
  }

  .intro-lines {
    justify-content: center;
    gap: 0.5rem;
  }
}

@media (max-width: 420px) {
  .auth-page {
    padding: 1.25rem 0.75rem;
  }

  .auth-intro h1 {
    font-size: 2.15rem;
  }

  .auth-intro p {
    font-size: 0.95rem;
    line-height: 1.8;
  }

  .intro-lines span {
    padding: 0.4rem 0.7rem;
    font-size: 0.84rem;
  }
}
</style>
