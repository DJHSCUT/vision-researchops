import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { localRagProxyGuard, ragAskPath } from './dev/ragProxy.js'

export default defineConfig({
  plugins: [vue(), localRagProxyGuard()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      [ragAskPath]: { target: 'http://127.0.0.1:8080', changeOrigin: true, xfwd: false },
    },
  },
  // Vite preview otherwise inherits server.proxy. No dev endpoint proxy in preview/build.
  preview: { host: '127.0.0.1', proxy: {} },
  test: { environment: 'jsdom', include: ['tests/**/*.test.js'], clearMocks: true },
})
