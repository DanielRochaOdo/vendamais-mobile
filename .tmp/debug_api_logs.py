import urllib.parse, urllib.request, urllib.error
url='https://plonbokgcxwsdqfyjkwl.supabase.co/rest/v1/api_logs'
key='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBsb25ib2tnY3h3c2RxZnlqa3dsIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NjA3Njg5OSwiZXhwIjoyMDgxNjUyODk5fQ.XTi7ZOVkj8Y7AqcA1plvpPP0W3NYDfX47_GWwdhTrUE'
params={'select':'id,created_at,endpoint,request_body,response_status','endpoint':'eq.erp-novo-usuario2','order':'created_at.asc','limit':'1'}
qs=urllib.parse.urlencode(params, safe='(),>')
req=urllib.request.Request(url+'?'+qs, headers={'apikey':key,'Authorization':'Bearer '+key,'Accept':'application/json'})
try:
  with urllib.request.urlopen(req,timeout=60) as r:
    print(r.read().decode('utf-8'))
except urllib.error.HTTPError as e:
  print('status', e.code)
  print(e.read().decode('utf-8'))
