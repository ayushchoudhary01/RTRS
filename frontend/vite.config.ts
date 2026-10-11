import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Each Trade Flow service owns a distinct /api/v1 path prefix, so the dev server can
// fan requests out by prefix with no rewriting and no CORS config on any service
// the browser only ever talks to this origin
const SERVICE_PORTS: Record<string, number> = {
  '/api/v1/trades': 8086,
  '/api/v1/approvals': 8087,
  '/api/v1/risk': 8083,
  '/api/v1/aml': 8084,
  '/api/v1/ledger': 8085,
  '/api/v1/admin/ledger': 8085,
  '/api/v1/settlement': 8089,
  '/api/v1/reconciliation': 8088,
}

const proxy = Object.fromEntries(
  Object.entries(SERVICE_PORTS).map(([path, port]) => [
    path,
    { target: `http://localhost:${port}`, changeOrigin: true },
  ]),
)

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy,
  },
})
