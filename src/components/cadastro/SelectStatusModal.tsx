import { useEffect, useState } from 'react';
import { Tag, X } from 'lucide-react';
import { supabase } from '../../lib/supabase';
import { Button } from '../Button';

interface StatusAdesao {
  id: string;
  nome: string;
  cor: string;
}

interface SelectStatusModalProps {
  onSelect: (statusId: string) => void;
  onClose: () => void;
}

export function SelectStatusModal({ onSelect, onClose }: SelectStatusModalProps) {
  const [statusList, setStatusList] = useState<StatusAdesao[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    void fetchStatus();
  }, []);

  const fetchStatus = async () => {
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('status_adesoes')
        .select('*')
        .order('ordem', { ascending: true });

      if (error) throw error;
      setStatusList(data || []);
    } catch (error) {
      console.error('Error fetching status:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = () => {
    if (selectedId) {
      onSelect(selectedId);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-md rounded-xl bg-white shadow-xl">
        <div className="p-6">
          <div className="mb-4 flex items-start justify-between gap-3">
            <div className="flex items-center gap-2">
              <Tag className="h-5 w-5 text-emerald-600" />
              <h3 className="text-lg font-semibold text-slate-800">Selecione o Status da Adesao</h3>
            </div>
            <button
              onClick={onClose}
              className="rounded p-1 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
              title="Continuar editando"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <p className="mb-4 text-sm text-slate-600">
            <span className="font-medium text-red-600">* Obrigatorio:</span> escolha o status da adesao antes de fechar.
          </p>

          {loading ? (
            <div className="py-8 text-center text-slate-600">Carregando...</div>
          ) : statusList.length === 0 ? (
            <div className="py-8 text-center">
              <p className="mb-4 text-slate-600">Nenhum status cadastrado. Configure os status em Configuracoes.</p>
              <Button onClick={onClose}>Fechar</Button>
            </div>
          ) : (
            <>
              <div className="mb-6 max-h-[400px] space-y-2 overflow-y-auto">
                {statusList.map((status) => (
                  <button
                    key={status.id}
                    onClick={() => setSelectedId(status.id)}
                    className={`w-full rounded-lg border-2 p-3 text-left transition-all ${
                      selectedId === status.id
                        ? 'border-emerald-500 bg-emerald-50'
                        : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className="h-4 w-4 shrink-0 rounded-full" style={{ backgroundColor: status.cor }} />
                      <span className="font-medium text-slate-800">{status.nome}</span>
                    </div>
                  </button>
                ))}
              </div>

              <div className="flex gap-2">
                <Button variant="secondary" onClick={onClose} className="flex-1">
                  Continuar editando
                </Button>
                <Button onClick={handleConfirm} disabled={!selectedId} className="flex-1">
                  Salvar status
                </Button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
