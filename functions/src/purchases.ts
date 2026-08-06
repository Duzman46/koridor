import { getDatabase } from "firebase-admin/database";
import { onValueCreated } from "firebase-functions/v2/database";
import { logger } from "firebase-functions";
import { GoogleAuth } from "google-auth-library";

/**
 * Server-side purchase verification.
 *
 * The app files a receipt; this checks the token against the Google Play Developer API and
 * only then writes the entitlement onto the profile. The database rules give clients no
 * write access to `users/{uid}/purchasedEntitlements`, so an unverified purchase can never
 * become a durable entitlement no matter what a modified client claims.
 *
 * Setup (see README):
 *  1. Enable the Google Play Android Developer API for the project.
 *  2. Link the Play Console to the project and grant the function's service account the
 *     "View financial data" permission.
 * Until that is done this function logs and marks receipts REJECTED; the app still honours
 * the purchase locally from Play's own signed response, so nobody loses what they bought.
 */

const PACKAGE_NAME = process.env.KORIDOR_PACKAGE_NAME ?? "com.duzman46.gridbound";
const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

/** Play's purchaseState for a one-time product. */
const PURCHASE_STATE_PURCHASED = 0;
const PURCHASE_STATE_PENDING = 2;

interface Receipt {
  purchaseToken: string;
  productIds: string[];
  orderId: string;
  purchaseTime: number;
  state: string;
}

export const verifyPurchase = onValueCreated(
  { ref: "/purchaseReceipts/{uid}/{tokenHash}" },
  async (event) => {
    const { uid, tokenHash } = event.params;
    const receipt = event.data.val() as Receipt | null;
    if (!receipt?.purchaseToken || !receipt.productIds?.length) return;

    const db = getDatabase();
    const receiptRef = db.ref(`purchaseReceipts/${uid}/${tokenHash}`);

    // One token, one account, forever. Claiming it globally is what stops the same
    // purchase being replayed onto a second account.
    const claim = await db
      .ref(`purchaseTokens/${tokenHash}`)
      .transaction((current) => (current === null ? { uid, at: Date.now() } : undefined));
    if (!claim.committed) {
      logger.warn("purchase token already claimed", { uid, tokenHash });
      await receiptRef.child("state").set("REJECTED");
      return;
    }

    try {
      const productId = receipt.productIds[0];
      const verdict = await verifyWithPlay(productId, receipt.purchaseToken);

      if (verdict === "pending") {
        // A slow payment method. Leave it PENDING; Play will send a fresh receipt when it
        // settles, and nothing is granted in the meantime.
        logger.info("purchase still pending", { uid, productId });
        return;
      }
      if (verdict !== "purchased") {
        logger.warn("purchase rejected by Play", { uid, productId, verdict });
        await receiptRef.child("state").set("REJECTED");
        return;
      }

      await db.ref().update({
        [`purchaseReceipts/${uid}/${tokenHash}/state`]: "VERIFIED",
        ...Object.fromEntries(
          receipt.productIds.map((id) => [`users/${uid}/purchasedEntitlements/${id}`, true])
        ),
      });
      logger.info("purchase verified", { uid, productId });
    } catch (error) {
      // Release the claim so a retry can verify the same token rather than losing it.
      await db.ref(`purchaseTokens/${tokenHash}`).remove();
      await receiptRef.child("state").set("PENDING");
      throw error;
    }
  }
);

type Verdict = "purchased" | "pending" | "unverified" | "rejected";

async function verifyWithPlay(productId: string, purchaseToken: string): Promise<Verdict> {
  let client;
  try {
    client = await new GoogleAuth({ scopes: [PLAY_SCOPE] }).getClient();
  } catch (error) {
    // No credentials configured yet. Reported rather than silently granting.
    logger.error("Play Developer API credentials are not configured", error);
    return "unverified";
  }

  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
    `${encodeURIComponent(PACKAGE_NAME)}/purchases/products/` +
    `${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`;

  const response = await client.request<{
    purchaseState?: number;
    acknowledgementState?: number;
  }>({ url });

  const state = response.data.purchaseState;
  if (state === PURCHASE_STATE_PURCHASED) return "purchased";
  if (state === PURCHASE_STATE_PENDING) return "pending";
  return "rejected";
}

/**
 * Withdraws an entitlement when a receipt is marked REVOKED — the hook support or a
 * Play refund webhook uses to take back a refunded purchase.
 */
export const revokeEntitlement = onValueCreated(
  { ref: "/entitlementRevocations/{uid}/{productId}" },
  async (event) => {
    const { uid, productId } = event.params;
    const db = getDatabase();
    await db.ref(`users/${uid}/purchasedEntitlements/${productId}`).remove();
    await db.ref(`entitlementRevocations/${uid}/${productId}`).remove();
    logger.info("entitlement revoked", { uid, productId });
  }
);
