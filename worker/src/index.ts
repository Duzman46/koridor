import { accessToken } from "./auth.js";
import { restDatabase } from "./db.js";
import { sweep } from "./sweep.js";

/**
 * Koridor's server.
 *
 * Everything competitive — rating, the weekly board, clearing away dead rooms — has to be
 * written by something no player controls, or a modified client could award itself anything
 * it liked. That used to be a Cloud Function; Cloud Functions require a billing account, so
 * it is this instead: a scheduled worker on a free plan, holding a service-account key.
 *
 * It deliberately has no `fetch` handler. A worker with only a `scheduled` handler has no
 * public URL at all, which means the thing holding the database's admin key cannot be
 * reached from the internet — there is no endpoint to find, probe, or abuse.
 */
export interface Env {
  /** The service-account JSON, whole. A Workers secret, never a var and never in the repo. */
  FIREBASE_SERVICE_ACCOUNT: string;
  FIREBASE_DATABASE_URL: string;
}

export default {
  async scheduled(_event: ScheduledEvent, env: Env): Promise<void> {
    const db = restDatabase(env.FIREBASE_DATABASE_URL, () =>
      accessToken(env.FIREBASE_SERVICE_ACCOUNT)
    );
    const result = await sweep(db, Date.now());
    // Logged every run so `wrangler tail` shows a heartbeat, not just failures — a cron that
    // silently stopped firing looks exactly like a cron with nothing to do.
    console.log("sweep", JSON.stringify(result));
  },
};
