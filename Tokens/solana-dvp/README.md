# Solana DvP Sample CorDapp 

This CorDapp provides a basic example to create, issue and perform a DvP (Delivery vs Payment) of an [Evolvable](https://training.corda.net/libraries/token-sdk/token-introduction/#evolvabletokentype), [NonFungible](https://training.corda.net/libraries/token-sdk/token-introduction/#nonfungibletoken) token in 
Corda utilizing the [Token SDK](https://github.com/corda/token-sdk).

## Concepts

### Flows

There are three flows that we'll primarily use in this example that you'll be building off of.

1. Create and issue a stock token using `StockTokenCreateAndIssueFlow`.
2. Initiate the sale of the shares through `StockSaleInitiatorFlow`.

## Pre-Requisites
[Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.12/community/getting-set-up.html)