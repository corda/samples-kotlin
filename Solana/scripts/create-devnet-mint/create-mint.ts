import {
  Connection,
  Keypair,
  SystemProgram,
  Transaction,
  sendAndConfirmTransaction,
} from "@solana/web3.js";
import {
  TOKEN_2022_PROGRAM_ID,
  createInitializeMintInstruction,
  createInitializeMetadataPointerInstruction,
  getMintLen,
  ExtensionType,
  TYPE_SIZE,
  LENGTH_SIZE,
} from "@solana/spl-token";
import {
  createInitializeInstruction as createInitializeMetadataInstruction,
  pack as packMetadata,
} from "@solana/spl-token-metadata";
import { Uploader } from "@irys/upload";
import { Solana } from "@irys/upload-solana";
import bs58 from "bs58";
import * as fs from "fs";
import * as path from "path";

// =============================================================================
// EDIT THESE VALUES TO CUSTOMISE YOUR TOKEN
// =============================================================================

const TOKEN_NAME        = "Blackstone Private Credit Fund";
const TOKEN_SYMBOL      = "BCRED";
const TOKEN_DECIMALS    = 2;  // Must be > 0 for Phantom to show under Tokens instead of Collectibles
const TOKEN_DESCRIPTION = "Blackstone Private Credit Fund tokenised on Solana";

const IMAGE_FILE = path.resolve(__dirname, "../../token_image.svg");
const IRYS_GATEWAY = "https://gateway.irys.xyz";

const MINT_AUTHORITY_KEYPAIR_FILE = path.resolve(
  __dirname,
  "../../bridging-token/workflows/src/integrationTest/resources/custodiedKeys/mintAuthoritySigner.json"
);

// ── Irys helpers ──────────────────────────────────────────────────────────────

async function getIrysUploader(keypair: Keypair) {
  const privateKeyBase58 = bs58.encode(keypair.secretKey);
  return Uploader(Solana)
    .withWallet(privateKeyBase58)
    .withRpc("https://api.devnet.solana.com")
    .devnet();
}

async function uploadToArweave(
  irys: Awaited<ReturnType<typeof getIrysUploader>>,
  data: Buffer,
  contentType: string,
  label: string
): Promise<string> {
  const tags = [{ name: "Content-Type", value: contentType }];
  const price   = await irys.getPrice(data.length);
  const balance = await irys.getLoadedBalance();

  if (balance.lt(price)) {
    const needed    = price.minus(balance);
    const neededSol = irys.utils.fromAtomic(needed);
    console.log(`  Funding Irys node with ${neededSol} SOL for ${label}…`);
    await irys.fund(needed);
    console.log("  Funded.");
  }

  console.log(`  Uploading ${label} (${data.length} bytes)…`);
  const receipt = await irys.upload(data, { tags });
  const url = `${IRYS_GATEWAY}/${receipt.id}`;
  console.log(`  ✓ ${label}: ${url}`);
  return url;
}

