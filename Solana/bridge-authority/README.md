# Bridge Authority Sample

This sample demonstrates how to bridge Corda assets to the Solana network using:
- a Bridge Authority (a Corda participant that orchestrates bridging/redemption), and
- a Solana Notary (a Corda notary backed by the [Solana notary program](https://github.com/corda/solana-notary)).

For a detailed explanation of how the bridge authority works, including configuration and the bridging/redemption
flows, see the [Bridge Authority documentation](https://github.com/corda/corda-solana-toolkit/blob/main/bridge-authority/README.md).

The Corda asset in this sample is a Fungible Token created using the Corda
[Token SDK](https://training.corda.net/libraries/tokens-sdk/). This project extends (without modifying the core flow 
logic of) the [StockPayDividends sample CorDapp](../../Tokens/stockpaydividend) and deploys the same party set as 
the original demo, adding the bridging parties.

## Prerequisites

[Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.14/enterprise/cordapps/getting-set-up.html)

A source code of a sibling project `StockPayDividends` sample CorDapp to be checked out.

You need access to Corda Enterprise (via repository access or a developer pack).

Install the [Solana CLI](https://solana.com/docs/intro/installation).

## Running the sample

This repository provides three ways to run the sample, depending on whether you want local Solana or devnet, and 
whether you want a test run or a long-running demo:

### Integration test with local test validator

```bash
./gradlew clean integration-tests:test --tests net.corda.samples.solana.bridge.authority.LocalNetBridgeAuthorityTest
```

This runs the [`LocalNetBridgeAuthorityTest`](integration-tests/src/test/kotlin/net/corda/samples/solana/bridge/authority/LocalNetBridgeAuthorityTest.kt)
integration test, which uses Corda Driver DSL. It deploys and starts local Corda nodes and installs the CorDapps,
starts Solana local test validator, creates Solana accounts and deploys the required Solana program,
bridges a portion of a Corda asset to Solana, then redeems it back.

### Integration test with Solana devnet

From the repository root:
```bash
./gradlew clean integration-tests:test --tests net.corda.samples.solana.bridge.authority.DevNetBridgeAuthorityTest
```

This runs the [`DevNetBridgeTokenTest`](integration-tests/src/test/kotlin/net/corda/samples/solana/bridge/authority/DevNetBridgeAuthorityTest.kt)
integration test, that perform the same operation as the [former test](#integration-test-with-local-test-validator),
except it targets Solana Devnet with pre-defined Solana accounts, in particular wallets of
[Shareholder](https://solscan.io/account/3Wuk6fKtqCzMppikC1S58vK3J5ZbqnbcZXkDhJUHCom7?cluster=devnet#portfolio)
and [Other Shareholder](https://solscan.io/account/AoDHzQwk7s6crxMcC1nptRVHAhd1LASEWbQmAQjCeBKj?cluster=devnet#portfolio).

### Long-running demo deployment with Solana devnet

```bash
./gradlew clean demo:test --tests net.corda.samples.solana.bridge.authority.DevNetBridgeAuthorityDemo
```

This runs the [`DevNetBridgeTokenDemo`](demo/src/test/kotlin/net/corda/samples/solana/bridge/authority/DevNetBridgeAuthorityDemo.kt)
long-standing local Corda nodes and installs the CorDapps,
and targets Solana DevNet with pre-defined Solana accounts (the same as in the [integration test](#integration-test-with-solana-devnet)).
Unlike the previous two tests, this will not run any bridging operation. Web UI to perform bridging may be provided separately.

In order to shut down, you need to shut down Corda node processes externally, e.g. `killall java`.

## Sample parties

The sample assumes the following parties:

* Parties interested in bridging/redeeming a Corda asset to/from Solana:
  * **Shareholder** - holds stock tokens on Corda and bridges/redeems to/from Solana; has a Solana wallet.
  * **Other Shareholder** - same role as **Shareholder**, but for illustration purposes this party initially doesn't 
    have an asset on Corda and gains them via redemption from Solana; has a Solana wallet.
* Parties that facilitate bridging/redemption — the only extra parties beyond those in the original `StockPayDividends` sample:
  * **Bridge Authority** - orchestrates bridging and redemption; has a Solana wallet.
  * **Solana Notary** - a notary responsible for Corda transactions that involve Solana instructions;
  manages custodied Solana wallets.
* Other parties:
  * **Notary** - the regular Corda notary.
  * **Bank** - issues fiat tokens (used by the original sample).
  * **WayneCo** - creates the stock definition/state and issues stock tokens.
  * **Observer** - observes stock lifecycle transactions.

Only **Bridge Authority** and **Solana Notary** require Solana-specific configuration. All other nodes run the
original CorDapp unmodified.

## Sample walkthrough

This sample focuses on bridging and omits dividend distribution (which is the focus of the original `StockPayDividends`
sample).

### Setup

The Corda network (including node configurations) and all required Solana wallets/accounts are created automatically
by the sample integration test:

- Before any bridging occurs, `Shareholder` must own some `AAPL` stock tokens, which are issued by `WayneCo` as part
  of the original `StockPayDividends` sample.
- On Solana, the sample creates and funds wallet accounts for `Bridge Authority`, both shareholders, and the token mint
  representing the Solana equivalent of the Corda `AAPL` stock.
- The `Bridge Authority` node is configured with mappings between Corda parties and Solana wallets, and between Corda
  token types and Solana mints. ATAs are created on demand and do not need to be configured explicitly.

### Bridging

`Shareholder` transfers 60 `AAPL` shares to the `Bridge Authority` using the standard `MoveStock` flow. The bridge
authority automatically escrows the tokens on Corda and mints the equivalent amount into `Shareholder`'s Solana wallet.

### Redemption

`Shareholder` (or any Solana holder of the bridged tokens) transfers SPL tokens to the redemption account. The bridge
authority detects the transfer, burns the SPL tokens on Solana, and releases the corresponding Corda tokens.

Because bridged tokens are fungible and pooled, Solana token transfers are independent of the original Corda holder.
For example:

1. `Shareholder` bridges shares to Solana.
2. `Shareholder` transfers the Solana tokens to `Other Shareholder` on Solana.
3. `Other Shareholder` redeems the shares on Corda by transferring the Solana tokens to the redemption account.
