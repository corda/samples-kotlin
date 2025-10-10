#!/bin/bash
solana config set --url localhost

NOTARY_ACCOUNT=`solana address -k $1`
solana airdrop 10 $NOTARY_ACCOUNT

bridgeAuthorityWallet=./build/nodes/custodied-keys/bridge-authority-wallet.json
solana-keygen new -o $bridgeAuthorityWallet --no-bip39-passphrase -f

bigBankWallet=./build/nodes/solana-keys/big-corp-wallet.json
solana-keygen new -o $bigBankWallet --no-bip39-passphrase -f

bridgeAuthorityAccount=`solana address -k $bridgeAuthorityWallet`
funderKeyFile=$1
solana transfer $bridgeAuthorityAccount 0.1 --fee-payer $funderKeyFile --from $funderKeyFile --allow-unfunded-recipient

bigBankAccount=`solana address -k $bigBankWallet`
solana transfer $bigBankAccount 0.1 --fee-payer $funderKeyFile --from $funderKeyFile --allow-unfunded-recipient

tokenMintFile=./build/nodes/solana-keys/token-mint.json
solana-keygen new -o $tokenMintFile --no-bip39-passphrase

MINT_ACCOUNT=$(spl-token create-token \
  --program-id TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb \
  --mint-authority $bridgeAuthorityAccount \
  --fee-payer $bridgeAuthorityWallet \
  --decimals 9 \
  --output json \
  $tokenMintFile | jq -r '.commandOutput.address')

TOKEN_ACCOUNT=$(spl-token create-account \
  --program-id TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb \
  --owner $bigBankAccount \
  --fee-payer $funderKeyFile \
  $MINT_ACCOUNT  | awk '/^Creating account / {print $3}')

bigBankPubKeyFile=./build/nodes/solana-keys/big-corp.pub
echo $TOKEN_ACCOUNT >> $bigBankPubKeyFile

tokenMintPubKeyFile=./build/nodes/solana-keys/token-mint.pub
echo $MINT_ACCOUNT >> $tokenMintPubKeyFile