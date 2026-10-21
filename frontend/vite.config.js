import process from 'node:process'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Where the Spring Boot backend runs. If it runs on another port, start the frontend with
// PETCLINIC_BACKEND=http://localhost:8090 npm run dev
const backend = process.env.PETCLINIC_BACKEND ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/graphql': { target: backend, ws: true },
      '/api': backend,
    },
    // The GraphQL operations live with the backend, in ../src/main/resources
    fs: { allow: ['..'] },
  },
})
