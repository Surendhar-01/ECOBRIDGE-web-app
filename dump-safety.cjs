const fs = require('fs')
const t = fs.readFileSync('supabase/migrations/0001_single_source_of_truth.sql', 'utf8')
const i = t.indexOf('INSERT INTO public.safety_guidelines')
process.stdout.write(t.slice(i))
