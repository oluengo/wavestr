// Convierte los textos de Android (app/src/main/res/values*/strings.xml) a src/strings.json para la web.
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'app', 'src', 'main', 'res')
const langs = { en: 'values', es: 'values-es', ca: 'values-ca' }

function unescape(s) {
  return s.trim()
    .replace(/^"(.*)"$/s, '$1')
    .replace(/\\n/g, '\n').replace(/\\'/g, "'").replace(/\\"/g, '"').replace(/\\@/g, '@').replace(/\\\?/g, '?')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&')
}

const out = {}
for (const [lang, dir] of Object.entries(langs)) {
  const file = join(root, dir, 'strings.xml')
  if (!existsSync(file)) continue
  const xml = readFileSync(file, 'utf8').replace(/<!--[\s\S]*?-->/g, '')
  const m = {}
  for (const [, name, val] of xml.matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) m[name] = unescape(val)
  for (const [, name, body] of xml.matchAll(/<plurals\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)) {
    const p = {}
    for (const [, q, v] of body.matchAll(/<item\s+quantity="([^"]+)"\s*>([\s\S]*?)<\/item>/g)) p[q] = unescape(v)
    m[name] = p
  }
  out[lang] = m
}
writeFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'strings.json'), JSON.stringify(out))
console.log('strings:', Object.entries(out).map(([k, v]) => `${k}=${Object.keys(v).length}`).join(' '))
