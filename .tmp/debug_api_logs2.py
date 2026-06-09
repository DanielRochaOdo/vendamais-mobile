import urllib.parse, urllib.request, json
url='https://plonbokgcxwsdqfyjkwl.supabase.co/rest/v1/api_logs'
key='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBsb25ib2tnY3h3c2RxZnlqa3dsIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NjA3Njg5OSwiZXhwIjoyMDgxNjUyODk5fQ.XTi7ZOVkj8Y7AqcA1plvpPP0W3NYDfX47_GWwdhTrUE'
qs=urllib.parse.urlencode({'select':'*','limit':'1'})
req=urllib.request.Request(url+'?'+qs, headers={'apikey':key,'Authorization':'Bearer '+key,'Accept':'application/json'})
with urllib.request.urlopen(req,timeout=60) as r:
  rows=json.loads(r.read().decode('utf-8'))
  print(rows[0].keys())
  print(rows[0])
