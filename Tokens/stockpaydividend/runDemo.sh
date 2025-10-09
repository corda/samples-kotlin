cd /Users/szymon.sztuka/workspace/samples-kotlin/Tokens/stockpaydividend

solana config set --url localhost

./gradlew deployNodes
# for local net change Notary node.conf rpcUtl =  http://localhost:8899

NOTARY_FILE=./bridging-flows/src/main/resources/Dev7chG99tLCAny3PNYmBdyhaKEVcZnSTp3p1mKVb5m5.json
NOTARY_ACCOUNT=`solana address -k $NOTARY_FILE`
solana airdrop 10 $NOTARY_ACCOUNT

bridgeAuthorityWallet=./build/nodes/custodied-keys/bridge-authority-wallet.json
solana-keygen new -o $bridgeAuthorityWallet --no-bip39-passphrase -f

bigBankWallet=./build/nodes/solana-keys/big-corp-wallet.json
solana-keygen new -o $bigBankWallet --no-bip39-passphrase -f

bridgeAuthorityAccount=`solana address -k $bridgeAuthorityWallet`
funderKeyFile=$NOTARY_FILE
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
  --fee-payer $NOTARY_FILE \
  $MINT_ACCOUNT  | awk '/^Creating account / {print $3}')

bigBankPubKeyFile=./build/nodes/solana-keys/big-corp.pub
echo $TOKEN_ACCOUNT >> $bigBankPubKeyFile

tokenMintPubKeyFile=./build/nodes/solana-keys/token-mint.pub
echo $MINT_ACCOUNT >> $tokenMintPubKeyFile

./gradlew expandBACordappConfig

./build/nodes/runnodes

# Check
spl-token balance --address $TOKEN_ACCOUNT
spl-token display $TOKEN_ACCOUNT


# On WayneCo node console:
start CreateAndIssueStock \
  symbol: TEST, \
  name: "Test Stock", \
  currency: USD, \
  price: 7.4, \
  issueVol: 2000, \
  notary: "O=Notary Service,L=Zurich,C=CH", \
  linearId: 6116560b-c78e-4e13-871d-d666a5d032a3

start MoveStock symbol: TEST, quantity: 1000, recipient: "O=Bridging Authority,L=New York,C=US"

# On BridgingAuthority node console:
start GetTokenToBridge symbol: TEST

start BridgeTokenRpc tokenRef: { txhash: <TX_HASH>, index: 0 } , bridgeAuthority: "O=Bridging Authority,L=New York,C=US"

start BridgeTokenRpc tokenRef: { txhash: 27D1EA2D305270BD1E31AA89AA1022A209412BA001F4BE46684549E7AA1DC380, index: 0 } , bridgeAuthority: "O=Bridging Authority,L=New York,C=US"

# Check
spl-token balance --address $TOKEN_ACCOUNT
spl-token display $TOKEN_ACCOUNT