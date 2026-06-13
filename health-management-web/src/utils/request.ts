import axios from 'axios';
import { ElMessage } from 'element-plus';

type SilentRequestConfig = {
  silentError?: boolean;
};

const codeMessageMap: Record<number, string> = {
  400: '请求参数不合法',
  401: '未登录或登录已过期',
  403: '没有权限访问该资源',
  404: '请求的资源不存在',
  500: '服务器内部错误',
  1001: '用户名已存在',
  1002: '用户不存在',
  1003: '密码错误',
  1004: '账号已锁定',
  1006: '邮箱已被注册',
  1007: '手机号已被注册',
  1008: '验证码无效或已过期',
  1009: '邮箱格式不正确',
  1010: '手机号格式不正确'
};

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Request interceptor
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (typeof res.code === 'number' && res.code !== 200) {
      const message = codeMessageMap[res.code] || res.message || '请求失败';
      const config = response.config as typeof response.config & SilentRequestConfig;
      if (!config.silentError) {
        ElMessage.error(message);
      }
      return Promise.reject(new Error(message));
    }
    return res;
  },
  (error) => {
    const status = error.response?.status;
    const message = (status && codeMessageMap[status]) || error.response?.data?.message || error.message;
    const config = error.config as SilentRequestConfig | undefined;
    if (!config?.silentError) {
      ElMessage.error(message);
    }
    return Promise.reject(error);
  }
);

export default request;
