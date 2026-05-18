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
```bash
start IssueSanctionsListFlow
```

#### UpdateSanctionsListFlow
Adds a party to the sanctions list.
```bash
start UpdateSanctionsListFlow partyToSanction: "O=PartyName,L=City,C=Country"
```

#### GetSanctionsListFlow
Retrieves the latest sanctions list from another node.
```bash
start GetSanctionsListFlow otherParty: "O=SanctionsAuthority,L=London,C=GB"
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

### Prerequisites
- Corda 4.12+
- Java 8+
- At least 4 nodes: SanctionsAuthority, TestNodeA, TestNodeB, TestNodeC

### Step-by-Step Execution

#### 1. Initial Setup - Create Sanctions List
```bash
# On Sanctions Authority Node
start IssueSanctionsListFlow
```

#### 2. Distribute Sanctions List
```bash
# On TestNodeA
start GetSanctionsListFlow otherParty: "O=SanctionsAuthority,L=London,C=GB"

# On TestNodeB  
start GetSanctionsListFlow otherParty: "O=SanctionsAuthority,L=London,C=GB"

# On TestNodeC
start GetSanctionsListFlow otherParty: "O=SanctionsAuthority,L=London,C=GB"
```

#### 3. Issue IOU
```bash
# On TestNodeA (as lender)
start IOUIssueFlow iouValue: 100, otherParty: "O=TestNodeB,L=London,C=GB", sanctionsBody: "O=SanctionsAuthority,L=London,C=GB"
```
**Note:** Copy the `linearId` from the output for use in subsequent steps.

#### 4. Transfer IOU (Optional)
```bash
# On TestNodeA (current lender)
start IOUTransferFlow linearId: "YOUR_LINEAR_ID_HERE", newLender: "O=TestNodeC,L=London,C=GB", sanctionsBody: "O=SanctionsAuthority,L=London,C=GB"
```

#### 5. Settle IOU
```bash
# On current lender (TestNodeA or TestNodeC if transferred)
start IOUSettleFlow linearId: "YOUR_LINEAR_ID_HERE", sanctionsBody: "O=SanctionsAuthority,L=London,C=GB"
```

### Testing Sanctions

#### Add Party to Sanctions List
```bash
# On Sanctions Authority
start UpdateSanctionsListFlow partyToSanction: "O=TestNodeC,L=London,C=GB"
```

#### Test Sanctions Enforcement
```bash
# This should fail - trying to create IOU with sanctioned party
start IOUIssueFlow iouValue: 100, otherParty: "O=TestNodeC,L=London,C=GB", sanctionsBody: "O=SanctionsAuthority,L=London,C=GB"

# This should fail - trying to transfer to sanctioned party
start IOUTransferFlow linearId: "YOUR_LINEAR_ID_HERE", newLender: "O=TestNodeC,L=London,C=GB", sanctionsBody: "O=SanctionsAuthority,L=London,C=GB"
```

## Verification Commands

### Check Vault Contents
```bash
# Check all IOUs
run vaultQuery contractStateType: net.corda.samples.referencestates.states.SanctionableIOUState

# Check sanctions list
run vaultQuery contractStateType: net.corda.samples.referencestates.states.SanctionedEntities

# Check all states
run vaultQuery contractStateType: net.corda.core.contracts.ContractState
```

### Check Network Map
```bash
# List all nodes
run networkMapSnapshot
```

## Flow Participants and Roles

| Flow | Initiator | Participants | Signers | Notes |
|------|-----------|--------------|---------|-------|
| IssueSanctionsListFlow | Sanctions Authority | Sanctions Authority | Sanctions Authority | Creates initial sanctions list |
| UpdateSanctionsListFlow | Sanctions Authority | Sanctions Authority | Sanctions Authority | Updates sanctions list |
| GetSanctionsListFlow | Any Node | Requesting Node, Sanctions Authority | None | Retrieves sanctions list |
| IOUIssueFlow | Lender | Lender, Borrower | Lender, Borrower | Creates new IOU |
| IOUTransferFlow | Current Lender | Current Lender, New Lender, Borrower | Current Lender, New Lender | Transfers IOU ownership |
| IOUSettleFlow | Lender or Borrower | Lender, Borrower | Lender, Borrower | Settles IOU |

## Key Features

### Reference States
- **Sanctions List**: Used as reference state in all IOU transactions
- **Non-Consuming**: Reference states are not consumed in transactions
- **Shared Data**: Multiple transactions can reference the same sanctions list
- **Version Control**: Transactions fail if sanctions list is updated after being referenced

### Sanctions Enforcement
- **Creation**: Cannot create IOUs involving sanctioned parties
- **Transfer**: Cannot transfer IOUs to sanctioned parties
- **Real-time**: Uses latest sanctions list for validation
- **Automatic**: Built into contract verification

### Flow Communication
- **Role-based**: Each participant knows their role (signer vs observer)
- **Bulletproof Serialization**: Uses `@CordaSerializable` data classes
- **Clear Protocols**: Separate signature collection from finality notification
- **Error Handling**: Robust error handling and logging

## Building and Testing

### Build
```bash
./gradlew clean build
```

### Run Tests
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "*.IOUFlowTests"
./gradlew test --tests "*.IOUSettleFlowTests"
./gradlew test --tests "*.SanctionsFlowTests"
```

### Deploy Nodes
```bash
./gradlew deployNodes
```

### Start Nodes
```bash
# Navigate to build/nodes directory
cd build/nodes

# Start all nodes
./runnodes
```

## Architecture
