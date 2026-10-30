import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    // 签到流程测试需要挂载 SFC，使用 DOM 环境
    environment: 'jsdom',
    include: ['src/__tests__/**/*.spec.js'],
    clearMocks: true
  }
})
