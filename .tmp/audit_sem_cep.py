import json, re, urllib.parse, urllib.request
from collections import defaultdict, Counter

SUPABASE_URL = "https://plonbokgcxwsdqfyjkwl.supabase.co"
SERVICE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBsb25ib2tnY3h3c2RxZnlqa3dsIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NjA3Njg5OSwiZXhwIjoyMDgxNjUyODk5fQ.XTi7ZOVkj8Y7AqcA1plvpPP0W3NYDfX47_GWwdhTrUE"
headers = {"apikey": SERVICE_KEY, "Authorization": f"Bearer {SERVICE_KEY}", "Accept": "application/json"}

def http_get_json(url, query):
    qs = urllib.parse.urlencode(query, safe='(),>')
    req = urllib.request.Request(url + '?' + qs, headers=headers, method='GET')
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode('utf-8'))

def fetch_table(table, params):
    out=[]; offset=0; limit=1000
    while True:
        p=params.copy(); p['limit']=str(limit); p['offset']=str(offset)
        rows=http_get_json(f"{SUPABASE_URL}/rest/v1/{table}", p)
        out.extend(rows)
        if len(rows)<limit: break
        offset+=limit
    return out

def normcpf(v):
    return re.sub(r'\D','',str(v or '')).zfill(11)[-11:]

def filled(v):
    if v is None: return False
    s=str(v).strip().lower()
    return s not in ('','null','none')

base=json.load(open('.tmp/cep_investigacao_result.json',encoding='utf-8-sig'))
rows=base['detalhado']
cpfs=[r['cpf'] for r in rows]

cadastros=[]
for i in range(0,len(cpfs),150):
    b=cpfs[i:i+150]
    cadastros.extend(fetch_table('cadastros',{
      'select':'id,cpf,created_at,updated_at,status,tipo_cadastro,endereco,payload_erp,origem_link_id,fluxo_publico,created_by,vendedor_codigo,vendedor_id,adesionista_id',
      'cpf':f"in.({','.join(b)})",
      'order':'created_at.asc'
    }))

# logs targeted endpoint
logs=fetch_table('api_logs',{
  'select':'id,created_at,endpoint,method,user_id,user_email,request_body,response_body,status_code,success,error_message',
  'endpoint':'eq.erp-novo-usuario2',
  'order':'created_at.asc'
})

cad_by_id={str(c['id']):c for c in cadastros}
cad_by_cpf=defaultdict(list)
for c in cadastros: cad_by_cpf[normcpf(c.get('cpf'))].append(c)

logs_by_cad=defaultdict(list)
logs_without_cad=[]
for l in logs:
    rb=l.get('request_body') if isinstance(l.get('request_body'),dict) else {}
    cid=rb.get('cadastro_id')
    if cid is None:
        logs_without_cad.append(l)
        continue
    cid=str(cid)
    if cid in cad_by_id:
        logs_by_cad[cid].append(l)

# only cpfs with ENVIADO_SEM_CEP
target=[r for r in rows if r['diagnostico']=='ENVIADO_SEM_CEP']

summary={
 'total_target_cpfs':len(target),
 'status_now':Counter(),
 'tipo_cadastro':Counter(),
 'fluxo_publico':Counter(),
 'origem_link':Counter(),
 'cadastros_with_any_log':0,
 'cadastros_without_log':0,
 'log_status_code':Counter(),
 'log_success':Counter(),
 'log_user_email_top':Counter(),
 'cep_no_request_pattern':Counter(),
}

audit_rows=[]
for r in target:
    cpf=r['cpf']
    cads=sorted(cad_by_cpf.get(cpf,[]), key=lambda x:x.get('created_at') or '')
    # first cadastro + latest cadastro
    first=cads[0] if cads else None
    latest=cads[-1] if cads else None
    all_logs=[]
    for c in cads:
        cid=str(c['id'])
        ll=logs_by_cad.get(cid,[])
        if ll: summary['cadastros_with_any_log']+=1
        else: summary['cadastros_without_log']+=1
        all_logs.extend(ll)

        summary['status_now'][str(c.get('status'))]+=1
        summary['tipo_cadastro'][str(c.get('tipo_cadastro'))]+=1
        summary['fluxo_publico'][str(c.get('fluxo_publico'))]+=1
        summary['origem_link']['com_origem_link' if c.get('origem_link_id') else 'sem_origem_link']+=1

    # dedupe logs
    uniq={str(l['id']):l for l in all_logs}
    all_logs=list(uniq.values())
    all_logs.sort(key=lambda x:x.get('created_at') or '')

    req_ceps=[]
    for l in all_logs:
        summary['log_status_code'][str(l.get('status_code'))]+=1
        summary['log_success'][str(l.get('success'))]+=1
        if l.get('user_email'): summary['log_user_email_top'][l['user_email']]+=1
        rb=l.get('request_body') if isinstance(l.get('request_body'),dict) else {}
        cep=rb.get('dados',{}).get('responsavelFinanceiro',{}).get('endereco',{}).get('cep')
        if cep is None: summary['cep_no_request_pattern']['missing_key_or_null']+=1
        elif str(cep).strip()=='' : summary['cep_no_request_pattern']['empty_string']+=1
        else: summary['cep_no_request_pattern']['filled']+=1
        req_ceps.append(cep)

    audit_rows.append({
      'cpf':cpf,
      'qtd_cadastros':len(cads),
      'first_created_at':first.get('created_at') if first else None,
      'first_status':first.get('status') if first else None,
      'first_fluxo_publico':first.get('fluxo_publico') if first else None,
      'first_origem_link_id':first.get('origem_link_id') if first else None,
      'first_cep_cadastro': (first.get('endereco') or {}).get('cep') if isinstance(first.get('endereco'),dict) else None,
      'latest_status':latest.get('status') if latest else None,
      'qtd_logs_erp_novo_usuario2':len(all_logs),
      'log_time_first':all_logs[0]['created_at'] if all_logs else None,
      'log_time_last':all_logs[-1]['created_at'] if all_logs else None,
      'req_ceps_unique':sorted({str(x) for x in req_ceps}),
      'user_emails':sorted({l.get('user_email') for l in all_logs if l.get('user_email')})
    })

# sort top emails
summary['log_user_email_top']=summary['log_user_email_top'].most_common(10)
summary['status_now']=summary['status_now'].most_common()
summary['tipo_cadastro']=summary['tipo_cadastro'].most_common()
summary['fluxo_publico']=summary['fluxo_publico'].most_common()
summary['origem_link']=summary['origem_link'].most_common()
summary['log_status_code']=summary['log_status_code'].most_common()
summary['log_success']=summary['log_success'].most_common()
summary['cep_no_request_pattern']=summary['cep_no_request_pattern'].most_common()

# quick timing distribution vs May/2026
month_counter=Counter((a['log_time_first'] or '')[:7] for a in audit_rows)

out={
 'summary':summary,
 'month_distribution_first_log':month_counter,
 'audit_rows_sample':audit_rows[:20],
 'audit_rows_all':audit_rows
}

open('.tmp/cep_sem_cep_auditoria.json','w',encoding='utf-8').write(json.dumps(out,ensure_ascii=False,indent=2))
print(json.dumps(out['summary'],ensure_ascii=False))
print('month_distribution_first_log',dict(month_counter))
print('total_audit_rows',len(audit_rows))
