/**
 * Turns a Google service-account key into a database access token.
 *
 * The Admin SDK does this for you and does not run on Workers, so it is done by hand: build
 * a JWT, sign it with the account's private key, and swap it at Google's token endpoint. The
 * signing uses WebCrypto, which is available in the runtime — no dependencies at all, which
 * matters for a worker whose entire job is to be trusted with a private key.
 *
 * The token is cached for its lifetime. Isolates stay warm between cron runs, so in practice
 * the key is used a handful of times a day rather than once a minute.
 */
const TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
const SCOPES = [
  "https://www.googleapis.com/auth/firebase.database",
  "https://www.googleapis.com/auth/userinfo.email",
].join(" ");

/** Renew this long before expiry, so a token cannot go stale mid-request. */
const RENEW_MARGIN_SECONDS = 120;

interface ServiceAccount {
  client_email: string;
  private_key: string;
}

let cached: { token: string; expiresAt: number } | null = null;

export function resetTokenCache(): void {
  cached = null;
}

export async function accessToken(serviceAccountJson: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.expiresAt - RENEW_MARGIN_SECONDS > now) return cached.token;

  const account = JSON.parse(serviceAccountJson) as ServiceAccount;
  if (!account.client_email || !account.private_key) {
    throw new Error("service account key is missing client_email or private_key");
  }

  const assertion = await signJwt(account, now);
  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) {
    throw new Error(`token exchange failed: ${response.status} ${await response.text()}`);
  }
  const granted = (await response.json()) as { access_token: string; expires_in: number };
  cached = { token: granted.access_token, expiresAt: now + granted.expires_in };
  return granted.access_token;
}

async function signJwt(account: ServiceAccount, now: number): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: account.client_email,
    scope: SCOPES,
    aud: TOKEN_ENDPOINT,
    iat: now,
    exp: now + 3600,
  };
  const body = `${base64Url(JSON.stringify(header))}.${base64Url(JSON.stringify(claims))}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(body)
  );
  return `${body}.${base64UrlBytes(new Uint8Array(signature))}`;
}

/**
 * A key pasted through a shell or a JSON field often arrives with its newlines escaped,
 * so `\n` is restored before the PEM envelope is stripped.
 */
function pemToDer(pem: string): ArrayBuffer {
  const body = pem
    .replace(/\\n/g, "\n")
    .replace(/-----[A-Z ]+-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

function base64Url(value: string): string {
  return base64UrlBytes(new TextEncoder().encode(value));
}

function base64UrlBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
