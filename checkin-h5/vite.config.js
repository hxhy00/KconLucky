import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  // 部署基路径由 .env.production 的 VITE_BASE 决定（生产：/kconluck2026/sign/）
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    base: env.VITE_BASE || '/sign/',
    build: {
      outDir: 'dist',
      chunkSizeWarningLimit: 1000
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/dev-api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        }
      }
    }
  }
})
