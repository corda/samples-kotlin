# Atomic Corda-Solana Delivery-vs-Payment Sample

This CorDapp provides an example atomic DvP ("delivery vs payment") transaction of an asset (in this case shares in a stock)
on a Corda network for payment using a Solana stablecoin (SPL Token).

## Prerequisites

- [Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.14/enterprise/cordapps/getting-set-up.html)
  with access to Corda Enterprise (via repository access or a developer pack).
- [Solana CLI tools](https://solana.com/docs/intro/installation) installed.

## Running the sample

Navigate to the `delivery-vs-payment` folder and run:

```bash
./gradlew build
```

or on Windows:

```bash
gradlew.bat build
```

This compiles the CorDapp and runs the integration test in
`workflows/src/test/kotlin/net/corda/samples/solana/dvp/StockDvpDriverTest.kt`. The test uses the Corda Driver DSL 
to start real Corda nodes and a local Solana test validator (via `SolanaNotaryExtension`), then executes a complete 
DvP between a seller and a buyer.

## Architecture

The DvP is an atomic swap: the seller delivers Corda stock tokens and the buyer pays in a Solana stablecoin. 
Atomicity is guaranteed by the Solana notary — it only finalises the Corda transaction (delivering the stock) if it 
can also execute the SPL token transfer (the payment) within the same Solana transaction. Neither leg can succeed 
without the other.

The Solana notary custodies the Solana private keys for each Corda participant. This allows it to sign the SPL token 
transfer on the buyer's behalf at notarisation time, without the buyer needing to interact with Solana directly.

`SharesPaymentContract` enforces on-ledger that the Solana instruction embedded in the transaction exactly encodes 
the Solana `transferChecked` instruction described by the `SharesPaymentState`. This means the buyer's signature on the 
Corda transaction is also their consent to the specific payment, preventing a malicious seller from substituting a 
different instruction.

In this sample the Corda asset is expressed as a Fungible Token via the
[Token SDK](https://github.com/corda/token-sdk), but any regular Corda state could be used.

## States

- **`StockState`**: An evolvable token type representing the stock being traded. Records the issuer, ticker symbol, 
  currency, and price per share.

- **`SharesPaymentState`**: A receipt that records the agreed payment terms — the quantity of stock being delivered, 
  the stablecoin mint, the Solana account addresses of both parties, and the stablecoin amount. 
  `SharesPaymentContract` uses this to verify that the embedded Solana notary instruction matches what both parties 
  signed.

## Flows

There are two flows `SharesDvP` and `SharesDvpResponder` to perform DvP.

Prerequisite: A buyer owns an amount of stablecoins on Solana.

1. Create and issue a Corda state using `CreateAndIssueStock`. This state is part of the "delivery".

2. Initiate the DvP through `SharesDvP`.
   DvP is initiated by the seller, who offers an asset for sale and communicates the price to the buyer.

3. The buyer accepts the price and provides the Solana account details from which the payment will be made.

4. The seller builds a Corda transaction to deliver the asset.
   The transaction also includes a `SharesPaymentContract`. This is not an on-ledger payment on Corda,
   but rather it is a receipt/record of what was agreed on Corda to be paid in stablecoins.
   Including this information allows the buyer to verify and approve the Corda transaction.
   The seller adds the stablecoin payment details (amount, the seller's destination account and the buyer-provided details)
   as a Solana notary instruction, which will be executed by the Solana notary node in the same Solana transaction as the notarisation.
   The seller creates their ATA for payment receipt, if such an account didn't already exist for the stablecoin.
   The seller sends the Corda transaction to the buyer to sign.

5. The buyer verifies that the transaction data matches what was agreed (for example, a quantity of the asset to exchange
   and the stablecoin amount to pay) and then signs the transaction.

6. The seller submits the transaction to Corda Notary for notarisation.

7. The notary performs the SPL token transfer of the stablecoin from the buyer's account to the seller's account
   according to the submitted Solana notary instruction.
   The Solana transaction also contains a Corda Program instruction to record Corda states.
   If there is sufficient stablecoin in the buyer's account and the Corda state hasn't been spent already
   then both the payment and delivery of the asset succeed atomically.

## Configuration

### CorDapp config — per node (`<NODE_ROOT_DIR>/cordapps/config/workflows-1.0.conf`)

| Key                   | Description                                                                                                                       |
|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `solanaWalletFile`    | Path to the node's Solana [file-system wallet](https://docs.solanalabs.com/cli/wallets/file-system). This is used to create ATAs. |
| `stablecoinTokenMint` | Base-58 public key of the stablecoin SPL token mint                                                                               |
| `solanaRpcUrl`        | Solana RPC endpoint (`http://127.0.0.1:8899` for the local validator; `https://api.devnet.solana.com` for devnet)                 |
| `solanaWebsocketUrl`  | Corresponding WebSocket URL (`ws://127.0.0.1:8900` for the local validator; `wss://api.devnet.solana.com` for devnet)             |

### Notary config

The Solana-specific notary configuration fields (`node.conf`, under `notary.solana`) are documented
[here](https://docs.r3.com/en/platform/corda/4.14/enterprise/node/setup/corda-configuration-fields.html#notary).
