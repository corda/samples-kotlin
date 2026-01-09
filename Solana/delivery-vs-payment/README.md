# Atomic Corda-Solana DvP Sample

This CorDapp provides an example atomic DvP ("delivery vs payment") transaction of an asset (in this case shares in a stock) 
on a Corda network for payment using a Solana stablecoin (SPL Token).

## Pre-Requisites
[Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.13/enterprise/cordapps/getting-set-up.html)

Access to Corda Enterprise repository or builds.
Change values in ``../constant.properties``, ``cordaOsVersion`` and ``cordaEnterpiseVersion`` to the appropriate version (minimum 4.14).

Install [Solana](https://solana.com/docs/intro/installation).

## Running the sample

Navigate to root folder of the project and run
``bash
./gradlew build
``
or on Windows
``bash
gradlew.bat build
``
The command deploys runs a test from ``./workflows/src/integrationTest/kotlin/net/corda/samples/solana/dvp/StockDvpDriverTest.kt`` file.
The test is written in Corda Driver DSL. It deploys locally Corda nodes, starts Solana local test validator (or uses devnet),
and runs a DvP transaction between two Corda Nodes and Solana. The test also creates Solana accounts and deploy Corda Program.

## Concepts

The DvP is an atomic swap between the two participants. They agree on the delivery of an asset and the payment for it. 
A payment is settled using a stablecoin on Solana. The Corda notary acts as an intermediary (on behalf of a payer)
and records the transaction on the Solana blockchain using the [Corda Notary Program](https://github.com/corda/solana-notary/).
This allows the swap to be performed atomically across both networks and prevents any double spends of either transfers.
In this example the Corda asset is expressed as a Fungible Token utilizing the [Token SDK](https://github.com/corda/token-sdk), 
however it could be any regular Corda state.

### Flows

There are two flows ``SharesDvp``and ``SharesDvpResponder`` to perform DvP. 

Prerequisite: A buyer owns an amount of stablecoins on Solana.

1. Create and issue a Corda state using `CreateAndIssueStock`. This state is part of the "delivery".

2. Initiate the DvP through `SharesDvP`.
   DvP is initiated by the seller, who offers an asset for sale and communicates the price to the buyer.

3. The buyer accepts the price and provides the Solana account details from which the payment will be made. 

4. The seller builds a Corda transaction to deliver the asset.
   The transaction also includes a `StockPaymentContract`. This is not an on-ledger payment on Corda,
   but rather it is a receipt/record of what was agreed on Corda to be paid in stablecoins.
   Including this information allows the buyer to verify and approve the Corda transaction.
   The seller adds the stablecoin payment details (amount, the seller’s destination account and the buyer-provided details)
   as a Solana notary instruction, which will be executed by the Solana notary node in the same Solana transaction as the notarisation.
   The seller creates their ATA for payment receipt, if such an account didn't already exist for the stablecoin.
   The seller sends the Corda transaction to the buyer to sign. 

5. The buyer verifies that the transaction data matches what was agreed (for example, a quantity of the asset to exchange 
   and the stablecoin amount to pay) and then signs the transaction.

6. The seller submits the transaction to Corda Notary for notarisation.

7. The notary performs the SPL token transfer of the stabelcoin from the buyer’s account to the seller’s account 
   according to the submitted Solana notary instruction. 
   The Solana transaction also contains a Corda Program instruction to record Corda states.
   If there is sufficient stablecoin in the buyer's account and the Corda state hasn't been spent already
   then both the payment and delivery of the asset succeed atomically.

### Configuration

Each Corda node require settings for the Cordapp workflows to connect Solana and access to Solana Wallet file.
The CorDapp configuration file (``<NODE_ROOT_DIR>/cordapps/config/workflows-1.0.conf``) contains 
the following Solana account settings for the participant (Corda party):

- ``solanaWalletFile`` the participant's Solana [file-system wallet](https://docs.solanalabs.com/cli/wallets/file-system),
used to auto-create create ATA for storing stabelcoins
- ``stablecoinTokenMint`` public key of the stablecoin mint account of SPL token
- ``solanaRpcUrl`` URL of the RPC provider for interacting with the blockchain; if you are using the test validator 
then this will be `http://127.0.0.1:8899`, if you want to use devnet then the URL is `https://api.devnet.solana.com`
- ``solanaWsUrl`` - The corresponding websocket URL of the RPC provider. `ws://127.0.0.1:8900` for the test validator 
and `wss://api.devnet.solana.com` for devnet

Corda notary requires additional settings in the node configuration (``node.conf`` file). 
They are grouped under ``solana`` sub-entry of ``notarty``:

- ``rpcUrl`` URL of the RPC provider for interacting with the blockchain. If you are using the test validator
then this will be `http://127.0.0.1:8899`; if you want to use devnet then the URL is `https://api.devnet.solana.com`
- ``websocketUrl`` The corresponding websocket URL of the RPC provider. `ws://127.0.0.1:8900` for the test validator
and `wss://api.devnet.solana.com` for devnet
- ``notaryKeypairFile`` The notary [file-system wallet](https://docs.solanalabs.com/cli/wallets/file-system) 
for singing Corda Program on Solana
- ``custodiedKeysDir`` The directory for notary to store Corda participant Solana file-system wallets 
for singing stablecoin transactions, these should be located in a different directory than the notary wallet
- ``programWhitelist`` the list of Solana Programs that can be run by the Notary, set to address of SPL Token Program
