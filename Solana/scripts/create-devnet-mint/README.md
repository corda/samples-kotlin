# Create Devnet Mint

Creates a Token-2022 mint on Solana Devnet with embedded metadata, uploading the token image and metadata JSON to Arweave first so they appear correctly in Phantom wallet.

## Prerequisites

- Node.js 18+
- The `mintAuthoritySigner` keypair must have devnet SOL (used both to pay for the Solana transaction and to fund the Arweave upload via Irys)

If the keypair has no SOL, airdrop some first:

```bash
solana airdrop 2 <mintAuthoritySigner-pubkey> --url devnet
```

## Install

```bash
npm install
```

## Create a new mint

1. Edit the token details at the top of `create-mint.ts`:

   ```ts
   const TOKEN_NAME        = "Blackstone Private Credit Fund";
   const TOKEN_SYMBOL      = "BCRED";
   const TOKEN_DECIMALS    = 0;
   const TOKEN_DESCRIPTION = "Blackstone Private Credit Fund tokenised on Solana";
   const IMAGE_FILE        = path.resolve(__dirname, "../../token_image.svg");
   ```

2. Run the script:

   ```bash
   npm run create
   ```

The script will:
- Upload the token image to Arweave (via Irys devnet, paid in devnet SOL)
- Build and upload a Metaplex-standard metadata JSON referencing the image
- Create the Token-2022 mint on Devnet with the Arweave URI embedded

The new mint address and Arweave URLs are printed to the console and saved to `last-mint.json`.

## Update metadata on an existing mint

1. Edit the fields you want to change at the top of `update-metadata.ts` (set any field to `null` to leave it unchanged):

   ```ts
   const MINT_ADDRESS = null;       // null = read from last-mint.json automatically
   const NEW_NAME     = "New Name";
   const NEW_SYMBOL   = null;
   const NEW_URI      = "https://gateway.irys.xyz/<new-upload-id>";
   ```

2. Run:

   ```bash
   npm run update
   ```

## Viewing in Phantom wallet

1. Open Phantom → **Settings → Developer Settings → Enable Testnet Mode**
2. Switch the network to **Devnet**
3. Go to **Manage Token List** and search for the mint address

Phantom fetches the `uri` field from the on-chain metadata to display the token name, symbol, image, and description.

## Arweave storage: devnet vs mainnet

By default the script uploads to the **Irys devnet node** (`https://devnet.irys.xyz`). Devnet uploads are temporary (a few weeks) and are sufficient for Phantom devnet testing.

For permanent storage, remove `.devnet()` and `.withRpc(...)` from `getIrysUploader()` in `create-mint.ts` and point to the mainnet node instead — the `Uploader` will default to mainnet Irys when no `devnet()` call is chained.

## Keypair location

Both scripts read `mintAuthoritySigner` from:

```
../../bridging-token/workflows/src/integrationTest/resources/custodiedKeys/mintAuthoritySigner.json
```

This keypair is the **mint authority** and the **metadata update authority**.
