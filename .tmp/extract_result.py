import json,csv

d=json.load(open('.tmp/cep_investigacao_result.json',encoding='utf-8-sig'))
rows=d['detalhado']
fields=['cpf','data_primeiro_cadastro','status_primeiro','cep_no_cadastro','cep_no_payload','qtd_chamadas_erp','qtd_com_cep','qtd_sem_cep','diagnostico']
with open('.tmp/cep_detalhado.csv','w',newline='',encoding='utf-8') as f:
    w=csv.DictWriter(f,fieldnames=fields)
    w.writeheader();w.writerows(rows)
with open('.tmp/cep_excecoes_nao_encontrados.txt','w',encoding='utf-8') as f:
    f.write('\n'.join(d['excecoes']['cpfs_nao_encontrados_em_cadastros']))
with open('.tmp/cep_excecoes_enviado_sem_log.txt','w',encoding='utf-8') as f:
    f.write('\n'.join(d['excecoes']['cpfs_status_enviado_sem_chamada_api_logs']))

print('samples_enviado_sem_cep')
for r in [x for x in rows if x['diagnostico']=='ENVIADO_SEM_CEP'][:5]:
    print(r)
print('samples_nao_enviado')
for r in [x for x in rows if x['diagnostico']=='NAO_ENVIADO_AINDA'][:5]:
    print(r)
print('sample_enviado_com_cep')
for r in [x for x in rows if x['diagnostico']=='ENVIADO_COM_CEP'][:5]:
    print(r)
