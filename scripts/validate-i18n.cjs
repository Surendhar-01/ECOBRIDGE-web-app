const fs = require('fs')
const path = require('path')

const resRoot = 'app/src/main/res'
const ktRoot = 'app/src/main/java/com/example'

// ---- collect declared strings, per locale ----
const locales = ['values', 'values-hi', 'values-mr']
const declared = {}
for (const locale of locales) {
  const dir = path.join(resRoot, locale)
  const set = new Set()
  const dupes = []
  if (fs.existsSync(dir)) {
    for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.xml'))) {
      const t = fs.readFileSync(path.join(dir, f), 'utf8')
      for (const m of t.matchAll(/<string\s+name="([^"]+)"/g)) {
        if (set.has(m[1])) dupes.push(`${locale}/${f}: ${m[1]}`)
        set.add(m[1])
      }
    }
  }
  declared[locale] = set
}

const base = declared['values']

// ---- locale parity ----
const missingHi = [...base].filter((k) => !declared['values-hi'].has(k))
const missingMr = [...base].filter((k) => !declared['values-mr'].has(k))
const extraHi = [...declared['values-hi']].filter((k) => !base.has(k))
const extraMr = [...declared['values-mr']].filter((k) => !base.has(k))

// ---- placeholder parity ----
const readValue = (locale, key) => {
  for (const f of fs.readdirSync(path.join(resRoot, locale)).filter((n) => n.endsWith('.xml'))) {
    const t = fs.readFileSync(path.join(resRoot, locale, f), 'utf8')
    const m = new RegExp(`<string\\s+name="${key}"[^>]*>([\\s\\S]*?)</string>`).exec(t)
    if (m) return m[1]
  }
  return null
}
const placeholders = (v) => (v ? (v.match(/%(\d+\$)?[sd]/g) || []).sort() : [])
const badPlaceholders = []
for (const key of base) {
  const en = placeholders(readValue('values', key))
  for (const locale of ['values-hi', 'values-mr']) {
    if (!declared[locale].has(key)) continue
    const other = placeholders(readValue(locale, key))
    if (other.join() !== en.join()) badPlaceholders.push(`${locale}/${key}: ${JSON.stringify(other)} != ${JSON.stringify(en)}`)
  }
}

// ---- R.string references from Kotlin ----
const walk = (dir, out = []) => {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name)
    if (e.isDirectory()) walk(full, out)
    else if (e.name.endsWith('.kt')) out.push(full)
  }
  return out
}
const undefinedRefs = new Map()
let refCount = 0
for (const f of walk(ktRoot)) {
  const t = fs.readFileSync(f, 'utf8')
  for (const m of t.matchAll(/\bR\.string\.([A-Za-z0-9_]+)/g)) {
    refCount++
    if (!base.has(m[1])) {
      if (!undefinedRefs.has(m[1])) undefinedRefs.set(m[1], [])
      undefinedRefs.get(m[1]).push(path.relative(ktRoot, f))
    }
  }
}

// ---- leftover per-language branching ----
const leftovers = []
for (const f of walk(ktRoot)) {
  const t = fs.readFileSync(f, 'utf8')
  if (/LanguageManager|when \(\s*(?:current)?[lL]anguage\s*\)\s*\{\s*Language\.ENGLISH/.test(t)) {
    leftovers.push(path.relative(ktRoot, f))
  }
}

console.log('declared strings (values):', base.size)
console.log('R.string references in Kotlin:', refCount)
console.log('')
console.log('undefined R.string references:', undefinedRefs.size)
for (const [k, files] of undefinedRefs) console.log('  ', k, '->', [...new Set(files)].join(', '))
console.log('')
console.log('missing in values-hi:', missingHi.length, missingHi.join(', '))
console.log('missing in values-mr:', missingMr.length, missingMr.join(', '))
console.log('extra in values-hi:', extraHi.length, extraHi.join(', '))
console.log('extra in values-mr:', extraMr.length, extraMr.join(', '))
console.log('')
console.log('placeholder mismatches:', badPlaceholders.length)
for (const b of badPlaceholders) console.log('  ', b)
console.log('')
console.log('duplicate string names:', dupesAll().length)
for (const d of dupesAll()) console.log('  ', d)
console.log('')
console.log('files still branching on Language:', leftovers.length, leftovers.join(', '))

function dupesAll() {
  const out = []
  for (const locale of locales) {
    const seen = new Map()
    const dir = path.join(resRoot, locale)
    for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.xml'))) {
      const t = fs.readFileSync(path.join(dir, f), 'utf8')
      for (const m of t.matchAll(/<string\s+name="([^"]+)"/g)) {
        if (seen.has(m[1])) out.push(`${locale}: ${m[1]} (${seen.get(m[1])} and ${f})`)
        else seen.set(m[1], f)
      }
    }
  }
  return out
}
