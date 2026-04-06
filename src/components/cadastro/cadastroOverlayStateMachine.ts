export type CadastroEntryPoint =
  | 'CADASTRO_MODAL'
  | 'INCLUSAO_DEPENDENTE_MODAL'
  | 'CONTINUAR_INCLUSAO_DEPENDENTE_MODAL';

export type CadastroErpError =
  | {
      kind: 'parceiro_invalido';
      message: string;
    }
  | {
      kind: 'dependente_ativo';
      details: string[];
    };

export type CadastroOverlayIntent =
  | {
      kind: 'entry_point';
      entryPoint: CadastroEntryPoint;
    }
  | {
      kind: 'empresa_nao_identificada';
      required: boolean;
    }
  | {
      kind: 'observacoes_empresa';
      empresaNome: string;
      observacoes: string;
    }
  | {
      kind: 'empresa_cancelada';
      empresaNome: string;
    }
  | {
      kind: 'lemmit_limit';
      limiteFormatado?: string;
      consumoFormatado?: string;
      saldoFormatado?: string;
      isUnlimited?: boolean;
    }
  | {
      kind: 'select_status';
    }
  | {
      kind: 'parceiro_invalido';
      message: string;
    }
  | {
      kind: 'dependente_ativo';
      details: string[];
    }
  | {
      kind: 'excluir_cadastro';
      cadastroId: string;
      titularNome: string;
    }
  | {
      kind: 'already_exists';
      cpf: string;
      summary: string;
    }
  | {
      kind: 'link_qr';
      linkId: string;
      linkUrl: string;
    }
  | {
      kind: 'link_associados';
      linkId: string;
      associados: string[];
    }
  | {
      kind: 'visualizar_arquivo';
      arquivoPath: string;
    };

export interface CadastroModalSignal {
  entryPoint?: CadastroEntryPoint | null;
  empresaNaoIdentificada?: boolean;
  empresaNaoIdentificadaRequired?: boolean;
  empresaObservacaoNome?: string | null;
  empresaObservacaoTexto?: string | null;
  empresaCanceladaNome?: string | null;
  lemmitLimit?: {
    limiteFormatado?: string;
    consumoFormatado?: string;
    saldoFormatado?: string;
    isUnlimited?: boolean;
  } | null;
  mustSelectStatus?: boolean;
  erpError?: CadastroErpError | null;
  excluirCadastroId?: string | null;
  excluirCadastroTitular?: string | null;
  alreadyExistsCpf?: string | null;
  alreadyExistsSummary?: string | null;
  linkQrId?: string | null;
  linkQrUrl?: string | null;
  linkAssociadosId?: string | null;
  linkAssociados?: string[];
  visualizarArquivoPath?: string | null;
}

const normalize = (value?: string | null): string => (value || '').trim();

export const resolveCadastroOverlay = (
  signal: CadastroModalSignal
): CadastroOverlayIntent | null => {
  if (signal.entryPoint) {
    return {
      kind: 'entry_point',
      entryPoint: signal.entryPoint,
    };
  }

  if (signal.empresaNaoIdentificada) {
    return {
      kind: 'empresa_nao_identificada',
      required: Boolean(signal.empresaNaoIdentificadaRequired),
    };
  }

  const observacaoNome = normalize(signal.empresaObservacaoNome);
  const observacaoTexto = normalize(signal.empresaObservacaoTexto);
  if (observacaoNome && observacaoTexto) {
    return {
      kind: 'observacoes_empresa',
      empresaNome: observacaoNome,
      observacoes: observacaoTexto,
    };
  }

  const empresaCanceladaNome = normalize(signal.empresaCanceladaNome);
  if (empresaCanceladaNome) {
    return {
      kind: 'empresa_cancelada',
      empresaNome: empresaCanceladaNome,
    };
  }

  if (signal.lemmitLimit) {
    return {
      kind: 'lemmit_limit',
      ...signal.lemmitLimit,
    };
  }

  if (signal.mustSelectStatus) {
    return { kind: 'select_status' };
  }

  if (signal.erpError?.kind === 'parceiro_invalido') {
    return {
      kind: 'parceiro_invalido',
      message: signal.erpError.message,
    };
  }

  if (signal.erpError?.kind === 'dependente_ativo') {
    return {
      kind: 'dependente_ativo',
      details: signal.erpError.details,
    };
  }

  const excluirCadastroId = normalize(signal.excluirCadastroId);
  const excluirCadastroTitular = normalize(signal.excluirCadastroTitular);
  if (excluirCadastroId && excluirCadastroTitular) {
    return {
      kind: 'excluir_cadastro',
      cadastroId: excluirCadastroId,
      titularNome: excluirCadastroTitular,
    };
  }

  const alreadyExistsCpf = normalize(signal.alreadyExistsCpf);
  const alreadyExistsSummary = normalize(signal.alreadyExistsSummary);
  if (alreadyExistsCpf && alreadyExistsSummary) {
    return {
      kind: 'already_exists',
      cpf: alreadyExistsCpf,
      summary: alreadyExistsSummary,
    };
  }

  const linkQrId = normalize(signal.linkQrId);
  const linkQrUrl = normalize(signal.linkQrUrl);
  if (linkQrId && linkQrUrl) {
    return {
      kind: 'link_qr',
      linkId: linkQrId,
      linkUrl: linkQrUrl,
    };
  }

  const linkAssociadosId = normalize(signal.linkAssociadosId);
  if (linkAssociadosId && Array.isArray(signal.linkAssociados) && signal.linkAssociados.length > 0) {
    return {
      kind: 'link_associados',
      linkId: linkAssociadosId,
      associados: signal.linkAssociados,
    };
  }

  const arquivoPath = normalize(signal.visualizarArquivoPath);
  if (arquivoPath) {
    return {
      kind: 'visualizar_arquivo',
      arquivoPath,
    };
  }

  return null;
};
