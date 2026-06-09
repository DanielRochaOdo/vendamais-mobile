import json,re,urllib.parse,urllib.request
from collections import Counter
URL='https://plonbokgcxwsdqfyjkwl.supabase.co'
KEY='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBsb25ib2tnY3h3c2RxZnlqa3dsIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NjA3Njg5OSwiZXhwIjoyMDgxNjUyODk5fQ.XTi7ZOVkj8Y7AqcA1plvpPP0W3NYDfX47_GWwdhTrUE'
H={'apikey':KEY,'Authorization':'Bearer '+KEY,'Accept':'application/json'}
def fetch(table,params):
 out=[];off=0
 while True:
  p=params.copy();p['limit']='1000';p['offset']=str(off)
  q=urllib.parse.urlencode(p,safe='(),>')
  req=urllib.request.Request(f'{URL}/rest/v1/{table}?'+q,headers=H)
  rows=json.loads(urllib.request.urlopen(req,timeout=120).read().decode())
  out.extend(rows)
  if len(rows)<1000:break
  off+=1000
 return out
base=json.load(open('.tmp/cep_investigacao_result.json',encoding='utf-8-sig'))
cpfs={r['cpf'] for r in base['detalhado'] if r['diagnostico']=='ENVIADO_SEM_CEP'}
cads=fetch('cadastros',{'select':'id,cpf','cpf':f"in.({','.join(sorted(cpfs))})"})
ids={str(c['id']) for c in cads}
logs=fetch('api_logs',{'select':'request_body,created_at,user_email','endpoint':'eq.erp-novo-usuario2'})
pat=Counter();samples=[]
for l in logs:
 rb=l.get('request_body') if isinstance(l.get('request_body'),dict) else {}
 cid=rb.get('cadastro_id')
 if cid is None or str(cid) not in ids: continue
 cep=rb.get('dados',{}).get('responsavelFinanceiro',{}).get('endereco',{}).get('cep')
 if cep!='': continue
 ik=rb.get('idempotency_key')
 iks=str(ik)
 if iks.startswith('cadastro:'): pat['web_cadastro_prefix']+=1
 elif iks.startswith('cadastro-envio:'): pat['android_cadastro_envio_prefix']+=1
 elif iks in ('None','null',''): pat['empty_or_none']+=1
 else: pat['other_'+iks[:30]]+=1
 if len(samples)<20:
  samples.append({'created_at':l.get('created_at'),'user_email':l.get('user_email'),'idempotency_key':ik})
print('patterns',pat)
print('samples')
for s in samples: print(s)
