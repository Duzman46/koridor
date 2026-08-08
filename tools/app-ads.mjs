#!/usr/bin/env node
// Writes public/app-ads.txt from the AdMob app id in monetization.properties.
//
// app-ads.txt is how a buyer checks that whoever is selling this app's ad space is allowed to.
// Without it a large part of programmatic demand simply does not bid, so the slots still fill
// but at the price nobody competed for. It is a text file, and it is one of the few things
// that changes revenue without changing a line of the app.
//
// Generated rather than committed, because the publisher id is a real account identifier and
// the rule in this repository is that no real id is ever checked in. public/app-ads.txt is
// gitignored; run this before `firebase deploy --only hosting`.
//
// Two things have to be true on Google's side for the file to be found at all, and neither of
// them is in this repository: the Play listing's "Website" field must point at the same
// domain, and AdMob has to crawl it, which it does within a day or two.

import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const source = join(root, "monetization.properties");
const target = join(root, "public", "app-ads.txt");

if (!existsSync(source)) {
  console.error("monetization.properties is missing — nothing to derive a publisher id from.");
  process.exit(1);
}

const properties = Object.fromEntries(
  readFileSync(source, "utf8")
    .split(/\r?\n/)
    .filter((line) => line.trim() && !line.trim().startsWith("#"))
    .map((line) => {
      const at = line.indexOf("=");
      return [line.slice(0, at).trim(), line.slice(at + 1).trim()];
    }),
);

const appId = properties.KORIDOR_ADMOB_APP_ID ?? "";
// ca-app-pub-<16 digits>~<10 digits>. The publisher is the part before the tilde, and it is
// what a buyer matches against; the app-specific half after it never appears in this file.
const match = /^ca-app-(pub-\d{16})~\d+$/.exec(appId);
if (!match) {
  console.error(`KORIDOR_ADMOB_APP_ID is not an AdMob app id: ${appId || "(empty)"}`);
  process.exit(1);
}
const publisher = match[1];

// Google's own test publisher. Publishing it would claim, in public, that a test account is
// authorised to sell this app's inventory — which is both false and the sort of thing that
// gets an AdMob account looked at.
if (publisher === "pub-3940256099942544") {
  console.error("That is Google's test publisher id. Put the real one in monetization.properties.");
  process.exit(1);
}

// f08c47fec0942fa0 is Google's certification authority id, fixed for every AdMob publisher.
writeFileSync(target, `google.com, ${publisher}, DIRECT, f08c47fec0942fa0\n`, "utf8");
console.log(`public/app-ads.txt written for ${publisher}`);
