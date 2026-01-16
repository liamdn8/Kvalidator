import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: '/kvalidator/web/',
  server: {
    port: 3000,
    proxy: {
      '/kvalidator/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})
