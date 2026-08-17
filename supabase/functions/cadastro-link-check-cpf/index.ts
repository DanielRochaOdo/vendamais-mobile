import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

const jsonResponse = (body: Record<string, unknown>, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });

const normalizeDigits = (value?: string | null) => (value || "").replace(/\D/g, "");

const hashToken = async (token: string) => {
  const data = new TextEncoder().encode(token);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
};

const checkLocalBlockedCpf = async (
  supabase: any,
  cpf: string,
) => {
  const { data, error } = await supabase.rpc("check_public_link_blocked_cpf", {
    p_cpf: cpf,
  });

  if (error) throw error;
  return data as { blocked?: boolean; reason?: string | null; code?: string | null } | null;
};

type PublicContact = {
  tipo: "celular" | "fixo" | "email" | "whatsapp";
  valor: string;
  principal?: boolean;
};

type PublicAddress = {
  cep: string;
  tipoLogradouro?: string;
  logradouro: string;
  numero: string;
  complemento?: string;
  bairro: string;
  cidade: string;
  uf: string;
};

type PublicPrefill = {
  nome?: string | null;
  dataNascimento?: string | null;
  sexoCodigo?: number | null;
  contatos: PublicContact[];
  endereco?: PublicAddress | null;
  nomeMae?: string | null;
};

const emptyPrefill = (): PublicPrefill => ({
  nome: null,
  dataNascimento: null,
  sexoCodigo: null,
  contatos: [],
  endereco: null,
  nomeMae: null,
});

const normalizePreviousContacts = (value: unknown): PublicContact[] => {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item, index) => {
    if (!item || typeof item !== "object") return [];
    const raw = item as Record<string, unknown>;
    const tipo = typeof raw.tipo === "string" ? raw.tipo.toLowerCase() : "";
    const valor = typeof raw.valor === "string" ? raw.valor.trim() : "";
    if (!valor || !["celular", "fixo", "email", "whatsapp"].includes(tipo)) return [];
    return [{
      tipo: tipo as PublicContact["tipo"],
      valor,
      principal: Boolean(raw.principal) || index === 0,
    }];
  });
};

const normalizePreviousAddress = (value: unknown): PublicAddress | null => {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const str = (key: string) => typeof raw[key] === "string" ? String(raw[key]).trim() : "";
  const cep = normalizeDigits(str("cep"));
  const logradouro = str("logradouro");
  const bairro = str("bairro");
  const cidade = str("cidade");
  const uf = str("uf");
  if (!cep && !logradouro && !bairro && !cidade && !uf) return null;
  return {
    cep,
    tipoLogradouro: str("tipoLogradouro") || undefined,
    logradouro,
    numero: str("numero"),
    complemento: str("complemento") || undefined,
    bairro,
    cidade,
    uf,
  };
};

