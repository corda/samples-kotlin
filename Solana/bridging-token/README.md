# Stock Bridging To Solana Sample

This sample demonstrates how to bridge Corda assets (states/tokens) to the Solana network using:
- a Bridge Authority (an extra Corda participant that orchestrates bridging/redemption), and
- a Solana Notary (a Corda notary backed by a Solana notary program).

The Corda asset in this sample is a Fungible Token created using the Corda [Token SDK](https://training.corda.net/libraries/tokens-sdk/),
but the same pattern can be applied to other Corda states.
This project extends (without modifying the core flow logic of) the [StockPayDividends sample CorDapp](https://github.com/corda/samples-kotlin/tree/release/4.12/Tokens/stockpaydividend)
and deploys the same party set as the original demo and adds bridging parties.

## Pre-Requisites
[Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.13/enterprise/cordapps/getting-set-up.html)

You need access to Corda Enterprise (via repository access or a developer pack). 
Provide repository URLs for Corda Enterprise JARs in:
- ``repositories.gradle`` 
- ``../../Tokens/stockpaydividend/repositories.gradle``.
  Update the following in``../constant.properties``:
- ``cordaOsVersion`` 
- ``cordaEnterpiseVersion``

Minimum supported version is Corda 4.14.

Install the [Solana CLI:](https://solana.com/docs/intro/installation).

## Running the sample

From the repository root:
```bash
./gradlew build
```
On Windows:
```bash
gradlew.bat build
```
This runs the integration test 
``./workflows/src/integrationTest/kotlin/net/corda/samples/solana/briding/BridgingTokenDriverTest.kt`` file.
The test is written using Corda Driver DSL. It deploys and starts local Corda nodes and installs the CorDapps, 
starts Solana local test validator (or targets devnet - TBD), creates Solana accounts and deploys the required Solana program,
bridges a portion of a Corda asset to Solana, then redeems it back.

## Concepts

Bridging allows to transfer an assets form Corda network to a token representation on Solana ledger.
The Cord asset is never burned, but it locked and can be redeemed back.
This sample demonstrates how to bridge Corda assets to the Solana network using
a Bridge Authority (an extra Corda participant that orchestrates bridging/redemption), and
a Solana Notary (a Corda notary backed by a Solana notary program).
The Corda asset in this sample is a Fungible Token created using the Corda Tokens SDK,
but the same pattern can be applied to other Corda states.
As an example, this project extends (without modifying the core flow logic of) the ``StockPayDividends`` sample CorDapp
and deploys the same party set as the original demo and adds bridging parties:

`StockPayDividend` CorDapp assumes there are following parties:
* **WayneCo** - creates the stock definition/state and issues stock tokens.
* **Shareholder** - holds stock tokens on Corda and bridges/redeems to/from Solana.
* **Other Shareholder** - receives tokens on Solana and may redeem them on Corda
* **Notary** - the regular Corda notary (for non-bridging transactions).
* **Bridge Authority** - a special node that facilitates bridging and redemption.
* **Solana Notary** - notary responsible for bridging/redemption transactions and Solana-side submission
* **Bank** - issues fiat tokens (used by the original sample).
* **Observer** -observes stock lifecycle transactions (used by the original sample).

WayneCo, Observer and Bank do not participate in bridging directly, but remain necessary for the stock issuance workflow 
from the original Stock CorDapp.

### Flows

This sample focuses on bridging, not on dividend distribution (which is the focus of the original Stock CorDapp).

#### Prerequisites:
Before bridging ``WayneCo`` creates a ``StockState`` for ``APPL``, issues stock tokens associated with that ``StockState``,
transfers some stock tokens to ``Shareholder``.

On Solana Wallet accounts for ``Briding Authority``, shareholders and Token Mint (related to Corda's ``APPL`` token) 
are created and funded.

``Bridge Authority`` node must be configured with mappings between:
- Corda participants and Solana wallet accounts, 
- Corda assets  and Solana mints + mint authorities
``Solana Notary`` must have access to required Solana custodied keys,
because it authorizes Solana transactions as part of notarisation.

#### Bridging Flow:

1. ``Shareholder`` moves tokens to Bridge Authority (Corda)
``Shareholder`` transfers shares (``60 AAPl`` shares) to the Bridge Authority by running ``MoveStock`` flow 
with ``Bridge Authority`` as the recipient, this is a standard Corda (Tokens SDK) action - 
the asset is moved under Corda’s rules before any Solana action. 
Tokens SDK action: the asset is moved under Corda’s rules before any Solana action occurs.

2. ``Bridge Authority`` prepares the bridged representation
Bridge Authority performs bridging on behalf of Shareholder:
  - Locks the received shares under a confidential identity controlled by Bridge Authority
    It internally locks the shares received by moving it under Corda Confidential Identity and creates a Corda token 
    equivalent state with additional Solana information.
  - Creates a “token-equivalent” representation that includes required Solana metadata (mint, destination wallet), 
    the metadata is looked up from the node configuration
  - Ensures the recipient (``Shareholder``) has an Associated Token Account (ATA) for that mint;
    if not, submits a Solana transaction to create it.
  - Changes a notary for the “token-equivalent” representation to the Solana Notary 
    (bridging-related transactions are notarised by the Solana Notary, not the regular Corda Notary)
    ``Bridge Authority`` then builds a Corda transaction that consumes the “bridged representation” state
    and includes the required instruction for minting on Solana.
    The Corda transaction is submitted to Solana Notary.

3. Solana Notary verifies and mints on Solana
The Solana Notary verifies the validity of the Corda transaction - checks that the amount to be minted on Solana
matches the amount locked on Corda, then it submits the Solana mint transaction. 
The Solana transaction is sign via Bridge Authority’s Solana wallet custodied by Solana Notary.
The Solana Notary finalizes the Corda transaction only if the Solana mint succeeds 
and the Solana program detects no double-mint.
After finality, the bridged amount is represented as tokens owned by the ``Shareholder``’s Solana wallet.

#### Redemption Flow:

1. Redemption initialization on Solana
    The Shareholder transfers some Solana tokens to a designated wallet, 
    more specifically to a Associated Token Account (ATA) for token mint’s.
   ``Bridge Authority`` monitors the redemption token accounts and is notified when a transfer occurs.
   ``Bridge Authority`` determines (from node configuration) which Corda asset type / token type Solana mint corresponds 
     and constructs a Corda transaction that  creates a new Corda “token-equivalent” representation state to me redeemed. 
     The state contains Solana metadata the token mint, the token account (ATA) and the amount to burn.
     The transaction contains instruction for Solana Notary to burn tokens on Solana from the redemption wallet (ATA).
   ``Bridge Authority`` submits the transaction to the Solana Notary or notarisation

2. Solana Notary verifies and burns on Solana
   The Solana Notary verifies the Corda transaction is valid - checks that the amount to be burn on Solana
   matches the amount to unlocked on Corda, then it submits a Solana bur transaction.
   The Solana transaction is signed using custodied redemption wallet for ``Shareholder``.
   The Solana Notary finalizes the Corda transaction only if the Solana burn succeeds.
   
3. Bridge Authority releases the Corda asset
   After the burn on Solana is finalized the ``Bridge Authority`` contains the “token-equivalent” representation,
   that carry on redemption process.
   ``Bridge Authority`` updates the “token-equivalent” representation so that subsequent Corda-only steps 
   are notarised by the regular Corda notary (Notary) rather than the Solana Notary.
   ``Bridge Authority`` performs Corda token selection and builds a Corda transaction that:
   - moves the redeemed shares to the Shareholder (via regular Corda Fungible Token move)
   - consumes the “token-equivalent” representation state
   This transaction is submitted to the regular Notary for verification and finality.
   At this point, redemption is complete:
   - the Solana tokens have been burned, and 
   - the corresponding Corda stock tokens have been transferred to the Shareholder.

### Configuration

Bridge Authority requires configuration to associate Corda participants/assets with Solana wallets and mints.
The CorDapp configuration file 
``<BRIDGE_AUTHORITY_NODE_ROOT_DIR>/cordapps/config/corda-bridging-token-workflows-0.1.1-SNAPSHOT.conf`` should contain:

- ``participants`` Map of Corda participants (X500 name) to their Solana wallet accounts (base58 address)
- ``redemptionWalletAccountToHolder`` Map of Solana wallet accounts (base58 address) used 
for redemption to Corda participants (X500 name)
- ``mintsWithAuthorities`` Map of Corda token type identifier (e.g., linear UUID / token identifier) to Solana mint account 
and mint authority (base58 addresses)
- ``solanaNotaryName`` - Corda X500 name od the notary that notarises bridging and redemption Corda transactions 
(the ``Solana Notary``)
- ``generalNotaryName`` - Corda X500 name of the notary used for regular Corda transactions (non-bridging)
- ``solanaWsUrl`` - URL of the RPC provider for interacting with the blockchain; if you are using the test validator
then this will be `http://127.0.0.1:8899`, if you want to use devnet then the URL is `https://api.devnet.solana.com`
- ``solanaRpcUrl`` - The corresponding websocket URL of the RPC provider. `ws://127.0.0.1:8900` for the test validator
and `wss://api.devnet.solana.com` for devnet
- ``bridgeAuthorityWalletFile`` Solana Wallet used to sign transaction for actions like creating ATAs for participants
- ``lockingIdentityLabel`` - Internal label used by Bridge Authority to store/retrieve the confidential identity used
for locking Corda assets (any UUID string)

Corda notary requires additional settings in ``node.conf`` file, under a ``notary.solana`` entry:
- ``rpcUrl`` URL of the RPC provider for interacting with the blockchain. If you are using the test validator
 then this will be `http://127.0.0.1:8899`; if you want to use devnet then the URL is `https://api.devnet.solana.com`
- ``websocketUrl`` The corresponding websocket URL of the RPC provider. `ws://127.0.0.1:8900` for the test validator
 and `wss://api.devnet.solana.com` for devnet
- ``notaryKeypairFile`` The notary [file-system wallet](https://docs.solanalabs.com/cli/wallets/file-system)
 for singing Corda Program on Solana
- ``custodiedKeysDir`` The directory for notary to store file-system wallets of Bridge Authority and a fee payer 
 for singing token mint transactions, these should be located in a different directory than the notary wallet
- ``programWhitelist`` the list of Solana Programs that can be run by the Notary, set to address of Token2022 Token Program

### FAQ 

- Why there are two notaries?
Bridging is designed to be minimally invasive to the existing Corda network: existing participants 
and the existing notary can continue running the unmodified Stock CorDapp, bridging is enabled by adding Bridge Authority 
and a Solana Notary, only bridging/redemption flows require the Solana Notary 
hence a need for the second notary in the Corda network.

- How locking works?
Bridge Authority “holds” bridged assets on Corda rather than destroying the original asset.
Assets moved for bridging are transferred to a confidential identity belonging to Bridge Authority,
this keeps a redeemable pool of assets on Corda.
Participants trust Bridge Authority to follow the authorized bridging/redeeming flows and not bypass constraints

- During redemption, which Corda token are transfer back?
Because the bridged asset is a Fungible Token, redemption does not require returning 
the exact same token instances that were originally bridged. Instead, bridged assets form a pooled inventory 
held by Bridge Authority and any current holder of the Solana token can redeem on Corda.
This means Solana-side token transfers are independent of the original Corda holder.
Example:
  - Shareholder bridges shares
  - Shareholder transfers Solana tokens to Other Shareholder on Solana
  - Other Shareholder redeems those shares on Corda (by transferring Solana tokens to redemption account to be burned)

- When is bridging “done”?
There may be a delay between the Shareholder moving tokens to Bridge Authority on Corda, 
and the Solana Notary minting tokens on Solana. However, the process is designed to be resilient to Corda node restarts, 
transient networking failures and temporary loss of Solana RPC connectivity.