async function main() {
  if (!fs.existsSync(MINT_AUTHORITY_KEYPAIR_FILE)) {
    throw new Error(`Keypair file not found: ${MINT_AUTHORITY_KEYPAIR_FILE}`);
  }
  const keypairBytes  = JSON.parse(fs.readFileSync(MINT_AUTHORITY_KEYPAIR_FILE, "utf-8"));
  const mintAuthority = Keypair.fromSecretKey(Uint8Array.from(keypairBytes));
  console.log("Mint authority:", mintAuthority.publicKey.toBase58());

  const connection = new Connection("https://api.devnet.solana.com", "confirmed");
  const balance    = await connection.getBalance(mintAuthority.publicKey);
  if (balance === 0) {
    throw new Error(
      `Mint authority has 0 SOL on devnet. Airdrop some first:\n` +
      `  solana airdrop 2 ${mintAuthority.publicKey.toBase58()} --url devnet`
    );
  }
  console.log(`Mint authority balance: ${(balance / 1e9).toFixed(4)} SOL\n`);

  console.log("── Arweave upload (Irys devnet) ────────────────────────────────");
  const irys = await getIrysUploader(mintAuthority);

  if (!fs.existsSync(IMAGE_FILE)) {
    throw new Error(`Image file not found: ${IMAGE_FILE}`);
  }
  const imageData = fs.readFileSync(IMAGE_FILE);
  const imageExt  = path.extname(IMAGE_FILE).toLowerCase().replace(".", "");
  const imageContentType = imageExt === "svg" ? "image/svg+xml" : `image/${imageExt}`;
  const imageUrl = await uploadToArweave(irys, imageData, imageContentType, "token image");

  const metadataJson = JSON.stringify(
    {
      name:        TOKEN_NAME,
      symbol:      TOKEN_SYMBOL,
      description: TOKEN_DESCRIPTION,
      image:       imageUrl,
    },
    null,
    2
  );
  const metadataUrl = await uploadToArweave(
    irys,
    Buffer.from(metadataJson),
    "application/json",
    "metadata JSON"
  );

  console.log();

  console.log("── Token-2022 Mint creation ─────────────────────────────────────");
  const mint = Keypair.generate();
  console.log("New mint address:", mint.publicKey.toBase58());

  // CRITICAL FIX: Match working code exactly - mintSpace ONLY for mint + extensions
  const extensions: ExtensionType[] = [ExtensionType.MetadataPointer];
  const mintSpace = getMintLen(extensions);
  const metadata = {
    updateAuthority: mintAuthority.publicKey,
    mint: mint.publicKey,
    name: TOKEN_NAME,
    symbol: TOKEN_SYMBOL,
    uri: metadataUrl,
    additionalMetadata: [],
  };
  const metadataSpace = TYPE_SIZE + LENGTH_SIZE + packMetadata(metadata).length;
  const totalSpace = mintSpace + metadataSpace;
  const lamports = await connection.getMinimumBalanceForRentExemption(totalSpace);

  console.log(`Account size: ${totalSpace} bytes  |  Rent: ${(lamports / 1e9).toFixed(6)} SOL`);

  // EXACT SEQUENCE FROM WORKING CODE
  const tx = new Transaction().add(
    // 1. CreateAccount with mintSpace (NOT totalSpace)
    SystemProgram.createAccount({
      fromPubkey: mintAuthority.publicKey,
      newAccountPubkey: mint.publicKey,
      space: mintSpace,  // ← KEY FIX: mintSpace only
      lamports,
      programId: TOKEN_2022_PROGRAM_ID,
    }),

    // 2. MetadataPointer FIRST
    createInitializeMetadataPointerInstruction(
      mint.publicKey,
      mintAuthority.publicKey,
      mint.publicKey, // metadata account = mint itself
      TOKEN_2022_PROGRAM_ID,
    ),

    // 3. InitializeMint
    createInitializeMintInstruction(
      mint.publicKey,
      TOKEN_DECIMALS,
      mintAuthority.publicKey,
      null, // no freeze authority
      TOKEN_2022_PROGRAM_ID,
    ),

    // 4. Metadata LAST
    createInitializeMetadataInstruction({
      programId: TOKEN_2022_PROGRAM_ID,
      metadata: mint.publicKey,
      updateAuthority: mintAuthority.publicKey,
      mint: mint.publicKey,
      mintAuthority: mintAuthority.publicKey,
      name: TOKEN_NAME,
      symbol: TOKEN_SYMBOL,
      uri: metadataUrl,
    })
  );

  console.log("Sending transaction…");
  const sig = await sendAndConfirmTransaction(
    connection,
    tx,
    [mintAuthority, mint],
    { commitment: "confirmed" }
  );

  console.log("\n✓ Token-2022 Mint created successfully!");
  console.log(`  Mint address : ${mint.publicKey.toBase58()}`);
  console.log(`  Metadata URI : ${metadataUrl}`);
  console.log(`  Image URI    : ${imageUrl}`);
  console.log(`  Explorer     : https://explorer.solana.com/address/${mint.publicKey.toBase58()}?cluster=devnet`);
  console.log(`  Solscan      : https://solscan.io/token/${mint.publicKey.toBase58()}?cluster=devnet`);
  console.log(`  Transaction  : https://explorer.solana.com/tx/${sig}?cluster=devnet`);

  fs.writeFileSync(
    path.join(__dirname, "last-mint.json"),
    JSON.stringify({ mintAddress: mint.publicKey.toBase58(), metadataUrl, imageUrl }, null, 2)
  );
  console.log("\n  Details saved to last-mint.json");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
