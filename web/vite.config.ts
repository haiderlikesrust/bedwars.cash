import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Local HLS preview: proxy the MediaMTX stream through the dev origin so it is
// same-origin with the app. This avoids cross-origin CORS + MediaMTX's HLS
// session-cookie check, which otherwise leave the player stuck buffering.
// Set STREAM_URL=http://localhost:5173/bedwars/index.m3u8 in backend/.env.
const MEDIAMTX_URL = process.env.MEDIAMTX_URL ?? 'http://localhost:8888';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/bedwars': {
        target: MEDIAMTX_URL,
        changeOrigin: true,
      },
    },
  },
});
