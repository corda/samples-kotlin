# Solana DvP Sample CorDapp 

This CorDapp provides an example to perform Delivery vs Payment ("DvP") transaction of an asset (a stock shares) 
on Corda network for a payment with a stablecoin on Solana network (SPL Token).

## Concepts

The DvP is atomic: two participants agree on the delivery of an asset and the payment for it. 
The payment is settled using a stablecoin on Solana by the Notary node. Notary acts on behalf of a payer. 
Corda asset is expressed as a Fungible Token utilizing the [Token SDK](https://github.com/corda/token-sdk), 
however it could be any regular Corda state.

### Flows

There are two flows that we'll primarily use in this example that you'll be building of.

Prerequisite: Solana account needs to be created and contains enough token amounts.

1. Create and issue a Corda state using `CreateAndIssueStock`, a state will be used for 'Delivery' part in DvP.

2. Initiate the DvP through `StockDvP`.
    DvP is initiated by the seller, who offers an asset for sale and communicates the price to the buyer.

    The buyer accepts the price and provides the Solana account details from which the payment will be made. 

    The seller builds a Corda transaction to deliver the asset (in this sample: moving a Corda token to the buyer).
    The transaction also includes a `StockPaymentContract`. This is not an on-ledger payment on Corda,
    it is a receipt/record of what was agreed on Corda to be paid on Solana.
    Including this information allows the buyer to verify and approve the Corda transaction.

    The seller sends the transaction to the buyer to sign. 
    The buyer verifies that the `StockPaymentState` matches what was agreed and then signs the transaction.

    The buyer adds the Solana payment details (the seller’s destination account and the buyer-provided details) 
    to the Notary instruction, and submits the Corda transaction for notarisation.
    The Notary performs the Solana SPL token transfer from the buyer’s account to the seller’s account,
    acting on behalf of the buyer. If the payment succeeds, the Notary notarises the Corda transaction 
    effectively approving delivery of the asset to the buyer.

### Configuration

Each party keeps configuration file with Solana account. Flow shares own account data for counterparty to make payment,
and also can verify if payment is performed for the same token (mint).
The CordApp configuration file contains the following Solana account setting for a participant (Corda party):

``solanaWalletAccount`` - public key of the participant's wallet account
``solanaTokenAccount`` - public key of SPL token account for payment
``solanaTokenMint`` - public key of the mint account of SPL token;
``solanaTokenMintDecimals`` - numeric value, CordApp uses Solana checked token transfer that requires providing decimal places

Public keys are written in Base58 format.

## Pre-Requisites
[Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.13/enterprise/cordapps/getting-set-up.html)

Change values in ``../constant.properties`` ``cordaVersion`` and ``cordaCoreVersion`` to the latest 4.14 Snapshot versions.
