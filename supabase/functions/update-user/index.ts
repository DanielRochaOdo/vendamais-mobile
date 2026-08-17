import { createClient } from 'npm:@supabase/supabase-js@2.57.4';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Client-Info, Apikey',
};

interface UpdateUserRequest {
  user_id: string;
  name: string;
  email: string;
  telefone?: string | null;
  is_active: boolean;
  lemmit_limite_consultas?: number | null;
  role?: 'ADMINISTRADOR' | 'GERENTE' | 'GESTOR' | 'CADASTRO' | 'SUPERVISOR' | 'VENDEDOR' | 'ADESIONISTA';
  external_id?: string | null;
  team_id?: string | null;
}

const requiresTeamAndExternal = (role?: string | null) =>
  ['SUPERVISOR', 'VENDEDOR', 'ADESIONISTA'].includes(role ?? '');

const roleMustNotHaveTeamOrExternal = (role?: string | null) =>
  ['GERENTE', 'CADASTRO'].includes(role ?? '');

const normalizeOptionalText = (value: string | null | undefined): string | null => {
  if (typeof value !== 'string') return null;
  const normalized = value.trim();
  return normalized || null;
};

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 200, headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabaseClient = createClient(supabaseUrl, supabaseServiceKey);

    const authHeader = req.headers.get('Authorization');
    if (!authHeader) throw new Error('Missing Authorization header');

    const token = authHeader.replace('Bearer ', '');
    const { data: { user: requestingUser }, error: authError } = await supabaseClient.auth.getUser(token);

    if (authError || !requestingUser) throw new Error('Unauthorized');

    const { data: requestingProfile, error: profileError } = await supabaseClient
      .from('profiles')
      .select('role, team_id, is_active')
      .eq('id', requestingUser.id)
      .maybeSingle();

    if (profileError || !requestingProfile || !requestingProfile.is_active) {
      throw new Error('User profile not found or inactive');
    }

    if (!['ADMINISTRADOR', 'GERENTE', 'SUPERVISOR'].includes(requestingProfile.role)) {
      throw new Error('Insufficient permissions to update users');
    }

    const requestData: UpdateUserRequest = await req.json();
    const { user_id, name, email, telefone, is_active, lemmit_limite_consultas, role, external_id, team_id } = requestData;

    if (!user_id || !name || !email) {
      throw new Error('Missing required fields: user_id, name, email');
    }

    const { data: targetProfile, error: targetError } = await supabaseClient
      .from('profiles')
      .select('id, role, team_id, external_id, email')
      .eq('id', user_id)
      .maybeSingle();

    if (targetError || !targetProfile) {
      throw new Error('Target user not found');
    }

    if (requestingProfile.role === 'SUPERVISOR' && requestingProfile.team_id !== targetProfile.team_id) {
      throw new Error('Supervisors can only update users in their own team');
    }

    const effectiveRole = role ?? targetProfile.role;
    const roleChanged = role !== undefined && role !== targetProfile.role;

    let nextExternalId: string | null = null;
    let nextTeamId: string | null = null;

    if (requiresTeamAndExternal(effectiveRole)) {
      const requestedExternalId = normalizeOptionalText(external_id);
      const requestedTeamId = normalizeOptionalText(team_id);

      // Ao editar apenas nome/email/telefone/limite/ativo, preserve os vínculos
      // atuais do vendedor/supervisor/adesionista em vez de exigir que todo
      // cliente reenvie external_id e team_id em cada alteração.
      nextExternalId = requestedExternalId ?? (!roleChanged ? normalizeOptionalText(targetProfile.external_id) : null);
      nextTeamId = requestedTeamId ?? (!roleChanged ? normalizeOptionalText(targetProfile.team_id) : null);

      if (!nextExternalId || !nextTeamId) {
        throw new Error(`${effectiveRole} precisa ter ID Externo e Equipe definidos`);
      }
    } else if (roleMustNotHaveTeamOrExternal(effectiveRole)) {
      nextExternalId = null;
      nextTeamId = null;
    } else {
      // Mantém o comportamento histórico para os demais papéis (ex.: ADMINISTRADOR):
      // a edição administrativa não introduz vínculo de equipe implicitamente.
      nextExternalId = null;
      nextTeamId = null;
    }

    const { error: authUpdateError } = await supabaseClient.auth.admin.updateUserById(user_id, {
      email,
      email_confirm: true,
    });

    if (authUpdateError) {
      throw new Error(`Failed to update auth user: ${authUpdateError.message}`);
    }

    const profileUpdatePayload = {
      name,
      email,
      telefone: telefone ?? null,
      is_active,
      lemmit_limite_consultas: lemmit_limite_consultas ?? null,
      ...(requestingProfile.role === 'ADMINISTRADOR' ? { role: role ?? targetProfile.role } : {}),
      external_id: nextExternalId,
      team_id: nextTeamId,
    };

    const { data: updatedProfile, error: updateProfileError } = await supabaseClient
      .from('profiles')
      .update(profileUpdatePayload)
      .eq('id', user_id)
      .select()
      .single();

    if (updateProfileError) {
      await supabaseClient.auth.admin.updateUserById(user_id, {
        email: targetProfile.email,
        email_confirm: true,
      });
      throw new Error(`Failed to update profile: ${updateProfileError.message}`);
    }

    return new Response(JSON.stringify({ success: true, user: updatedProfile }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (error) {
    return new Response(
      JSON.stringify({
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error',
      }),
      {
        status: 400,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json',
        },
      }
    );
  }
});