const mapLemmitPrefill = (payload: any): PublicPrefill => {
  const pessoa = payload?.pessoa;
  if (!pessoa || typeof pessoa !== "object") return emptyPrefill();

  const contacts: PublicContact[] = [];
  const celulares = Array.isArray(pessoa.celulares)
    ? [...pessoa.celulares]
        .filter((item: any) => item?.plus === true)
        .sort((a: any, b: any) => Number(a?.ranking ?? 999) - Number(b?.ranking ?? 999))
    : [];
  celulares.forEach((item: any, index: number) => {
    if (!item?.numero) return;
    contacts.push({
      tipo: item.whatsapp ? "whatsapp" : "celular",
      valor: `${item.ddd ?? ""}${item.numero}`.replace(/\D/g, ""),
      principal: index === 0,
    });
  });

  const fixos = Array.isArray(pessoa.fixos)
    ? [...pessoa.fixos].sort((a: any, b: any) => Number(a?.ranking ?? 999) - Number(b?.ranking ?? 999))
    : [];
  fixos.forEach((item: any) => {
    if (!item?.numero) return;
    contacts.push({
      tipo: "fixo",
      valor: `${item.ddd ?? ""}${item.numero}`.replace(/\D/g, ""),
      principal: false,
    });
  });

  const emails = Array.isArray(pessoa.emails)
    ? [...pessoa.emails].sort((a: any, b: any) => Number(a?.ranking ?? 999) - Number(b?.ranking ?? 999))
    : [];
  emails.forEach((item: any) => {
    if (!item?.email) return;
    contacts.push({ tipo: "email", valor: String(item.email).trim(), principal: false });
  });

  let address: PublicAddress | null = null;
  if (Array.isArray(pessoa.enderecos) && pessoa.enderecos.length > 0) {
    const sorted = [...pessoa.enderecos].sort(
      (a: any, b: any) => Number(a?.ranking ?? 999) - Number(b?.ranking ?? 999),
    );
    const item = sorted.find((entry: any) => Number(entry?.ranking) === 1) || sorted[0];
    address = {
      cep: normalizeDigits(item?.cep),
      tipoLogradouro: item?.tipo_logradouro || undefined,
      logradouro: item?.logradouro || "",
      numero: item?.numero || "",
      complemento: item?.complemento || undefined,
      bairro: item?.bairro || "",
      cidade: item?.cidade || "",
      uf: item?.uf || "",
    };
  }

  const sexo = String(pessoa.sexo || "").trim().toUpperCase();
  const sexoCodigo = sexo === "M" || sexo === "MASCULINO"
    ? 1
    : sexo === "F" || sexo === "FEMININO"
      ? 0
      : null;

  let dataNascimento: string | null = null;
  if (pessoa.data_nascimento) {
    const raw = String(pessoa.data_nascimento).trim();
    const match = raw.match(/^(\d{4}-\d{2}-\d{2})/);
    if (match) dataNascimento = match[1];
    else {
      const parsed = new Date(raw);
      if (!Number.isNaN(parsed.getTime())) dataNascimento = parsed.toISOString().slice(0, 10);
    }
  }

  return {
    nome: pessoa.nome || null,
    dataNascimento,
    sexoCodigo,
    contatos: contacts,
    endereco: address,
    nomeMae: pessoa.nome_mae || null,
  };
};

const mergePrefill = (primary: PublicPrefill, fallback: PublicPrefill): PublicPrefill => ({
  nome: primary.nome || fallback.nome || null,
  dataNascimento: primary.dataNascimento || fallback.dataNascimento || null,
  sexoCodigo: primary.sexoCodigo === 0 || primary.sexoCodigo === 1
    ? primary.sexoCodigo
    : fallback.sexoCodigo ?? null,
  contatos: primary.contatos.length > 0 ? primary.contatos : fallback.contatos,
  endereco: primary.endereco?.cep || primary.endereco?.logradouro
    ? primary.endereco
    : fallback.endereco || null,
  nomeMae: primary.nomeMae || fallback.nomeMae || null,
});

