# Corda Solana Samples

## Corda Asset Bridging to Solana

This sample demonstrates a asset bridging pattern between Corda 4 and the Solana blockchain. 
It models how a Corda-issued asset (e.g., a "stock" token represented in Corda) can be moved (back and forth) 
across networks by coordinating Corda ledger updates with corresponding Solana accounts.

[See the bridge-token sample](bridge-token/)

## Atomic Delivery-versus-Payment

This sample demonstrates an atomic Delivery-versus-Payment (DvP) workflow spanning delivery on Corda 4 
and payment in stablecoins on Solana. 
The goal is to ensure that delivery of an asset and payment occur as a single logical outcome:
either both happen or neither happens, reducing settlement risk across chains.

[See the delivery-vs-payment sample](delivery-vs-payment/)

