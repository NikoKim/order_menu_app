import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { port: 5175, strictPort: true, proxy: { '/api': {target: process.env.NAVY_API_TARGET || 'http://127.0.0.1:8082',ws:true,xfwd:true} } }
})
