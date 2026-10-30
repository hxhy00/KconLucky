import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    // 大屏逻辑跑在 SFC 内，需要 DOM 环境挂载组件
    environment: 'jsdom',
    include: ['src/__tests__/**/*.spec.js'],
    clearMocks: true
  }
})
