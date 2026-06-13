import { createRouter, createWebHistory } from 'vue-router';
import Home from '../views/user/Home.vue';
import AIChat from '../views/user/AIChat.vue';
import Goals from '../views/user/Goals.vue';
import Reminders from '../views/user/Reminders.vue';
import Reports from '../views/user/Reports.vue';
import Family from '../views/user/Family.vue';
import Devices from '../views/Devices.vue';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';
import ApiGuide from '../views/ApiGuide.vue';

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home,
    meta: { requiresAuth: true, title: '健康管理' },
  },
  {
    path: '/ai-chat',
    name: 'AIChat',
    component: AIChat,
    meta: { requiresAuth: true, title: '智能健康助手' },
  },
  {
    path: '/goals',
    name: 'Goals',
    component: Goals,
    meta: { requiresAuth: true, title: '目标管理' },
  },
  {
    path: '/reminders',
    name: 'Reminders',
    component: Reminders,
    meta: { requiresAuth: true, title: '提醒通知' },
  },
  {
    path: '/family',
    name: 'Family',
    component: Family,
    meta: { requiresAuth: true, title: '家庭组' },
  },
  {
    path: '/reports',
    name: 'Reports',
    component: Reports,
    meta: { requiresAuth: true, title: '健康周报与月报' },
  },
  {
    path: '/devices',
    name: 'Devices',
    component: Devices,
    meta: { requiresAuth: true, title: '设备管理' },
  },
  {
    path: '/api-guide',
    name: 'ApiGuide',
    component: ApiGuide,
    meta: { requiresAuth: true, title: '接口使用指南' },
  },
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: { title: '注册' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue'),
    meta: { title: '页面未找到' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token');

  if (to.meta.requiresAuth && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } });
  } else if ((to.path === '/login' || to.path === '/register') && token) {
    next('/');
  } else {
    next();
  }
});

router.afterEach((to) => {
  document.title = (to.meta.title as string) || '健康管理系统';
});

export default router;
