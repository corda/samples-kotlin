# Solana Integration Samples: Bridging and Delivery vs Payment

This sample demonstrates two patterns for integrating Corda assets with the Solana blockchain:

1. **Bridging** - Transferring Corda assets (states/tokens) to Solana token representations and redeeming them back.
2. **Delivery vs Payment (DvP)** - Atomic cross-ledger settlement where shares transfer on Corda and stablecoin payment settles on Solana in a single coordinated transaction.

Both patterns use:
- A **Solana Notary** - a Corda notary backed by the [Solana notary program](https://github.com/corda/solana-notary) that can execute Solana blockchain transactions as part of Corda transaction finality.
- The Corda [Token SDK](https://training.corda.net/libraries/tokens-sdk/) for asset representation on Corda.

This project extends (without modifying the core flow logic of)
the [StockPayDividends sample CorDapp](../../Tokens/stockpaydividend).

## Pre-Requisites
[Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.13/enterprise/cordapps/getting-set-up.html)

A source code of a sibling project `StockPayDividends` sample CorDapp to be checked out.

You need access to Corda Enterprise (via repository access or a developer pack).
Provide repository URLs for Corda Enterprise JARs in:
- [`repositories.gradle`](repositories.gradle)
- sample [`repositories.gradle`](../../Tokens/stockpaydividend/repositories.gradle).

Install the [Solana CLI](https://solana.com/docs/intro/installation).

## Running the sample

### Bridging: Integration test with Solana Local Validator

From the repository root:
```bash
./gradlew clean integrationtest --tests net.corda.samples.solana.bridging.token.BridgingTokenDriverTest
```

On Windows:
```bash
gradlew.bat clean integrationtest --tests net.corda.samples.solana.bridging.token.BridgingTokenDriverTest
```

This runs the [`BridgingTokenDriverTest`](workflows/src/integrationTest/kotlin/net/corda/samples/solana/bridging/token/BridgingTokenDriverTest.kt)
integration test, which uses Corda Driver DSL. It deploys and starts local Corda nodes and installs the CorDapps,
starts Solana local test validator, creates Solana accounts and deploys the required Solana program,
bridges a portion of a Corda asset to Solana, then redeems it back.

### Bridging: Integration test with Solana Devnet

From the repository root:
```bash
./gradlew clean integrationtest --tests net.corda.samples.solana.bridging.token.DevNetBridgingTokenDriverTest
```

On Windows:
```bash
gradlew.bat clean integrationtest --tests net.corda.samples.solana.bridging.token.DevNetBridgingTokenDriverTest
```

This runs the [`DevNetBridgingTokenDriverTest`](workflows/src/integrationTest/kotlin/net/corda/samples/solana/bridging/token/DevNetBridgingTokenDriverTest.kt)
integration test, that performs the same operation as the [former test](#bridging-integration-test-with-solana-local-validator),
except it targets Solana Devnet with pre-defined Solana accounts, in particular wallets of
 [Issuer](https://solscan.io/account/3Wuk6fKtqCzMppikC1S58vK3J5ZbqnbcZXkDhJUHCom7?cluster=devnet#portfolio)
and [Custodian](https://solscan.io/account/AoDHzQwk7s6crxMcC1nptRVHAhd1LASEWbQmAQjCeBKj?cluster=devnet#portfolio) - switch to `NFT` tab.

### DvP: Integration test with Solana Local Validator

From the repository root:
```bash
./gradlew clean integrationtest --tests net.corda.samples.solana.bridging.token.DvpDriverTest
```

On Windows:
```bash
gradlew.bat clean integrationtest --tests net.corda.samples.solana.bridging.token.DvpDriverTest
```

This runs the [`DvpDriverTest`](workflows/src/integrationTest/kotlin/net/corda/samples/solana/bridging/token/DvpDriverTest.kt)
integration test. It deploys local Corda nodes, starts a Solana local test validator,
creates stablecoin token accounts on Solana, issues stock on Corda, then executes an atomic
delivery-vs-payment where shares move on Corda and stablecoin payment settles on Solana in a single transaction.

### Long-running demo deployment with Solana Devnet

From the repository root:
```bash
./gradlew clean integrationtest --tests net.corda.samples.solana.bridging.token.DevNetBridgingTokenDriverDemo
```

On Windows:
```bash
gradlew.bat clean integrationtest --tests net.corda.samples.solana.bridging.token.DevNetBridgingTokenDriverDemo
```

This runs the [`DevNetBridgingTokenDriverDemo`](workflows/src/integrationTest/kotlin/net/corda/samples/solana/bridging/token/DevNetBridgingTokenDriverDemo.kt)
long-standing local Corda nodes and installs the CorDapps,
and targets Solana DevNet with pre-defined Solana accounts (the same as in the [integration test](#bridging-integration-test-with-solana-devnet)).
Unlike the previous tests, this will not run any bridging operation. Web UI to perform bridging may be provided separately.

In order to shut down, you need to shut down Corda node processes externally, e.g. `killall java`.

## Project Structure

```
bridging-token/
├── contracts/                          # DvP contract and state definitions
│   └── src/main/kotlin/.../dvp/
│       ├── contracts/
│       │   └── SharesPaymentContract.kt  # Enforces DvP rules on-ledger
│       └── states/
│           └── SharesPaymentState.kt     # State representing an atomic DvP
├── workflows/
│   └── src/
│       ├── main/kotlin/.../dvp/flows/
│       │   ├── SharesDvP.kt              # DvP initiator and responder flows
│       │   ├── SolanaService.kt          # Corda service for Solana interactions
│       │   ├── CachedTokenManagement.kt  # ATA creation with caching
│       │   └── Utilities.kt             # Stock queries, decimal scaling
│       └── integrationTest/kotlin/...
│           ├── BridgingTokenDriverTest.kt      # Bridging test (local validator)
│           ├── DevNetBridgingTokenDriverTest.kt # Bridging test (devnet)
│           ├── DevNetBridgingTokenDriverDemo.kt # Long-running demo
│           └── DvpDriverTest.kt                # DvP test (local validator)
├── build.gradle
├── repositories.gradle
└── settings.gradle
```

## Concepts

### Parties

The sample assumes the following parties:

* **Seller** / **Issuer** - holds stock tokens on Corda; has a Solana wallet to receive stablecoin payment (DvP) or bridged tokens (bridging).
* **Buyer** / **Custodian** - purchases shares on Corda by paying stablecoin on Solana (DvP); or receives bridged tokens on Solana and redeems them on Corda (bridging). Has a Solana wallet.
* **Bridge Authority** - a special node that facilitates bridging and redemption; has a Solana wallet. Required only for the bridging pattern.
* **Solana Notary** - a notary that can execute Solana blockchain transactions (mint, burn, SPL transfer) as part of Corda transaction finality. Used by both patterns.
* **Notary** - the regular Corda notary for non-Solana transactions.
* **WayneCo** - creates stock definitions and issues stock tokens.
* **Bank** - issues fiat tokens (used by the original StockPayDividends sample).
* **Observer** - observes stock lifecycle transactions.

---

### Delivery vs Payment (DvP)

Delivery vs Payment enables atomic cross-ledger settlement: shares are delivered on Corda while stablecoin
payment settles on Solana, all within a single Corda transaction. If either leg fails, neither settles.

#### How it works

The DvP pattern leverages the Solana Notary's ability to embed Solana instructions
(called "notary instructions") inside a Corda transaction. When the Solana Notary finalises the Corda
transaction, it also executes the Solana SPL token transfer. Both the Corda state transition and the
Solana payment are committed atomically.

#### DvP State and Contract

**`SharesPaymentState`** captures the full terms of the exchange:
- **Corda side**: the stock symbol, quantity, seller, and buyer
- **Solana side**: the stablecoin mint, buyer's token account (source of payment), seller's token account (destination), payment amount, and token decimals

**`SharesPaymentContract`** enforces the following rules (command: `Agree`):
- No inputs consumed (this is a creation-only state)
- Exactly one `SharesPaymentState` output
- Both seller and buyer must sign
- Exactly one Solana notary instruction, which must be an `SplToken.transferChecked` call
  transferring the agreed stablecoin amount from buyer to seller

#### DvP Flow

```
Seller                          Buyer                         Solana Notary
  │                               │                               │
  │──── 1. Send quote ───────────>│                               │
  │     (quantity, price/share)   │                               │
  │                               │                               │
  │<─── 2. Payment details ──────│                               │
  │     (stablecoin mint,         │                               │
  │      wallet, token account)   │                               │
  │                               │                               │
  │── 3. Build transaction ──>    │                               │
  │   - Move shares to buyer      │                               │
  │   - Create SharesPaymentState │                               │
  │   - Attach Solana transfer    │                               │
  │     instruction               │                               │
  │                               │                               │
  │──── 4. Collect signature ────>│                               │
  │                               │── 5. Verify & sign ──>       │
  │                               │   - Check payment amount      │
  │                               │   - Check token accounts      │
  │                               │   - Check share quantity      │
  │                               │                               │
  │──────── 6. Finality ─────────────────────────────────────────>│
  │                               │       7. Execute Solana       │
  │                               │          SPL transfer         │
  │                               │       8. Notarise Corda tx    │
  │<──────── 9. Done ────────────────────────────────────────────│
  │                               │                               │

Result:
  - Buyer has shares on Corda
  - Seller has stablecoin on Solana
  - Both committed atomically
```

**Step-by-step:**

1. **Seller sends quote** - The seller initiates the `SharesDvP` flow, specifying the stock symbol, quantity, buyer party, and Solana Notary. The seller queries the vault for the stock's price and sends a quote (quantity, price per share) to the buyer.

2. **Buyer provides payment details** - The buyer's responder flow (`SharesDvpResponder`) receives the quote, calculates the total payment, and sends back a `SolanaPayer` containing: the stablecoin token mint address, the buyer's Solana wallet public key, and the buyer's Associated Token Account (ATA).

3. **Seller builds transaction** - The seller constructs a Corda transaction containing:
   - A token move command transferring shares from seller to buyer (using the Tokens SDK)
   - A `SharesPaymentState` output recording the DvP terms
   - A Solana notary instruction: `SplToken.transferChecked` to move stablecoin from the buyer's ATA to the seller's ATA

   The seller also ensures they have an ATA for the stablecoin mint (creating one if needed via `SolanaService`).

4. **Buyer verifies and signs** - The buyer's responder verifies:
   - Exactly one `SharesPaymentState` exists in the transaction
   - The payment amount matches the agreed quote (quantity x price)
   - The buyer's token account and wallet match expectations
   - The stablecoin mint matches the agreed token
   - Fungible tokens (shares) are present and the quantity is correct

5. **Solana Notary finalises** - The Solana Notary processes the Corda transaction. It executes the embedded SPL token transfer instruction on the Solana blockchain, then notarises the Corda transaction. If the Solana transfer fails, the entire Corda transaction is rejected.

#### DvP Configuration

Each participant node requires the following CorDapp configuration:

```yaml
stablecoinTokenMint: <base58_pubkey>    # Solana stablecoin mint address
solanaWalletFile: <path_to_keypair>     # Path to Solana wallet keypair file
solanaRpcUrl: <solana_rpc_endpoint>     # Solana RPC URL
solanaWsUrl: <solana_ws_endpoint>       # Solana WebSocket URL
```

The Solana Notary node requires:
```yaml
notary:
  validating: false
  solana:
    rpcUrl: <solana_rpc_endpoint>
    websocketUrl: <solana_ws_endpoint>
    notaryKeypairFile: <path_to_notary_keypair>
    custodiedKeysDir: <path_for_participant_keys>
```

---

### Bridging

Bridging is transferring an asset from a Corda network to a [token representation](https://solana.com/hi/docs/tokens) on
the Solana blockchain.
The Corda asset is never destroyed (burnt), but rather is locked and can be redeemed back.
This pattern uses a Bridge Authority (an extra Corda participant that orchestrates bridging/redemption)
in addition to the Solana Notary.

#### Setup

The Corda network (including node configurations) and all required Solana wallets/accounts are created automatically
by the sample integration test.

- Before any bridging occurs, the `Issuer` must own some `AAPL` stock tokens.
  These tokens are issued by `WayneCo` as part of the original `StockPayDividends` sample.

- On Solana, the sample creates and funds wallet accounts for: `Bridge Authority`, both parties (Issuer and Custodian), and the token mint
  representing the Solana equivalent of the Corda `AAPL` stock.
  These wallets are accessed by either `Bridge Authority` or `Solana Notary` depending on the operation being performed.

- The `Bridge Authority` node is configured with mappings between Corda and Solana, allowing it to
   map a given Corda participant and asset to the corresponding Solana wallet and token mint.
   Asset mappings can only be configured after the Corda asset (in this example, `AAPL` stock)
   and the relevant Solana wallets/mint exist.
   Explicit token accounts are not required in configuration because Associated Token Accounts (ATAs) are used and created on demand.
   For a detailed explanation of the configuration entries for `Bridge Authority` and `Solana Notary`,
   see the [Bridging Toolkit Documentation](TBD).

Only `Bridge Authority` and `Solana Notary` require Solana-specific configuration. All other nodes run the original CorDapp unmodified.

#### Bridging Flow

1. **Issuer moves tokens to Bridge Authority (Corda)**

   `Issuer` transfers shares (`60 AAPL` shares) to the Bridge Authority by running `MoveStock` flow
   with `Bridge Authority` as the recipient. This is a standard Corda (Tokens SDK) action -
   the asset is moved under Corda's rules before any Solana action occurs.

2. **Bridge Authority prepares the bridged representation**

   Bridge Authority performs bridging on behalf of Issuer:
   - Locks the received shares under a confidential identity controlled by Bridge Authority.
   - Creates a "token-equivalent" representation that includes required Solana metadata (mint, destination wallet),
     looked up from the node configuration.
   - Ensures the recipient (`Issuer`) has an Associated Token Account (ATA) for that mint;
     if not, submits a Solana transaction to create it.
   - Changes the notary for the "token-equivalent" representation to the Solana Notary
     (bridging-related transactions are notarised by the Solana Notary, not the regular Corda Notary).
   - Builds a Corda transaction that consumes the "bridged representation" state
     and includes the required instruction for minting on Solana.
     The Corda transaction is submitted to the Solana Notary.

3. **Solana Notary verifies and mints on Solana**

   The Solana Notary verifies the validity of the Corda transaction - checks that the amount to be minted on Solana
   matches the amount locked on Corda, then it submits the Solana mint transaction.
   The Solana transaction is signed via Bridge Authority's Solana wallet custodied by Solana Notary.
   The Solana Notary finalizes the Corda transaction only if the Solana mint succeeds
   and the Solana program detects no double-mint.
   After finality, the bridged amount is represented as tokens owned by the `Issuer`'s Solana wallet.

#### Redemption Flow

1. **Redemption initialization on Solana**

   The Issuer transfers some Solana tokens to a designated wallet (an Associated Token Account for the token mint).
   `Bridge Authority` monitors the redemption token accounts and is notified when a transfer occurs.
   `Bridge Authority` determines (from node configuration) which Corda asset type the Solana mint corresponds to
   and constructs a Corda transaction that creates a new Corda "token-equivalent" representation state to be redeemed.
   The state contains Solana metadata: the token mint, the token account (ATA), and the amount to burn.
   The transaction contains an instruction for the Solana Notary to burn tokens on Solana from the redemption wallet (ATA).
   `Bridge Authority` submits the transaction to the Solana Notary for notarisation.

2. **Solana Notary verifies and burns on Solana**

   The Solana Notary verifies the Corda transaction is valid - checks that the amount to be burned on Solana
   matches the amount to be unlocked on Corda, then it submits a Solana burn transaction.
   The Solana transaction is signed using the custodied redemption wallet for `Issuer`.
   The Solana Notary finalizes the Corda transaction only if the Solana burn succeeds.

3. **Bridge Authority releases the Corda asset**

   After the burn on Solana is finalized, the `Bridge Authority` uses the "token-equivalent" representation
   to carry on the redemption process.
   `Bridge Authority` updates the "token-equivalent" representation so that subsequent Corda-only steps
   are notarised by the regular Corda Notary rather than the Solana Notary.
   `Bridge Authority` performs Corda token selection and builds a Corda transaction that:
   - moves the redeemed shares to the Issuer (via regular Corda Fungible Token move)
   - consumes the "token-equivalent" representation state

   This transaction is submitted to the regular Notary for verification and finality.
   At this point, redemption is complete:
   - the Solana tokens have been burned, and
   - the corresponding Corda stock tokens have been transferred to the Issuer.

## FAQ

**Why are there two notaries?**

Bridging and DvP are designed to be minimally invasive to the existing Corda network. Existing participants
and the existing notary can continue running the unmodified Stock CorDapp. The Solana Notary is added
specifically for transactions that require Solana blockchain interaction (bridging, redemption, and DvP settlement).
For bridging, the Bridge Authority is also added. Regular Corda transactions continue to use the standard Notary.

**How does locking work (bridging)?**

`Bridge Authority` holds the bridged assets on Corda rather than destroying them.
Assets moved for bridging are transferred to a confidential identity belonging to `Bridge Authority`,
which keeps a redeemable pool of assets on Corda.
Corda participants trust the Bridge Authority to follow the authorized bridging/redeeming flows and not bypass
constraints. It's likely the asset issuer is also the `Bridge Authority`.

**During redemption, which Corda tokens are transferred back?**

Because the bridged asset is a fungible token, redemption does not require returning
the exact same token instances that were originally bridged. Instead, bridged assets form a pooled inventory
held by `Bridge Authority` and any current holder of the bridged Solana token can redeem on Corda.
This means Solana token transfers are independent of the original Corda holder.

Example:
  - `Issuer` bridges shares
  - `Issuer` transfers Solana tokens to `Custodian` on Solana
  - `Custodian` redeems those shares on Corda by transferring the Solana tokens to the redemption account

**How does atomicity work in DvP?**

The DvP transaction is atomic because both the Corda share transfer and the Solana stablecoin payment are
embedded in a single Corda transaction. The Solana Notary executes the Solana SPL token transfer as part of
notarisation. If the Solana transfer fails, the Corda transaction is not notarised and the shares are not moved.
Neither party bears settlement risk.

**What happens if a node restarts during a flow?**

Both bridging and DvP flows are designed to be resilient to Corda node restarts, transient networking failures,
and temporary loss of Solana RPC connectivity. The DvP implementation uses cached ATA management
(`CachedTokenManagement`) that handles idempotent ATA creation, ensuring flows can safely resume after restart.
