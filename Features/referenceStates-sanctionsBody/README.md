# Reference States - Sanctions Body CorDapp

This CorDapp demonstrates the use of reference states in Corda by implementing an IOU system with sanctions checking. The application shows how reference states can be used to share data across transactions without consuming it.

## Overview

The CorDapp consists of:
- **SanctionedEntities**: A reference state containing a list of sanctioned parties
- **SanctionableIOUState**: An IOU state that references the sanctions list for validation
- **Flows**: Complete IOU lifecycle including issue, transfer, and settlement with sanctions checking

## States

### SanctionedEntities
A linear state that maintains a list of sanctioned parties. This state is used as a reference state in IOU transactions to ensure no sanctioned parties participate.

```kotlin
data class SanctionedEntities(
    val badPeople: List<Party>,
    val issuer: Party,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState
```

### SanctionableIOUState
An IOU state representing a debt between a lender and borrower, with built-in sanctions checking.

```kotlin
data class SanctionableIOUState(
    val value: Int,
    val lender: Party,
    val borrower: Party,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState
```

## Contracts

### SanctionedEntitiesContract
Governs the creation and updating of sanctions lists.

**Commands:**
- `Create`: Creates a new sanctions list
- `Update`: Updates an existing sanctions list

### SanctionableIOUContract
Governs IOU transactions with sanctions checking.

**Commands:**
- `Create`: Issues a new IOU (requires sanctions list reference)
- `Transfer`: Transfers IOU ownership (requires sanctions list reference)
- `Settle`: Settles/closes an IOU

## Flows

### Sanctions Management Flows

#### IssueSanctionsListFlow
Creates an initial empty sanctions list.

**Usage:**
```bash
# Without specific notary (uses default)
flow start IssueSanctionsListFlow

# With specific notary
flow start IssueSanctionsListFlow notary: "O=Notary,OU=CRAFT-LDNM-FPW93FR4K6,L=London,C=GB"
```

#### UpdateSanctionsListFlow
Adds a party to the sanctions list.

**Usage:**
```bash
# Without specific notary (uses existing state's notary)
flow start UpdateSanctionsListFlow partyToSanction: "O=PartyName,L=City,C=Country"

# With specific notary
flow start UpdateSanctionsListFlow partyToSanction: "O=PartyName,L=City,C=Country", notary: "O=Notary,OU=CRAFT-LDNM-FPW93FR4K6,L=London,C=GB"
```

#### GetSanctionsListFlow
Retrieves the latest sanctions list from another node.
**Usage:**
```bash
flow start GetSanctionsListFlow otherParty: "O=SanctionsAuthority,L=London,C=GB"
```

### IOU Lifecycle Flows

#### IOUIssueFlow
Creates a new IOU between two parties with sanctions checking.
```bash
start IOUIssueFlow iouValue: 100, otherParty: "O=Borrower,L=City,C=Country", sanctionsBody: "O=SanctionsAuthority,L=London,C=GB"
```

#### IOUTransferFlow
Transfers IOU ownership from current lender to a new lender.
```bash
start IOUTransferFlow linearId: "LINEAR_ID", newLender: "O=NewLender,L=City,C=Country", sanctionsBody: "O=SanctionsAuthority,L=London,C=GB"
```

#### IOUSettleFlow
Settles an IOU, removing it from the ledger.
```bash
start IOUSettleFlow linearId: "LINEAR_ID", sanctionsBody: "O=SanctionsAuthority,L=London,C=GB"
```

## Complete Usage Guide

This CorDapp demonstrates the use of [reference states](https://training.corda.net/corda-details/reference-states/) in a transaction and in the verification method of a contract.

## Concepts
This CorDapp allows two nodes to enter into an IOU agreement, but enforces that both parties belong to a list of sanctioned entities. This list of sanctioned entities is taken from a referenced SanctionedEntities state.

## Pre-Requisites

[Set up for CorDapp development](https://docs.r3.com/en/platform/corda/4.12/community/getting-set-up.html)


### Running the CorDapp

Open a terminal and go to the project root directory and type: (to deploy the nodes using bootstrapper)
```
./gradlew clean build deployNodes
```
Then type: (to run the nodes)
```
./build/nodes/runnodes
```


### Running the flow

We will interact with the nodes via their shell.

When the nodes are up and running, the first thing you need to do is create a sanctions list. To do this, open the shell for the SanctionsBody node and run the command:

    flow start IssueSanctionsListFlow

Now that the sanctions list has been made, the party that wants to issue the flow needs to be able to reference it. To do this, it needs to pull the sanction list into its own vault. From the IOUPartyA shell run:

    flow start GetSanctionsListFlow otherParty: SanctionsBody

Next, we want to issue an IOU. Run from the IOUPartyA shell:

    flow start IOUIssueFlow iouValue: 5, otherParty: IOUPartyB, sanctionsBody: SanctionsBody

We've seen how to successfully send an IOU to a non-sanctioned party, so what if we want to send one to a sanctioned party? First we need to update the sanction list so, from the SanctionsBody shell, run:

    flow start UpdateSanctionsListFlow partyToSanction: DodgyParty

We need to update the reference before we use it in a new transaction so, from IOUPartyA's shell, run:

    flow start GetSanctionsListFlow otherParty: SanctionsBody

Now try an issue a flow to DodgyParty:

    flow start IOUIssueFlow iouValue: 5, otherParty: DodgyParty, sanctionsBody: SanctionsBody

The flow will error with the message 'java.lang.IllegalArgumentException: Failed requirement: The borrower O=DodgyParty, L=Moscow, C=RU is a sanctioned entity'!

