import { isBlockedAssociation, isHiddenPlan, isInvalidCompanySituation } from "./cadastro-config-rules.ts";
import type { CadastroRules } from "./cadastro-config-rules.ts";

const config: CadastroRules = {
  situacoes_que_barram: [1, 4, 6],
  planos_validos: [4, 11, 3, 26],
  planos_ocultos: [5, 35, 106, 139].map(String),
  codigos_empresa_invalidos: ["2", "4", "5", "6", "18"],
};
const assert = (condition: boolean, label: string) => {
  if (!condition) throw new Error(label);
};

Deno.test("situação ativa + planos 3/26: não barra; planos 6/139: barra", () => {
  assert(!isBlockedAssociation(1, 3, config), "plano 3 não deve barrar");
  assert(isBlockedAssociation(1, 6, config), "plano 6 deve barrar");
  assert(isBlockedAssociation(1, 139, config), "plano 139 deve barrar");
  assert(!isBlockedAssociation(1, 26, config), "plano 26 não deve barrar");
});
Deno.test("regras são dinâmicas, sem hard-code de 106 ou outros planos", () => {
  assert(isHiddenPlan(106, config) && isHiddenPlan(139, config), "planos ocultos");
  assert(!isHiddenPlan(3, config), "plano disponível");
  const changed = { ...config, planos_ocultos: ["777"] };
  assert(!isHiddenPlan(106, changed) && isHiddenPlan("777", changed), "adição/remoção");
});
Deno.test("bloqueio de empresa verifica código da situação, não ID de empresa", () => {
  assert(isInvalidCompanySituation(18, config), "situação inválida 18");
  assert(!isInvalidCompanySituation(239, config), "ID 239 não é uma situação inválida");
});
Deno.test("situação fora da lista não barra, mesmo se plano não for válido", () => {
  assert(!isBlockedAssociation(7, 139, config), "situação não bloqueada");
});