const invokePublicFunction = async (
  supabaseUrl: string,
  anonKey: string,
  functionName: string,
  body: Record<string, unknown>,
) => {
  const response = await fetch(`${supabaseUrl}/functions/v1/${functionName}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${anonKey}`,
      apikey: anonKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let data: any = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { error: text };
  }
  return { response, data };
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Metodo nao permitido" }, 405);
  }

  try {
    const { token, cpf } = await req.json() as { token?: string; cpf?: string };
    if (!token || typeof token !== "string") return jsonResponse({ error: "Token obrigatorio" }, 400);

    const normalizedCpf = normalizeDigits(cpf);
    if (normalizedCpf.length !== 11) return jsonResponse({ error: "CPF invalido" }, 400);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const supabase = createClient(supabaseUrl, supabaseServiceKey);
    const tokenHash = await hashToken(token.trim());

    const { data: link, error: linkError } = await supabase
      .from("cadastro_links")
      .select("id, is_active")
      .eq("token_hash", tokenHash)
      .maybeSingle();
    if (linkError) return jsonResponse({ error: "Erro ao validar link" }, 500);
    if (!link) return jsonResponse({ error: "Link nao encontrado ou invalido" }, 404);
    if (!link.is_active) return jsonResponse({ error: "Link inativo" }, 410);

    const { data: existingCadastro, error: existingCadastroError } = await supabase
      .from("cadastros")
      .select("id")
      .eq("origem_link_id", link.id)
      .eq("cpf", normalizedCpf)
      .eq("status", "enviado")
      .limit(1)
      .maybeSingle();
    if (existingCadastroError) return jsonResponse({ error: "Erro ao verificar uso anterior do CPF" }, 500);
    if (existingCadastro) {
      return jsonResponse({
        error: "Este CPF ja concluiu uma adesao por este link e nao pode reutiliza-lo.",
        code: "CPF_ALREADY_USED_ON_LINK",
      }, 409);
    }

    const localBlockedCpf = await checkLocalBlockedCpf(supabase, normalizedCpf);
    if (localBlockedCpf?.blocked) {
      return jsonResponse({
        error: localBlockedCpf.reason || "Este CPF nao pode utilizar este link.",
        code: localBlockedCpf.code || "CPF_BLOCKED_LOCALLY",
      }, 409);
    }

    if (!supabaseAnonKey) return jsonResponse({ error: "Configuracao publica do Supabase indisponivel" }, 500);

    const erpCheck = await invokePublicFunction(
      supabaseUrl,
      supabaseAnonKey,
      "erp-check-associado",
      { cpf: normalizedCpf },
    );
    if (!erpCheck.response.ok) {
      return jsonResponse({ error: erpCheck.data?.error || "Erro ao validar CPF no ERP" }, 502);
    }
    if (erpCheck.data?.exists && erpCheck.data?.shouldBlock) {
      return jsonResponse({
        error: erpCheck.data?.blockReason || "Cliente ja cadastrado no sistema",
        code: "CPF_BLOCKED_BY_ERP",
      }, 409);
    }

    const { data: config } = await supabase
      .from("cadastro_config")
      .select("ativar_lemmit")
      .eq("id", 1)
      .maybeSingle();

    const { data: previousCadastro } = await supabase
      .from("cadastros")
      .select("nome,data_nascimento,sexo_codigo,nome_mae,contatos,endereco,erp_dados_associado")
      .eq("cpf", normalizedCpf)
      .not("erp_dados_associado", "is", null)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    const previousPrefill: PublicPrefill = previousCadastro
      ? {
          nome: previousCadastro.nome || null,
          dataNascimento: previousCadastro.data_nascimento || null,
          sexoCodigo: previousCadastro.sexo_codigo === 0 || previousCadastro.sexo_codigo === 1
            ? previousCadastro.sexo_codigo
            : null,
          contatos: normalizePreviousContacts(previousCadastro.contatos),
          endereco: normalizePreviousAddress(previousCadastro.endereco),
          nomeMae: previousCadastro.nome_mae || null,
        }
      : emptyPrefill();

    let lemmitPrefill = emptyPrefill();
    let lemmitFailed = false;
    if (config?.ativar_lemmit ?? true) {
      try {
        const lemmit = await invokePublicFunction(
          supabaseUrl,
          supabaseAnonKey,
          "lemit-consulta-pessoa",
          { cpf: normalizedCpf },
        );
        if (lemmit.response.ok && lemmit.data?.pessoa) lemmitPrefill = mapLemmitPrefill(lemmit.data);
        else lemmitFailed = true;
      } catch (error) {
        console.warn("[cadastro-link-check-cpf] Lemmit unavailable:", error);
        lemmitFailed = true;
      }
    }

    const prefill = mergePrefill(lemmitPrefill, previousPrefill);
    const hasLemmitData = Boolean(
      lemmitPrefill.nome || lemmitPrefill.dataNascimento || lemmitPrefill.contatos.length > 0 || lemmitPrefill.endereco,
    );
    const hasPreviousData = Boolean(
      previousPrefill.nome || previousPrefill.dataNascimento || previousPrefill.contatos.length > 0 || previousPrefill.endereco,
    );

    const message = hasLemmitData
      ? "Dados localizados e preenchidos automaticamente."
      : hasPreviousData
        ? "Dados anteriores encontrados e reaproveitados para agilizar o cadastro."
        : lemmitFailed
          ? "A consulta automatica de dados esta temporariamente indisponivel. Continue o cadastro manualmente."
          : "CPF validado. Continue o cadastro.";

    return jsonResponse({ ok: true, prefill, message });
  } catch (error) {
    console.error("[cadastro-link-check-cpf] unexpected error:", error);
    const message = error instanceof Error ? error.message : "Erro inesperado";
    return jsonResponse({ error: message }, 500);
  }
});
