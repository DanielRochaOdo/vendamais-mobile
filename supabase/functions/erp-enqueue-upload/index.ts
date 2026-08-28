import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

interface EnqueueRequest {
  cadastroId: string | null;
  idFuncionario: number;
  idDependente: number;
  arquivoPath: string;
  arquivoNome: string;
  tipo: "titular" | "dependente";
  bucket?: string;
}

const KNOWN_QUEUE_STATUSES = ["queued", "retry_wait", "processing", "failed", "success"];

function triggerQueueProcessor(supabaseUrl: string, serviceRoleKey: string) {
  const promise = fetch(`${supabaseUrl}/functions/v1/erp-process-upload-queue`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${serviceRoleKey}`,
    },
    body: "{}",
  }).catch((error) => {
    console.warn("Falha ao disparar processamento imediato da fila:", error);
  });

  const edgeRuntime = (globalThis as any).EdgeRuntime;
  if (edgeRuntime?.waitUntil) {
    edgeRuntime.waitUntil(promise);
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 200,
      headers: corsHeaders,
    });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const supabaseClient = createClient(
      supabaseUrl,
      serviceRoleKey,
      {
        auth: {
          autoRefreshToken: false,
          persistSession: false,
        },
      },
    );

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Autorização necessária" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const token = authHeader.replace("Bearer ", "");
    const { data: { user }, error: authError } = await supabaseClient.auth.getUser(token);

    if (authError || !user) {
      return new Response(JSON.stringify({ error: "Token inválido" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: profile } = await supabaseClient
      .from("profiles")
      .select("id, role")
      .eq("id", user.id)
      .single();

    if (!profile) {
      return new Response(JSON.stringify({ error: "Perfil não encontrado" }), {
        status: 404,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const body: EnqueueRequest = await req.json();
    const bucket = body.bucket?.trim() || "cadastros-temp-files";
    const arquivoPath = body.arquivoPath?.trim();
    const arquivoNome = body.arquivoNome?.trim();

    if (
      !body.idFuncionario ||
      !body.idDependente ||
      !arquivoPath ||
      !arquivoNome ||
      !["titular", "dependente"].includes(body.tipo)
    ) {
      return new Response(JSON.stringify({ error: "Parâmetros obrigatórios faltando ou inválidos" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    let createdBy = user.id;

    if (body.cadastroId) {
      const { data: cadastro } = await supabaseClient
        .from("cadastros")
        .select("id, created_by")
        .eq("id", body.cadastroId)
        .single();

      if (cadastro) {
        createdBy = cadastro.created_by || user.id;
      }
    }

    const { data: existingItems, error: existingError } = await supabaseClient
      .from("erp_upload_queue")
      .select("*")
      .eq("id_funcionario", body.idFuncionario)
      .eq("id_dependente", body.idDependente)
      .eq("arquivo_path", arquivoPath)
      .eq("bucket", bucket)
      .in("status", KNOWN_QUEUE_STATUSES)
      .order("created_at", { ascending: false })
      .limit(1);

    if (existingError) {
      console.warn("Não foi possível consultar upload existente; será tentado um novo enqueue:", existingError);
    }

    const existingItem = existingItems?.[0];
    if (existingItem) {
      if (existingItem.status === "success") {
        if (body.cadastroId && !existingItem.cadastro_id) {
          await supabaseClient
            .from("erp_upload_queue")
            .update({ cadastro_id: body.cadastroId, created_by: createdBy })
            .eq("id", existingItem.id);
        }

        return new Response(
          JSON.stringify({
            queued: true,
            reused: true,
            delivered: true,
            queue_id: existingItem.id,
            message: "Este contrato já foi entregue ao ERP. Nenhum novo envio foi criado.",
          }),
          {
            status: 200,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
          },
        );
      }

      if (existingItem.status !== "processing") {
        const isManualRestartAfterFinalFailure = existingItem.status === "failed";
        const { data: reusedItem, error: reuseError } = await supabaseClient
          .from("erp_upload_queue")
          .update({
            cadastro_id: body.cadastroId || existingItem.cadastro_id,
            status: "queued",
            attempts: isManualRestartAfterFinalFailure ? 0 : existingItem.attempts,
            next_attempt_at: new Date().toISOString(),
            last_error: null,
            last_status_code: null,
            arquivo_nome: arquivoNome,
            tipo: body.tipo,
            created_by: createdBy,
          })
          .eq("id", existingItem.id)
          .select()
          .single();

        if (reuseError || !reusedItem) {
          console.error("Erro ao reativar upload existente:", reuseError);
          return new Response(
            JSON.stringify({ error: "Erro ao reativar upload", details: reuseError?.message }),
            {
              status: 500,
              headers: { ...corsHeaders, "Content-Type": "application/json" },
            },
          );
        }

        triggerQueueProcessor(supabaseUrl, serviceRoleKey);
        return new Response(
          JSON.stringify({
            queued: true,
            reused: true,
            queue_id: reusedItem.id,
            message: "Contrato já armazenado. Retry reativado sem necessidade de anexar o arquivo novamente.",
          }),
          {
            status: 200,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
          },
        );
      }

      if (body.cadastroId && !existingItem.cadastro_id) {
        await supabaseClient
          .from("erp_upload_queue")
          .update({ cadastro_id: body.cadastroId, created_by: createdBy })
          .eq("id", existingItem.id);
      }

      return new Response(
        JSON.stringify({
          queued: true,
          reused: true,
          processing: true,
          queue_id: existingItem.id,
          message: "Este contrato já está sendo enviado. Não é necessário anexar o arquivo novamente.",
        }),
        {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        },
      );
    }

    const queueItem = {
      cadastro_id: body.cadastroId,
      created_by: createdBy,
      id_funcionario: body.idFuncionario,
      id_dependente: body.idDependente,
      arquivo_path: arquivoPath,
      arquivo_nome: arquivoNome,
      bucket,
      tipo: body.tipo,
      status: "queued",
      attempts: 0,
      next_attempt_at: new Date().toISOString(),
    };

    const { data: queueData, error: queueError } = await supabaseClient
      .from("erp_upload_queue")
      .insert(queueItem)
      .select()
      .single();

    if (queueError) {
      console.error("Erro ao enfileirar upload:", queueError);
      return new Response(
        JSON.stringify({ error: "Erro ao enfileirar upload", details: queueError.message }),
        {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        },
      );
    }

    triggerQueueProcessor(supabaseUrl, serviceRoleKey);

    return new Response(
      JSON.stringify({
        queued: true,
        reused: false,
        queue_id: queueData.id,
        message: "Contrato enfileirado para envio ao ERP. O processamento será iniciado automaticamente.",
      }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  } catch (error) {
    console.error("Erro na função erp-enqueue-upload:", error);
    return new Response(
      JSON.stringify({
        error: "Erro interno",
        details: error instanceof Error ? error.message : String(error),
      }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  }
});
