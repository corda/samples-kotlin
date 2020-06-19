# Yo! CorDapp

Send Yo's! to all your friends running Corda nodes!


## Concepts

In the original yo application, the app sent what is essentially a nudge from one endpoint and another.

In corda, we can use abstractions to accomplish the same thing.


We define a [state](https://training.corda.net/key-concepts/concepts/#states) (the yo to be shared), define a [contract](https://training.corda.net/key-concepts/concepts/#contracts) (the way to make sure the yo is legit), and define the [flow](https://training.corda.net/key-concepts/concepts/#flows) (the control flow of our cordapp).

### States
We define a [Yo as a state](./contracts/src/main/kotlin/net/corda/examples/yo/states/YoState.kt), or a corda fact.

```kotlin
@BelongsToContract(YoContract::class)
data class YoState(val origin: Party,
                   val target: Party,
                   val yo: String = "Yo!") : ContractState {
    override val participants = listOf(target)
    override fun toString() = "${origin.name}: $yo"
}
```


### Contracts
We define [the "Yo Social Contract"](./contracts/src/main/kotlin/net/corda/examples/yo/contracts/YoContract.kt), which, in this case, verifies some basic assumptions about a Yo.

```kotlin
    override fun verify(tx: LedgerTransaction) = requireThat {
        val command = tx.commands.requireSingleCommand<Commands.Send>()
        "There can be no inputs when Yo'ing other parties." using (tx.inputs.isEmpty())
        "There must be one output: The Yo!" using (tx.outputs.size == 1)
        val yo = tx.outputsOfType<YoState>().single()
        "No sending Yo's to yourself!" using (yo.target != yo.origin)
        "The Yo! must be signed by the sender." using (yo.origin.owningKey == command.signers.single())
    }

```


### Flows
And then we send the Yo [within a flow](./workflows/src/main/kotlin/net/corda/examples/yo/flows/Flows.kt).

```kotlin
        @Suspendable
        override fun call(): SignedTransaction {
        progressTracker.currentStep = CREATING

        val me = serviceHub.myInfo.legalIdentities.first()
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        val command = Command(YoContract.Commands.Send(), listOf(me.owningKey))
        val state = YoState(me, target)
        val stateAndContract = StateAndContract(state, YoContract.ID)
        val utx = TransactionBuilder(notary = notary).withItems(stateAndContract, command)

        progressTracker.currentStep = SIGNING
        val stx = serviceHub.signInitialTransaction(utx)

        progressTracker.currentStep = VERIFYING
        stx.verify(serviceHub)

        progressTracker.currentStep = FINALISING
        val targetSession = initiateFlow(target)
        return subFlow(FinalityFlow(stx, listOf(targetSession), FINALISING.childProgressTracker()))
    }
```

On the receiving end, the other corda node will simply receive the Yo using corda provided subroutines, or subflows.

```kotlin
    return subFlow(ReceiveFinalityFlow(counterpartySession))
```


## Usage


### Pre-Requisites

See https://docs.corda.net/getting-set-up.html.


### Running the nodes

```
./gradlew clean deployNodes
```
Then type: (to run the nodes)
```
./build/nodes/runnodes
```

### Sending a Yo

We will interact with the nodes via their specific shells. When the nodes are up and running, use the following command to send a
Yo to another node:

```
    flow start YoFlow target: PartyB
```

Where `NODE_NAME` is 'PartyA' or 'PartyB'. The space after the `:` is required. You are not required to use the full
X500 name in the node shell. Note you can't sent a Yo! to yourself because that's not cool!

To see all the Yo's! other nodes have sent you in your vault (you do not store the Yo's! you send yourself), run:

```
    run vaultQuery contractStateType: net.corda.examples.yo.states.YoState
```
