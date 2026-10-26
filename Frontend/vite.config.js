import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // `npm run build` puts the site inside Spring Boot, which then serves it at http://localhost:8081.
  build: {
    outDir: '../Backend/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    // Forward API calls to Spring Boot so the browser sees one origin in development.
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
})
