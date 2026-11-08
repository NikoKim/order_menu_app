import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const proxyTarget = process.env.VITE_PROXY_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: 'moment_dist'
  },
  server: {
    port: 5174,
    host: true,
    proxy: {
      '/api': {
        target: proxyTarget,
        ws: true,
        xfwd: true,
        changeOrigin: true
      },
      '/images': {
        target: proxyTarget,
        changeOrigin: true
      },
      '/message-chat-images': {
        target: proxyTarget,
        changeOrigin: true
      },
      '/ws': {
        target: proxyTarget.replace(/^http/, 'ws'),
        ws: true,
        changeOrigin: true
      },
      '/share': {
        target: proxyTarget,
        changeOrigin: true,
        rewrite: path => path.replace(/^\/share\/moments\/(\d+)/, '/api/moments/$1/share-card')
      }
    }
  },
  resolve: {
    alias: {
      '@': '/src'
    }
  }
})
