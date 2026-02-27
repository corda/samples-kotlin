import {
  Connection,
  Keypair,
  PublicKey,
  Transaction,
  sendAndConfirmTransaction,
} from "@solana/web3.js";
import { TOKEN_2022_PROGRAM_ID } from "@solana/spl-token";
import { createUpdateFieldInstruction } from "@solana/spl-token-metadata";
import * as fs from "fs";
import * as path from "path";

// =============================================================================
// EDIT THESE VALUES TO UPDATE YOUR TOKEN'S METADATA
// Set to null to leave a field unchanged.
// =============================================================================

// Leave as null to auto-read from last-mint.json produced by create-mint.ts,
// or paste the mint address string directly, e.g.:
//   const MINT_ADDRESS = "FuWNGKEmJKweaon2cnn8bwB8gogYkdDexeUZCWwvb7t5";
const MINT_ADDRESS: string | null = null;

// Standard fields – set to null to skip updating that field
const NEW_NAME:   string | null = "My Updated Token";
const NEW_SYMBOL: string | null = "MUT";
const NEW_URI:    string | null = "https://example.com/my-token-metadata.json";

// Additional custom fields visible in some explorers.
// Each entry is a [key, value] pair. Remove any you don't need.
const ADDITIONAL_FIELDS: [string, string][] = [
  // ["twitter", "https://twitter.com/yourhandle"],
  // ["discord", "https://discord.gg/yourserver"],
];

// =============================================================================

const MINT_AUTHORITY_KEYPAIR_FILE = path.resolve(
  __dirname,
  "../../bridging-token/workflows/src/integrationTest/resources/custodiedKeys/mintAuthoritySigner.json"
);

async function resolveMintAddress(): Promise<PublicKey> {
  if (MINT_ADDRESS) {
    return new PublicKey(MINT_ADDRESS);
  }
  const lastMintFile = path.join(__dirname, "last-mint.json");
  if (!fs.existsSync(lastMintFile)) {
    throw new Error(
      "No MINT_ADDRESS set and last-mint.json not found. " +
      "Either run create-mint.ts first or set MINT_ADDRESS in this file."
    );
  }
  const { mintAddress } = JSON.parse(fs.readFileSync(lastMintFile, "utf-8"));
  console.log(`Using mint address from last-mint.json: ${mintAddress}`);
  return new PublicKey(mintAddress);
}

async function main() {
  // ── Load mintAuthoritySigner (= update authority) ──────────────────────────
  const keypairBytes = JSON.parse(fs.readFileSync(MINT_AUTHORITY_KEYPAIR_FILE, "utf-8"));
  const updateAuthority = Keypair.fromSecretKey(Uint8Array.from(keypairBytes));
  console.log("Update authority:", updateAuthority.publicKey.toBase58());

  const mintPubkey = await resolveMintAddress();
  console.log("Mint address:    ", mintPubkey.toBase58());

  // ── Connect to Devnet ───────────────────────────────────────────────────────
  const connection = new Connection("https://api.devnet.solana.com", "confirmed");

  // ── Build update instructions ───────────────────────────────────────────────
  // Each field must be sent in its own updateField instruction.
  // Sending them all in one transaction is fine.
  const instructions = [];

  if (NEW_NAME !== null) {
    instructions.push(
      createUpdateFieldInstruction({
        programId:       TOKEN_2022_PROGRAM_ID,
        metadata:        mintPubkey,
        updateAuthority: updateAuthority.publicKey,
        field:           "name",
        value:           NEW_NAME,
      })
    );
    console.log(`  Updating name   → "${NEW_NAME}"`);
  }

  if (NEW_SYMBOL !== null) {
    instructions.push(
      createUpdateFieldInstruction({
        programId:       TOKEN_2022_PROGRAM_ID,
        metadata:        mintPubkey,
        updateAuthority: updateAuthority.publicKey,
        field:           "symbol",
        value:           NEW_SYMBOL,
      })
    );
    console.log(`  Updating symbol → "${NEW_SYMBOL}"`);
  }

  if (NEW_URI !== null) {
    instructions.push(
      createUpdateFieldInstruction({
        programId:       TOKEN_2022_PROGRAM_ID,
        metadata:        mintPubkey,
        updateAuthority: updateAuthority.publicKey,
        field:           "uri",
        value:           NEW_URI,
      })
    );
    console.log(`  Updating uri    → "${NEW_URI}"`);
  }

  for (const [key, value] of ADDITIONAL_FIELDS) {
    instructions.push(
      createUpdateFieldInstruction({
        programId:       TOKEN_2022_PROGRAM_ID,
        metadata:        mintPubkey,
        updateAuthority: updateAuthority.publicKey,
        field:           key,
        value,
      })
    );
    console.log(`  Updating custom field "${key}" → "${value}"`);
  }

  if (instructions.length === 0) {
    console.log("Nothing to update – all fields are null.");
    return;
  }

  // ── Send ────────────────────────────────────────────────────────────────────
  const tx = new Transaction().add(...instructions);
  console.log("\nSending transaction…");
  const sig = await sendAndConfirmTransaction(
    connection,
    tx,
    [updateAuthority],
    { commitment: "confirmed" }
  );

  console.log("\n✓ Metadata updated!");
  console.log(`  Explorer    : https://explorer.solana.com/address/${mintPubkey.toBase58()}?cluster=devnet`);
  console.log(`  Transaction : https://explorer.solana.com/tx/${sig}?cluster=devnet`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
