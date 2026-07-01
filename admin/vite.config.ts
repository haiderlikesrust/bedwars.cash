import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The admin app calls the backend same-origin under /api (proxied here in dev,
// by Caddy in prod) so the httpOnly session cookie works without CORS.
const BACKEND = process.env.ADMIN_BACKEND ?? 'http://localhost:8787';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5175,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true },
    },
  },
});
