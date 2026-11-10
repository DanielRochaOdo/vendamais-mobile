/**
 * Regras de cadastro configuráveis: a tabela cadastro_config é a única fonte
 * de verdade. Nenhum plano/situação é codificado no aplicativo.
 */
export type CadastroRules = {
  situacoes_que_barram: number[];
  planos_validos: number[];
  planos_ocultos: string[];
  codigos_empresa_invalidos: string[];
};

const normalizeCode = (value: unknown): string => {
  const number = Number(String(value ?? "").trim());
  return Number.isInteger(number) && number >= 0 ? String(number) : "";
};

export function isBlockedAssociation(
  situacao: unknown,
  plano: unknown,
  rules: CadastroRules,
): boolean {
  const situationCode = Number(situacao);
  const planCode = Number(plano);
  return rules.situacoes_que_barram.includes(situationCode) &&
    !rules.planos_validos.includes(planCode);
}

export function isHiddenPlan(code: unknown, rules: CadastroRules): boolean {
  const normalized = normalizeCode(code);
  return normalized !== "" &&
    rules.planos_ocultos.some((item) => normalizeCode(item) === normalized);
}

export function isInvalidCompanySituation(code: unknown, rules: CadastroRules): boolean {
  const normalized = normalizeCode(code);
  return normalized !== "" &&
    rules.codigos_empresa_invalidos.some((item) => normalizeCode(item) === normalized);
}

/** Erros ou configuração incompleta não autorizam cadastro por omissão. */
export async function loadCadastroRules(supabase: any): Promise<CadastroRules> {
  const { data, error } = await supabase.from("cadastro_config")
    .select("situacoes_que_barram,planos_validos,planos_ocultos,codigos_empresa_invalidos")
    .eq("id", 1).maybeSingle();
  if (error || !data ||
      !Array.isArray(data.situacoes_que_barram) ||
      !Array.isArray(data.planos_validos) ||
      !Array.isArray(data.planos_ocultos) ||
      !Array.isArray(data.codigos_empresa_invalidos)) {
    throw new Error("CADASTRO_CONFIG_UNAVAILABLE");
  }
  return {
    situacoes_que_barram: data.situacoes_que_barram.map(Number),
    planos_validos: data.planos_validos.map(Number),
    planos_ocultos: data.planos_ocultos.map(String),
    codigos_empresa_invalidos: data.codigos_empresa_invalidos.map(String),
  };
}

/** Confere a situação atual da empresa no ERP, não uma cópia antiga do link. */
export async function isCurrentCompanyInvalid(companyCode: number, rules: CadastroRules): Promise<boolean> {
  if (rules.codigos_empresa_invalidos.length === 0) return false;
  if (!Number.isInteger(companyCode) || companyCode <= 0) throw new Error("ERP_COMPANY_INVALID");
  const token = Deno.env.get("ERP_TOKEN");
  if (!token) throw new Error("ERP_TOKEN_NOT_CONFIGURED");
  let base = Deno.env.get("ERP_BASE_URL") || Deno.env.get("ERP_ENDPOINT") || "https://odontoart.s4e.com.br";
  if (!/^https?:\/\//i.test(base)) base = `https://${base}`;
  const url = `${base.replace(/\/+$/, "")}/api/empresa/BuscaEmpresas?token=${encodeURIComponent(token)}&empresaId=${encodeURIComponent(String(companyCode))}`;
  const response = await fetch(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error("ERP_COMPANY_UNAVAILABLE");
  const payload = await response.json();
  const empresa = Array.isArray(payload?.dados) ? payload.dados[0] : null;
  if (!empresa) throw new Error("ERP_COMPANY_NOT_FOUND");
  return isInvalidCompanySituation(empresa.CodigoSituacao ?? empresa.codigoSituacao, rules);
}
