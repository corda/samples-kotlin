#!/bin/bash

# ========================================
# Token-2022 Metadata - CORRECT SYNTAX
# ========================================
MINT_ADDRESS="FuWNGKEmJKweaon2cnn8bwB8gogYkdDexeUZCWwvb7t5"
PAYER_KEYPAIR_PATH="/Users/simon.brooks/.config/solana/devnet.json"
MINT_AUTHORITY_PATH="./bridging-token/workflows/build/resources/integrationTest/custodiedKeys/mintAuthoritySigner.json"
TOKEN_NAME="Bridged Corda Token"
TOKEN_SYMBOL="BCT"
CLUSTER="devnet"

# ========================================
set -e

echo "🚀 Setting Token-2022 metadata (spl-token-cli 5.3.0)"
echo "Mint: $MINT_ADDRESS"
echo ""

# Setup payer (authority handled automatically via config)
solana config set --url "$CLUSTER" --keypair "$PAYER_KEYPAIR_PATH"

# Verify authority file exists
[[ ! -f "$MINT_AUTHORITY_PATH" ]] && { echo "❌ Missing: $MINT_AUTHORITY_PATH"; exit 1; }

echo "📋 Current state:"
spl-token display "$MINT_ADDRESS"

echo ""
echo "🏷️  Setting Name..."
spl-token update-metadata "$MINT_ADDRESS" name "$TOKEN_NAME" --authority "$MINT_AUTHORITY_PATH"

echo "💱 Setting Symbol..."
spl-token update-metadata "$MINT_ADDRESS" symbol "$TOKEN_SYMBOL" --authority "$MINT_AUTHORITY_PATH"

echo ""
echo "✅ SUCCESS!"
echo "📱 Phantom: Close/reopen wallet"
echo "🔍 https://explorer.solana.com/address/$MINT_ADDRESS?cluster=devnet"
echo "🎉 Shows: '$TOKEN_NAME ($TOKEN_SYMBOL)'"
