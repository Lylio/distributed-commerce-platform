import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig(({ mode }) => {
  // Custom targets remain server-side; only VITE_* variables can be exposed to browser code.
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env };
  const proxy = {
    '/api/orders': { target: env.ORDER_API_TARGET || 'http://localhost:8081', changeOrigin: true, rewrite: (path: string) => path.replace(/^\/api/, '') },
    '/api/products': { target: env.ORDER_API_TARGET || 'http://localhost:8081', changeOrigin: true, rewrite: (path: string) => path.replace(/^\/api/, '') },
    '/api/inventory': { target: env.INVENTORY_API_TARGET || 'http://localhost:8082', changeOrigin: true, rewrite: (path: string) => path.replace(/^\/api/, '') },
  };
  return { plugins: [react()], server: { port: Number(env.FRONTEND_PORT || 5173), strictPort: true, proxy }, preview: { proxy }, test: { environment: 'jsdom', execArgv: ['--no-experimental-webstorage'], environmentOptions: { jsdom: { url: 'http://localhost/' } }, setupFiles: ['./src/test-setup.ts'], include: ['src/**/*.test.{ts,tsx}'], restoreMocks: true } };
});
