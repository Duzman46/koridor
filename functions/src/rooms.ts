import { getDatabase } from "firebase-admin/database";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";

/**
 * Sweeps rooms that were never played out.
 *
 * Rooms carry an `expiresAt` stamp: half an hour for one still waiting for an opponent, a
 * day once a match is under way. Without this the database would accumulate abandoned rooms
 * forever, and the room browser would fill up with codes nobody is behind.
 *
 * Finished rooms are kept a little longer than their expiry so the rating function and a
 * reconnecting client can still read the outcome, then removed with everything keyed to them.
 */
export const sweepExpiredRooms = onSchedule("every 30 minutes", async () => {
  const db = getDatabase();
  const now = Date.now();

  const snapshot = await db
    .ref("rooms")
    .orderByChild("expiresAt")
    .endAt(now)
    .limitToFirst(500)
    .get();

  if (!snapshot.exists()) {
    logger.info("no expired rooms");
    return;
  }

  const updates: Record<string, unknown> = {};
  let removed = 0;
  let expired = 0;

  snapshot.forEach((child) => {
    const room = child.val();
    const code = child.key;
    if (!code || !room) return;

    if (room.status === "IN_PROGRESS" || room.status === "STARTING") {
      // A match that ran past its window is closed rather than deleted, so both players
      // still see why it ended instead of the room vanishing mid-game.
      updates[`rooms/${code}/status`] = "EXPIRED";
      updates[`rooms/${code}/currentTurnUserId`] = "";
      updates[`rooms/${code}/expiresAt`] = now + 60 * 60 * 1000;
      updates[`rooms/${code}/browseKey`] = `${room.visibility ?? "PRIVATE"}_EXPIRED`;
      expired += 1;
    } else {
      updates[`rooms/${code}`] = null;
      updates[`roomSecrets/${code}`] = null;
      removed += 1;
    }
  });

  if (Object.keys(updates).length > 0) {
    await db.ref().update(updates);
  }
  logger.info("room sweep complete", { removed, expired });
});
