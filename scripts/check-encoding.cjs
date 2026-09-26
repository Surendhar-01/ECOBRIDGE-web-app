const fs = require('fs')
const files = process.argv.slice(2)
let bad = 0
for (const f of files) {
  if (!fs.existsSync(f)) { console.log(`${f}  (does not exist)`); continue }
  const t = fs.readFileSync(f, 'utf8')
  t.split('\n').forEach((line, i) => {
    if (line.includes('\uFFFD')) {
      bad++
      console.log(`${f}:${i + 1}  ${line.trim().slice(0, 160)}`)
    }
  })
  if (t.charCodeAt(0) === 0xfeff) { console.log(`${f} has a BOM`); bad++ }
}
console.log(bad === 0 ? 'clean: no replacement characters, no BOM' : `${bad} problem line(s)`)
