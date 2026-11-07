import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

// Se publica en https://oluengo.github.io/wavestr/app/ (GitHub Pages del repo).
export default defineConfig({
  base: './',
  plugins: [svelte()],
  build: { outDir: 'dist', assetsDir: 'assets', sourcemap: false, target: 'es2022' },
})
