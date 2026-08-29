import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:10011'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': apiProxyTarget,
      },
    },
  }
})
