# Stock Bridging To Solana Sample

This demo shows how to bridge Corda states built with [Token SDK]((https://training.corda.net/libraries/tokens-sdk/))
to the Solana network via an additional participant, the Bridge Authority, and a Solana Notary.
As a sample application, the unmodified [Stock CorDapp](https://github.com/corda/samples-kotlin/tree/release/4.12/Tokens/stockpaydividend)
is deployed to several parties (the same party set as in the original Stock CorDapp demo).

## Pre-Requisites

//TODO 

## Running the sample

//TODO

Modify ``repositories.gradle`` in this project and sibling ``../../Tokens/stockpaydividend/repositories.gradle``
to include Corda artifacts.

Note below commands need to be run from within the project root directory.

This will check the project into 'build/tmp', it will be deleted whenever you run Gradle `clean` task.

Open a terminal and go to the project root directory and type: (to deploy the nodes using bootstrapper)
```bash
./gradlew clean build
```

## Concepts

`StockPayDividend` CorDapp assumes there are 4 parties:
* **WayneCo** - creates the stock state.
* **Shareholder** - owns the stock and bridge shares to Solana Network.
* **Other Shareholder** - will receive tokens on Solana and then redeem on Corda to own the stock.
* **Bank** - issues fiat tokens.
* **Observer** - monitors all the stocks by keeping a copy of transactions whenever a stock is created or updated.

Bridging activities requires additional parties:
* **Bridge Authority** - performs bridging by running "Corda-Solana-Toolkit" Cordapp
* **Solana Notary** - ensures tokens are created on Solana Network

### Flows

//TODO

These steps focus on bridging activities and not on dividend as in [Stock Cordapp](https://github.com/corda/samples-kotlin/tree/release/4.12/Tokens/stockpaydividend) usage.

1. IssueStock - Stock Issuer
WayneCo creates a StockState and issues some stock tokens associated to the created StockState.
On company WayneCo's node: IssueStock symbol: AAPL, name: "Stock, SP500", currency: USD, price: 7.4, issueVol: 500, notary: "O=Notary Service, L=London, C=GB"

2. MoveStock - Stock Issuer
WayneCo transfers some stock tokens to the Shareholder.
On company WayneCo's node: start MoveStock symbol: AAPL, quantity: 100, recipient: Shareholder
The Shareholder received 100 stock tokens: On shareholder node: start GetStockBalance symbol: AAPL

3. Bridge To Solana - Stock Issuer
Shareholder transfers some stock tokens to the Bridge Authority.
On shareholder node: start MoveStock symbol: AAPL, quantity: 60, recipient: "Bridge Authority"

4. Now at the Bridge Authority's terminal, we can see that it received 100 stock tokens:
On Bridge Authority node: start GetStockBalance symbol: TEST
