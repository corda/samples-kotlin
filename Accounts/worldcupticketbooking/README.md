<p align="center">
  <img src="https://github.com/corda/samples-java/blob/DEVREL-1623/Accounts/t20-cricket-worldcup-ticket-booking-cordapp/images/T20-World-Cup-e1578570579412.jpg" alt="Corda" width="2000">
</p>

# T20 Cricket World Cup Ticket Booking using Accounts and Tokens

This Cordapp shows how to integrate accounts and tokens. 

# Background

This sample shows you how to integrate accounts and tokens. This sample talks about a scenario where typically when the Cricket season starts, BCCI (Board of Control for Cricket) starts selling tickets.
As of now there are multiple dealers whom the BCCI issues tickets and further these dealers sell tickets to their client. We are trying to simulate similar functionality maintaining the entore issuance and selling
of the tickets on Corda Platform.

# Steps to Execute

Required Nodes-

1. BCCI node
2. Bank Node
3. Dealer1 Node
4. Dealer2 Node

Accounts-

Accounts will be created by the Dealer nodes for their clients on their nodes and will be shared with the Bank and BCCI nodes.

Looking at the above diagram follow below mentioned steps to run the application.

####  Step 1

    start CreateAndShareAccountFlow accountName : dealer1 , partyToShareAccountInfoTo : BCCI

Run the above flow on the Dealer1 node. This will create an account on the Dealer1 node and share this account info with BCCI node.
partyToShareAccountInfoTo will be modified later to take in a list so that account can be shared with multiple nodes.
The above flow will create an account named dealer1 on Dealer1 node. Similarly create below accounts on Dealer1 node.

    start CreateAndShareAccountFlow  accountName : buyer1 , partyToShareAccountInfoTo : Bank
    start CreateAndShareAccountFlow  accountName : buyer2 , partyToShareAccountInfoTo : Bank
    
The above flows will craete accounts named buyer1 and buyer2 on Dealer1's node and will share this account info with the Bank node.

####  Step 2

    run vaultQuery contractStateType : com.r3.corda.lib.accounts.contracts.states.AccountInfo

Run the above query to confirm if accounts are created on Dealer1 node. Also run the above query on Bank and BCCI node to confirm if 
account info is shared with these nodes.

####  Step 3

    start IssueCashFlow accountName : buyer1 , currency : USD , amount : 20

Run the above command on the Bank node, which will issue 20 USD to buyer1 account.

####  Step 4

        run vaultQuery contractStateType : com.r3.corda.lib.accounts.contracts.states.AccountInfo

Copy the id from the above querys output. This is the external id or you can say the unique id of the account.
Check balance of buyer1 account by hitting below API by passing in this id. Note you will have to start the server before hitting the API.
This is a POST request so hit below url using Postman.

    http://127.0.0.1:8080/cash-balance?accountId=2f9c288c-aea8-4214-bf1f-b401729a9cea

####  Step 5

    run vaultQuery contractStateType : com.r3.corda.lib.tokens.contracts.states.FungibleToken

Run the above command on Dealer1's node to confirm if 20 USD fungible tokens are created on Dealer1's node. Look at the current holder field in the output.
This should be an AnonymousParty which specifies an account.

####  Step 6

    start CreateT20CricketTicketTokenFlow ticketTeam : MumbaiIndiansVsRajasthanRoyals
    
Run the above flow on BCCI's node. BCCI node will create base token type for the T20 Ticket for the match MumbaiIndians Vs RajasthanRoyals. The ticket ID returned from this flow will be needed in the next steps.

####  Step 7

    run vaultQuery contractStateType : com.t20worldcup.states.T20CricketTicket

You can see your ticket state generated via vault query
####  Step 8

    start IssueNonFungibleTicketFlow tokenId : <XXX-XXX-XXXX-XXXXX>, dealerAccountName : dealer1

Run the above flow on BCCI's node to issue a non fungible token based off the token type which we created in Step5. You will need to replace the `<XXX-XXX-XXXX-XXXXX>` with the uuid returned from step 6. This token will be issued by the BCCI node to dealer1 account on Dealer1 node. 

####  Step 9

    run vaultQuery contractStateType : com.r3.corda.lib.tokens.contracts.states.NonFungibleToken

Run the above flow on Dealer1's node to confirm if the token has been issued to the dealer1 account. Take a look at the current holder it will be a key representing the account.

####  Step 10
```
flow start BuyT20CricketTicket tokenId: <XXX-XXX-XXXX-XXXXX>, buyerAccountName: buyer1, sellerAccountName: dealer1, costOfTicker: 5, currency: USD
```

This is the DVP flow where the buyer(buyer1 account on Dealer1 node) account will pay cash to seller account(dealer1 account on Dealer1 node), and the seller accountwill transfer the ticket token to the buyer. Again, replace the `<XXX-XXX-XXXX-XXXXX>` with the uuid generated in step 6.

####  Step 11
```
flow start QuerybyAccount whoAmI: buyer1
flow start QuerybyAccount whoAmI: dealer1
```
Confirm who owns the FungibleToken (cash) and NonFungibleToken (ticket) again by running this on Dealer1's node.

####  Step 12

Retrieve the account id of dealer1 and buyer1 as we did in Step5 and confirm the cash balances. dealer1 account should have balance of 5 and buyer1 will have 
balance of 15.

# Further Reading

For accounts visit https://github.com/corda/accounts.

For tokens visit https://github.com/corda/token-sdk.

# Future Enhancements

1.As of now accounts created on Dealer1 node perform the DVP. This sample will be modified to include Dealer2 as well and have accounts on Dealer1 interact with 
Dealer2's accounts. 

2.The base token type will be updated and the updates will be distributed to the current token holders.
