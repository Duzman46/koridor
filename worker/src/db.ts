/**
 * The slice of the Realtime Database REST API this worker needs.
 *
 * There is no Admin SDK here — it does not run on Workers. The REST API is the same surface
 * underneath, and the parts that matter are all present: indexed queries, multi-path updates,
 * and compare-and-set through ETags, which is what stands in for a transaction.
 *
 * Every call is authenticated with a service-account access token, so these reads and writes
 * bypass the security rules entirely. That is the point: rating is exactly the thing no
 * client is allowed to write.
 */
export interface Rtdb {
  /** Reads a node. `query` values must already be JSON, e.g. `equalTo: '"PENDING"'`. */
  get<T>(path: string, query?: Record<string, string>): Promise<T | null>;

  /**
   * Moves `path` from `expected` to `next`, and only if it still holds `expected`.
   *
   * Returns false when someone got there first. This is the claim that stops one match
   * being rated twice if two runs ever overlap.
   */
  claim(path: string, expected: string, next: string): Promise<boolean>;

  set(path: string, value: unknown): Promise<void>;

  /** One multi-path update: every key is a path from the root, and all of it lands together. */
  update(updates: Record<string, unknown>): Promise<void>;
}

export class RtdbError extends Error {
  constructor(
    readonly status: number,
    readonly path: string,
    body: string
  ) {
    super(`database ${status} at ${path}: ${body.slice(0, 200)}`);
  }
}

/**
 * @param baseUrl the database URL, or the emulator's origin
 * @param token supplies a bearer token; the emulator accepts the literal `owner`
 * @param baseQuery extra parameters on every request — the emulator needs `ns`
 */
export function restDatabase(
  baseUrl: string,
  token: () => Promise<string>,
  baseQuery: Record<string, string> = {}
): Rtdb {
  const root = baseUrl.replace(/\/+$/, "");

  async function call(
    path: string,
    init: RequestInit,
    query: Record<string, string> = {}
  ): Promise<Response> {
    const url = new URL(`${root}/${path}.json`);
    for (const [key, value] of Object.entries({ ...baseQuery, ...query })) {
      url.searchParams.set(key, value);
    }
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${await token()}`);
    return fetch(url.toString(), { ...init, headers });
  }

  async function ok(path: string, response: Response): Promise<Response> {
    if (!response.ok) throw new RtdbError(response.status, path, await response.text());
    return response;
  }

  return {
    async get<T>(path: string, query: Record<string, string> = {}): Promise<T | null> {
      const response = await ok(path, await call(path, { method: "GET" }, query));
      return (await response.json()) as T | null;
    },

    async claim(path: string, expected: string, next: string): Promise<boolean> {
      const current = await ok(
        path,
        await call(path, { method: "GET", headers: { "X-Firebase-ETag": "true" } })
      );
      if ((await current.json()) !== expected) return false;
      const etag = current.headers.get("ETag");
      if (!etag) throw new RtdbError(current.status, path, "no ETag on a conditional read");

      const written = await call(path, {
        method: "PUT",
        headers: { "if-match": etag, "Content-Type": "application/json" },
        body: JSON.stringify(next),
      });
      // 412 is the whole reason this is conditional: someone else moved it first.
      if (written.status === 412) return false;
      await ok(path, written);
      return true;
    },

    async set(path: string, value: unknown): Promise<void> {
      await ok(
        path,
        await call(path, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(value),
        })
      );
    },

    async update(updates: Record<string, unknown>): Promise<void> {
      if (Object.keys(updates).length === 0) return;
      await ok(
        "/",
        await call("", {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(updates),
        })
      );
    },
  };
}
