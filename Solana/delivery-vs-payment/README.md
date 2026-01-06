# Solana DvP Sample CorDapp 

This CorDapp provides an example to perform Delivery vs Payment ("DvP") transaction of an asset (a stock shares) 
on Corda network for a payment with a stablecoin on Solana network (SPL Token).

## Concepts

The DvP is an atomic swap between two participants. They agree on the delivery of an asset and the payment for it. 
A payment is settled using a stablecoin on Solana. Corda Notary node acts as intermediary (on behalf of a payer)
and records the transaction in Corda Notary Program (on Solana). This allows to perform a swap on both networks in atomic manner,
and avoids any double transfer of stablecoins.
Corda asset is expressed as a Fungible Token utilizing the [Token SDK](https://github.com/corda/token-sdk), 
however it could be any regular Corda state.

### Flows

There are two flows that we'll use in this example.

Prerequisite: Solana stablecoin needs to be created, some amount needs to be transferred to ATA account of the buyer.
The sample test `[StockDvpDriverTest.kt](workflows/src/integrationTest/kotlin/net/corda/samples/solana/dvp/StockDvpDriverTest.kt)`
used Corda tooling to programmatically start a Solana local validator and to create Solana accounts/programs.
A buyers ATA is created by a helper Corda flow `CreateAtaFlow` and then funded with stablecoins.

1. Create and issue a Corda state using `CreateAndIssueStock`, a state will be used for 'Delivery' part in DvP.

2. Initiate the DvP through `SharesDvP`.
    DvP is initiated by the seller, who offers an asset for sale and communicates the price to the buyer.

    The buyer accepts the price and provides the Solana account details from which the payment will be made. 

    The seller builds a Corda transaction to deliver the asset (in this sample: moving a Corda token to the buyer).
    The transaction also includes a `StockPaymentContract`. This is not an on-ledger payment on Corda,
    it is a receipt/record of what was agreed on Corda to be paid in stablecoins.
    Including this information allows the buyer to verify and approve the Corda transaction.
    The seller adds the stablecoin payment details (amount, the seller’s destination account and the buyer-provided details)
    to an instruction for Corda Notary. This will be executed by Corda Notary.
    The seller creates ATA for payment receival, if an account doesn't exist yet.

    The seller sends the transaction to the buyer to sign. 
    The buyer verifies that the `StockPaymentState` matches what was agreed (for example TokenMint decimals) and then signs the transaction.

    The seller submits the transaction to Corda Notary for notarisation.
    The Notary performs the Solana SPL token transfer from the buyer’s account to the seller’s account according to submitted instruction,
    acting on behalf of the buyer. The Notary adds Corda Program instruction to record Corda transaction on Solana program. 
    If the payment succeeds or the Corda Program already recorded such transaction,
    the Notary notarises the Corda transaction effectively approving delivery of the asset to the buyer.

### Configuration

Each party keeps configuration file with Solana account. Flow shares own account data for counterparty to make payment,
and also can verify if payment is performed for the same token (mint).
The CordApp configuration file contains the following Solana account setting for a participant (Corda party):

``solanaWalletFile`` - the file path with public-private key pair of the participant's wallet
``solanaTokenMint`` - public key of the mint account of SPL token
``solanaRpcUrl`` - Solana RPC URL to obtain account info; e.g.: ``http://127.0.0.1:8899``
``solanaWsUrl`` - Solana WS URL, not used by a flow in this sample, however needed by underlying client, for future use; e.g.: ``ws://127.0.0.1:8900``

Public keys are written in Base58 format.

## Pre-Requisites
[Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.13/enterprise/cordapps/getting-set-up.html)

Change values in ``../constant.properties`` ``cordaVersion`` and ``cordaCoreVersion`` to the latest 4.14 Snapshot versions.

### Resiliency

TODO