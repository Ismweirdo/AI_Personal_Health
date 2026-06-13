import request from '../utils/request';

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  phone: string;
  smsCode: string;
}

export interface LoginRequest {
  account: string;
  password: string;
}

export interface PhoneCodeLoginRequest {
  phone: string;
  smsCode: string;
}

export interface CaptchaOption {
  id: string;
  imageData: string;
  alt: string;
}

export interface CaptchaChallengeResponse {
  token: string;
  prompt: string;
  options: CaptchaOption[];
  expiresInSeconds: number;
}

export interface SendSmsCodeRequest {
  phone: string;
  purpose: 'register' | 'login';
  captchaToken: string;
  selectedOptionId: string;
}

export interface SmsCodeResponse {
  message: string;
  expiresInSeconds: number;
  resendAfterSeconds: number;
  debugCode?: string;
}

export interface AuthResponse {
  token: string;
  refreshToken?: string;
  userId: number;
  username: string;
}

export function createCaptcha() {
  return request.get<any, { data: CaptchaChallengeResponse }>('/auth/captcha');
}

export function sendSmsCode(data: SendSmsCodeRequest) {
  return request.post<any, { data: SmsCodeResponse }>('/auth/sms-code', data);
}

export function register(data: RegisterRequest) {
  return request.post<any, { data: AuthResponse }>('/auth/register', data);
}

export function login(data: LoginRequest) {
  return request.post<any, { data: AuthResponse }>('/auth/login', data);
}

export function loginWithPhoneCode(data: PhoneCodeLoginRequest) {
  return request.post<any, { data: AuthResponse }>('/auth/login/phone-code', data);
}

export function logout() {
  return request.post('/auth/logout');
}

export function clearAuth() {
  localStorage.removeItem('token');
  localStorage.removeItem('userId');
  localStorage.removeItem('username');
}
