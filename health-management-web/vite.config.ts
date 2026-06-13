import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  build: {
    sourcemap: 'hidden',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return
          }
          if (id.includes('element-plus')) return 'element-plus'
          if (id.includes('echarts') || id.includes('zrender') || id.includes('vue-echarts')) return 'charts'
          if (id.includes('lucide-vue-next')) return 'icons'
          if (id.includes('marked')) return 'markdown'
          if (id.includes('vue') || id.includes('vue-router')) return 'vue-vendor'
          return 'vendor'
        },
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
