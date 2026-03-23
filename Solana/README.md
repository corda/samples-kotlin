# Corda Solana Samples

These samples showcase integrations of Corda with the [Solana blockchain](https://solana.com/), enabling atomic
cross-chain workflows between the two networks.

Atomicity is provided by the
[Solana notary](https://docs.r3.com/en/platform/corda/4.14/enterprise/notary/solana-notary.html): a specialised
Corda notary that executes Solana transactions as an integral part of Corda notarisation. Because both operations
are committed in the same Solana transaction, they either both succeed or both fail — with no possibility of one
leg completing without the other.

## Samples

* [**Delivery-vs-Payment**](delivery-vs-payment): A seller transfers Corda stock tokens to a buyer and receives
  Solana stablecoin payment in return — atomically, in a single notarisation.

* [**Bridge Authority**](bridge-authority): Demonstrates how Solana bridging can be added to an existing Corda
  network without modification (in this case the [stock pay dividend sample](../Tokens/stockpaydividend)). A
  [Bridge Authority](https://github.com/corda/corda-solana-toolkit/tree/main/bridge-authority) node orchestrates
  bridging on behalf of token holders, locking Corda tokens in a pool while a
  [Solana notary](https://docs.r3.com/en/platform/corda/4.14/enterprise/notary/solana-notary.html#configuration)
  atomically mints equivalent SPL tokens — and burns them to release the original Corda tokens on redemption.

## Prerequisites

[Corda Enterprise 4.14](https://docs.r3.com/en/platform/corda/4.14/enterprise/release-notes-enterprise.html#new-features-enhancements-and-restrictions)
(or later) and [Solana CLI tools](https://solana.com/docs/intro/installation) are required.

## Devnet

The samples can also be run against [Solana devnet](https://solscan.io/account/notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY?cluster=devnet)
where [`devnet-sample-notary-keypair.json`](devnet-sample-notary-keypair.json) has been provisioned for testing
purposes.

> [!IMPORTANT]
> This keypair is shared publicly and may be used by anyone. It is provided for convenience only, with no guarantees
> of availability, balance, or continuity. R3 accepts no responsibility for any issues arising from its use.

> [!CAUTION]
> Never use this keypair on mainnet. It is publicly known and any funds sent to it can be accessed by anyone.

There needs to be sufficient SOL in this account for notarisation to work. If you start seeing insufficient SOL
errors in the notary logs, the notary account will need topping up. This can be done using a
[faucet](https://solana.com/developers/guides/getstarted/solana-token-airdrop-and-faucets) or by running:

```bash
solana airdrop 5 devnet-sample-notary-keypair.json
```

More information about whitelisting notary keys and the Solana notary program in general can be found
[here](https://github.com/corda/solana-notary).
