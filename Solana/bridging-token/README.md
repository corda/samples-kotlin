# Stock Bridging To Solana Sample

This sample demonstrates how to bridge Corda assets (states/tokens) to the Solana network using:
- a Bridge Authority (an extra Corda participant that orchestrates bridging/redemption), and
- a Solana Notary (a Corda notary backed by the [Solana notary program](https://github.com/corda/solana-notary)).

The Corda asset in this sample is a Fungible Token created using 
the Corda [Token SDK](https://training.corda.net/libraries/tokens-sdk/),
but the same pattern can be applied to other Corda states.
This project extends (without modifying the core flow logic of) 
the [StockPayDividends sample CorDapp](../../Tokens/stockpaydividend)
and deploys the same party set as the original demo and adds bridging parties.

## Pre-Requisites
[Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.13/enterprise/cordapps/getting-set-up.html)

A source code of a sibling project `StockPayDividends` sample CorDapp to be checked out.

You need access to Corda Enterprise (via repository access or a developer pack). 
Provide repository URLs for Corda Enterprise JARs in:
- [`repositories.gradle`](repositories.gradle) 
- sample [`repositories.gradle`](../../Tokens/stockpaydividend/repositories.gradle).

Install the [Solana CLI:](https://solana.com/docs/intro/installation).

## Running the sample

This repository provides three ways to run the sample, depending on whether you want local Solana or Devnet, 
and whether you want a test run or a long-running demo:

### Integration test with Solana Local Validator

From the repository root:
```bash
./gradlew clean integrationtest --tests net.corda.samples.solana.bridging.token.BridgingTokenDriverTest
```

On Windows:
```bash
gradlew.bat  clean integrationtest --tests net.corda.samples.solana.bridging.token.BridgingTokenDriverTest
```

This runs the [`BridgingTokenDriverTest`](workflows/src/integrationTest/kotlin/net/corda/samples/solana/bridging/token/BridgingTokenDriverTest.kt)
integration test, which uses Corda Driver DSL. It deploys and starts local Corda nodes and installs the CorDapps, 
starts Solana local test validator, creates Solana accounts and deploys the required Solana program,
bridges a portion of a Corda asset to Solana, then redeems it back.

### Integration test with Solana Devnet

From the repository root:
```bash
./gradlew clean integrationtest --tests net.corda.samples.solana.bridging.token.DevNetBridgingTokenDriverTest
```

On Windows:
```bash
gradlew.bat  clean integrationtest --tests net.corda.samples.solana.bridging.token.DevNetBridgingTokenDriverTest
```

This runs the [`DevNetBridgingTokenDriverTest`](workflows/src/integrationTest/kotlin/net/corda/samples/solana/bridging/token/DevNetBridgingTokenDriverTest.kt)
integration test, that perform the same operation as the [former test](#Integration-test-with-Solana-Local-Validator),
except it targets Solana Devnet with pre-defined Solana accounts, in particular wallets of
 [Shareholder](https://solscan.io/account/3Wuk6fKtqCzMppikC1S58vK3J5ZbqnbcZXkDhJUHCom7?cluster=devnet#portfolio)
and [Other Shareholder](https://solscan.io/account/AoDHzQwk7s6crxMcC1nptRVHAhd1LASEWbQmAQjCeBKj?cluster=devnet#portfolio) - switch to `NFT` tab.

### Long-running demo deployment with Solana Devnet

From the repository root:
```bash
./gradlew clean integrationtest --tests net.corda.samples.solana.bridging.token.DevNetBridgingTokenDriverDemo
```

On Windows:
```bash
gradlew.bat  clean integrationtest --tests net.corda.samples.solana.bridging.token.DevNetBridgingTokenDriverDemo
```

This runs the [`DevNetBridgingTokenDriverDemo`](workflows/src/integrationTest/kotlin/net/corda/samples/solana/bridging/token/DevNetBridgingTokenDriverDemo.kt)
long-standing local Corda nodes and installs the CorDapps,
and targets Solana DevNet with pre-defined Solana accounts (the same as in the [integration test](#Integration-test-with-Solana-Devnet)). 
Unlike the previous two tests, this will not run any bridging operation. Web UI to perform bridging may be provided separately. 

In order to shut down, you need to shut down Corda node processes externally, e.g. `killall java`.


## Concepts

Bridging is transferring an asset from a Corda network to a [token representation](https://solana.com/hi/docs/tokens) on
the Solana blockchain.
The Corda asset is never destroyed (burnt), but rather is locked and can be redeemed back.
This sample demonstrates how to bridge Corda assets to the Solana network using
a Bridge Authority (an extra Corda participant that orchestrates bridging/redemption), and
a Solana Notary (which is the gateway to the Solana blockchain).
The Corda asset in this sample is a Fungible Token created using the Corda Tokens SDK,
but the same pattern can be applied to other Corda states.
As an example, this project extends the `StockPayDividends` sample CorDapp, without modifying the core flow logic,
and deploys the same party set as the original demo but adds bridging parties.

The sample assumes the following parties:
* Parties interested in bridging/redeeming a Corda asset to/from Solana:
  * **Shareholder** - holds stock tokens on Corda and bridges/redeems to/from Solana; has Solana wallet.
  * **Other Shareholder** - same role as **Shareholder**, however for illustration purposes
  this party initially doesn't have an asset on Corda, and gains them via redemption from Solana; has Solana wallet.
* Parties facilitate bridging/redemption - the only extra parties beyond ones in original `StockPayDividends` sample:
  * **Bridge Authority** - a special node that facilitates bridging and redemption; has Solana wallet.
  * **Solana Notary** - a notary responsible for any Corda transactions that involves bridging/redemption to/from Solana;
  manages Solana wallets.
* Parties indirectly involved bridging/redemption:
  * **Notary** - the regular Corda notary.
* Parties that do not participate in bridging, 
  but remain necessary for the Corda assets issuance workflows (used by the original sample):
  * **Bank** - issues fiat tokens.
  * **WayneCo** - creates the stock definition/state and issues stock tokens.
  * **Observer** - observes stock lifecycle transactions.

### Flows

This sample focuses on bridging, and omits dividend distribution (which is the focus of the original `StopPayDivided` sample).

#### Setup:

The Corda network (including node configurations) and all required Solana wallets/accounts are created automatically 
by the sample integration test.

- Before any bridging occurs, the`Shareholder` must own some `AAPL` stock tokens.
  These tokens are issued by `WayneCo` as part of the original `StopPayDivided` sample.

- On Solana, the sample creates and funds wallet accounts for: `Bridge Authority`, both shareholders, and the token mint
  representing the Solana equivalent of the Corda `AAPL` stock.
  These wallets are accessed by either `Bridge Authority` or `Solana Notary`depending on the operation being performed.

- The `Bridge Authority` node is configured with mappings between Corda and Solana, allowing it to translate:
   map a given Corda participant and asset to the corresponding Solana wallet and token mint
   Asset mappings can only be configured after the Corda asset (in this example, `AAPL` stock) 
   and the relevant Solana wallets/mint exist.
   Explicit token accounts are not required in configuration because Associated Token Accounts (ATAs) are used and created on demand.
   For a detailed explanation of the configuration entries for `Bridge Authority` and `Solana Notary`, 
   see the [Bridging Toolkit Documentation](TBD).

Only `Bridge Authority` and `Solana Notary require Solana-specific configuration. All other nodes run the original CorDapp unmodified.

#### Bridging Flow:

1. `Shareholder` moves tokens to Bridge Authority (Corda)
`Shareholder` transfers shares (`60 AAPl` shares) to the Bridge Authority by running `MoveStock` flow 
with `Bridge Authority` as the recipient, this is a standard Corda (Tokens SDK) action - 
the asset is moved under Corda’s rules before any Solana action. 
Tokens SDK action: the asset is moved under Corda’s rules before any Solana action occurs.

2. `Bridge Authority` prepares the bridged representation
Bridge Authority performs bridging on behalf of Shareholder:
  - Locks the received shares under a confidential identity controlled by Bridge Authority
    It internally locks the shares received by moving it under Corda Confidential Identity and creates a Corda token 
    equivalent state with additional Solana information.
  - Creates a “token-equivalent” representation that includes required Solana metadata (mint, destination wallet), 
    the metadata is looked up from the node configuration
  - Ensures the recipient (`Shareholder`) has an Associated Token Account (ATA) for that mint;
    if not, submits a Solana transaction to create it.
  - Changes a notary for the “token-equivalent” representation to the Solana Notary 
    (bridging-related transactions are notarised by the Solana Notary, not the regular Corda Notary)
    `Bridge Authority` then builds a Corda transaction that consumes the “bridged representation” state
    and includes the required instruction for minting on Solana.
    The Corda transaction is submitted to Solana Notary.

3. Solana Notary verifies and mints on Solana
The Solana Notary verifies the validity of the Corda transaction - checks that the amount to be minted on Solana
matches the amount locked on Corda, then it submits the Solana mint transaction. 
The Solana transaction is sign via Bridge Authority’s Solana wallet custodied by Solana Notary.
The Solana Notary finalizes the Corda transaction only if the Solana mint succeeds 
and the Solana program detects no double-mint.
After finality, the bridged amount is represented as tokens owned by the `Shareholder`’s Solana wallet.

#### Redemption Flow:

1. Redemption initialization on Solana
    The Shareholder transfers some Solana tokens to a designated wallet, 
    more specifically to a Associated Token Account (ATA) for token mint’s.
   `Bridge Authority` monitors the redemption token accounts and is notified when a transfer occurs.
   `Bridge Authority` determines (from node configuration) which Corda asset type / token type Solana mint corresponds 
     and constructs a Corda transaction that  creates a new Corda “token-equivalent” representation state to me redeemed. 
     The state contains Solana metadata the token mint, the token account (ATA) and the amount to burn.
     The transaction contains instruction for Solana Notary to burn tokens on Solana from the redemption wallet (ATA).
   `Bridge Authority` submits the transaction to the Solana Notary or notarisation

2. Solana Notary verifies and burns on Solana
   The Solana Notary verifies the Corda transaction is valid - checks that the amount to be burn on Solana
   matches the amount to unlocked on Corda, then it submits a Solana bur transaction.
   The Solana transaction is signed using custodied redemption wallet for `Shareholder`.
   The Solana Notary finalizes the Corda transaction only if the Solana burn succeeds.
   
3. Bridge Authority releases the Corda asset
   After the burn on Solana is finalized the `Bridge Authority` contains the “token-equivalent” representation,
   that carry on redemption process.
   `Bridge Authority` updates the “token-equivalent” representation so that subsequent Corda-only steps 
   are notarised by the regular Corda notary (Notary) rather than the Solana Notary.
   `Bridge Authority` performs Corda token selection and builds a Corda transaction that:
   - moves the redeemed shares to the Shareholder (via regular Corda Fungible Token move)
   - consumes the “token-equivalent” representation state
   This transaction is submitted to the regular Notary for verification and finality.
   At this point, redemption is complete:
   - the Solana tokens have been burned, and 
   - the corresponding Corda stock tokens have been transferred to the Shareholder.

### FAQ 

- Why there are two notaries?
Bridging is designed to be minimally invasive to the existing Corda network. Existing participants 
and the existing notary can continue running the unmodified Stock CorDapp. Bridging is enabled by adding the `Bridge 
Authority` and the `Solana Notary`. Only bridging and redemption flows require the `Solana Notary`,
hence a need for the second notary in the Corda network.

- How locking works?
`Bridge Authority` holds the bridged assets on Corda rather than destroying it.
Assets moved for bridging are transferred to a confidential identity belonging to `Bridge Authority`,
which keeps a redeemable pool of assets on Corda.
Corda participants trust the Bridge Authority to follow the authorized bridging/redeeming flows and not bypass 
constraints. It's likely the asset issuer is also the `Bridge Authority`.

- During redemption, which Corda tokens are transferred back?
Because the bridged asset is a fungible token, redemption does not require returning 
the exact same token instances that were originally bridged. Instead, bridged assets form a pooled inventory 
held by `Bridge Authority` and any current holder of the bridged Solana token can redeem on Corda.
This means Solana token transfers are independent of the original Corda holder.

Example:
  - `Shareholder` bridges shares
  - `Shareholder` transfers Solana tokens to `Other Shareholder` on Solana
  - `Other Shareholder `redeems those shares on Corda by transferring the Solana tokens to the redemption account

- When is bridging done?
There may be a delay between the `Shareholder` moving tokens to `Bridge Authority` on Corda
and the Solana Notary minting the tokens on Solana. However, the process is designed to be resilient to Corda node 
restarts, transient networking failures and temporary loss of Solana RPC connectivity.
