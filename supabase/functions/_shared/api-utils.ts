export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

type SupabaseLike = {
  from: (table: string) => {
    insert: (payload: any) => PromiseLike<any>;
  };
  auth: {
    getUser: (token: string) => Promise<{
      data: {
        user: { id: string; email?: string | null } | null;
      };
      error?: unknown;
    }>;
  };
};

export interface ApiLogData {
  user_id?: string;
  user_email?: string;
  endpoint: string;
  method: string;
  request_body: unknown;
  response_body?: unknown;
  status_code?: number;
  success: boolean;
  error_message?: string;
  duration_ms: number;
  [key: string]: unknown;
}

export async function saveLog(supabase: SupabaseLike, logData: ApiLogData) {
  try {
    await supabase.from("api_logs").insert(logData);
  } catch (error) {
    console.error("Error saving log:", error);
  }
}

export async function resolveRequestUser(
  supabase: SupabaseLike,
  req: Request
): Promise<{ userId?: string; userEmail?: string }> {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return {};
  }

  const token = authHeader.replace("Bearer ", "").trim();
  if (!token) {
    return {};
  }

  const { data: { user } } = await supabase.auth.getUser(token);
  if (!user) {
    return {};
  }

  return {
    userId: user.id,
    userEmail: user.email ?? undefined,
  };
}
