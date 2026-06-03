import json,re,urllib.parse,urllib.request
from collections import Counter
SUPABASE_URL='https://plonbokgcxwsdqfyjkwl.supabase.co'
KEY='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBsb25ib2tnY3h3c2RxZnlqa3dsIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NjA3Njg5OSwiZXhwIjoyMDgxNjUyODk5fQ.XTi7ZOVkj8Y7AqcA1plvpPP0W3NYDfX47_GWwdhTrUE'
H={'apikey':KEY,'Authorization':'Bearer '+KEY,'Accept':'application/json'}
def get(table,params):
  out=[];off=0
  while True:
    p=params.copy();p['limit']='1000';p['offset']=str(off)
    q=urllib.parse.urlencode(p,safe='(),>')
    req=urllib.request.Request(f'{SUPABASE_URL}/rest/v1/{table}?'+q,headers=H)
    rows=json.loads(urllib.request.urlopen(req,timeout=120).read().decode())
    out.extend(rows)
    if len(rows)<1000:break
    off+=1000
  return out

def norm(v):return re.sub(r'\D','',str(v or '')).zfill(11)[-11:]
base=json.load(open('.tmp/cep_investigacao_result.json',encoding='utf-8-sig'))
cpfs={r['cpf'] for r in base['detalhado'] if r['diagnostico']=='ENVIADO_SEM_CEP'}
cads=get('cadastros',{'select':'id,cpf','cpf':f"in.({','.join(sorted(cpfs))})"})
ids={str(c['id']) for c in cads}
logs=get('api_logs',{'select':'id,created_at,user_email,request_body,response_body,status_code,success,error_message,endpoint','endpoint':'eq.erp-novo-usuario2'})
sel=[]
for l in logs:
 rb=l.get('request_body') if isinstance(l.get('request_body'),dict) else {}
 cid=str(rb.get('cadastro_id')) if rb.get('cadastro_id') is not None else None
 if cid in ids:
  cep=rb.get('dados',{}).get('responsavelFinanceiro',{}).get('endereco',{}).get('cep')
  if isinstance(cep,str) and cep=='':
   sel.append(l)

errs=Counter()
resp_msg=Counter()
for l in sel:
 if l.get('error_message'): errs[str(l['error_message'])]+=1
 rb=l.get('response_body') if isinstance(l.get('response_body'),dict) else {}
 # collect message traces
 for m in [rb.get('error'), rb.get('details',{}).get('mensagem') if isinstance(rb.get('details'),dict) else None,
           rb.get('data',{}).get('mensagem') if isinstance(rb.get('data'),dict) else None,
           rb.get('details',{}).get('message') if isinstance(rb.get('details'),dict) else None]:
  if isinstance(m,str) and m.strip(): resp_msg[m.strip()]+=1

print('total_logs_empty_cep',len(sel))
print('success_counter',Counter(str(x.get('success')) for x in sel))
print('status_code_counter',Counter(str(x.get('status_code')) for x in sel))
print('top_error_message',errs.most_common(10))
print('top_response_messages',resp_msg.most_common(10))
print('sample_failures')
for l in [x for x in sel if not x.get('success')][:5]:
  print({'id':l['id'],'created_at':l['created_at'],'user_email':l.get('user_email'),'status_code':l.get('status_code'),'error_message':l.get('error_message'),'response_body':l.get('response_body')})
