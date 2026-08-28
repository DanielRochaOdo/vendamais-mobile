from pathlib import Path
import runpy

script = Path('.github/scripts/fix_adhesion_commit_ux.py')
text = script.read_text()
old = '''    2,\n    "ERP reused post-commit HTTP status",\n)'''
new = '''    1,\n    "ERP reused post-commit HTTP status",\n)'''
if old not in text:
    raise SystemExit('could not normalize reused status expected count')
script.write_text(text.replace(old, new, 1))

runpy.run_path(str(script), run_name='__main__')

erp = Path('supabase/functions/erp-novo-usuario2/index.ts')
text = erp.read_text()
old_status = '''          statusCode = attachment.required && !attachment.queued ? 503 : 200;\n          errorMessage = statusCode === 200 ? undefined : responseBody.error;\n'''
new_status = '''          statusCode = 200;\n          errorMessage = undefined;\n'''
count = text.count(old_status)
if count != 1:
    raise SystemExit(f'idempotency reused status: expected 1 match, got {count}')
erp.write_text(text.replace(old_status, new_status, 1))
print('updated ERP idempotency reused post-commit HTTP status')
