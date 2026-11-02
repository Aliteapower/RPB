import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import {execFileSync} from 'node:child_process'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'

export default defineConfig(({command}) => {
 const version = command === 'build' ? (process.env.RPB_FRONTEND_VERSION || execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim()) : 'development'
 if(command === 'build' && !/^[a-f0-9]{40}$/.test(version)) throw new Error('RPB_FRONTEND_VERSION must be a 40-character Git commit SHA')
 return {
  define: {'import.meta.env.VITE_FRONTEND_VERSION': JSON.stringify(version)},
  plugins: [vue(), {name:'rpb-frontend-version',generateBundle(){this.emitFile({type:'asset',fileName:'frontend-version.json',source:JSON.stringify({version})+'\n'})}}],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalized = id.replace(/\\/g, '/')
          // Barcode decoding is optional; keep it out of the wallet's initial vendor download.
          if (normalized.includes('/node_modules/@zxing/')) return 'product-barcode-decoder'
          if (normalized.includes('/node_modules/')) {
            return 'vendor'
          }
          if (normalized.includes('/src/i18n/')) {
            return 'i18n'
          }
          if (normalized.includes('/src/api/')) {
            return 'api'
          }
        }
      }
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5176,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true
      }
    }
  }
 }
})
