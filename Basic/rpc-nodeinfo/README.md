# NodeInfo

Allows one to get some rudimentary information about a running Corda node via [RPC](https://docs.corda.net/docs/corda-os/api-rpc.html#api-rpc-operations)

Useful for debugging network issues, ensuring flows have loaded etc.

After cloning, use the _getInfo_ gradle task to retrieve node information.



## Concepts


Here we'll be using java just to create an RPC call against a corda node.


You'll find our example to do this in [Main.kt](./kotlin-app/src/main/kotlin/net/corda/Main.kt#L15)

```kotlin
    val proxy = loginToCordaNode(host, username, password)

    println("Node connected: ${proxy.nodeInfo().legalIdentities.first()}")

    println("Time: ${proxy.currentNodeTime()}.")

    println("Flows: ${proxy.registeredFlows()}")

    println("Platform version: ${proxy.nodeInfo().platformVersion}")

    println("Current Network Map Status -->")
    proxy.networkMapSnapshot().map {
        println("-- ${it.legalIdentities.first().name} @ ${it.addresses.first().host}")
    }

    println("Registered Notaries -->")
    proxy.notaryIdentities().map {
        println("-- ${it.name}")
    }
```


## Usage



### Deploy and run the node

```
./greadlew deployNodes
./build/node/runnodes
```

Then run the following task against Party A defined in the CorDapp Example:

Java version:

    ./gradlew kotlin-app:getInfo -Phost="localhost:10007" -Pusername="user1" -Ppassword="test"

In a closer look of the parameters:

- Phost: The RPC connection address of the target node
- Pusername: The username used to login (specified in the node.conf)
- Ppassword: The password used to login (specified in the node.conf)

### Sample Output

```
./gradlew getInfo -Phost="localhost:10006" -Pusername="user1" -Ppassword="test"

> Task :getInfo
Logging into localhost:10006 as user1
Node connected: O=PartyA, L=London, C=GB
Time: 2018-02-27T11:30:37.729Z.
Flows: [com.example.flow.ExampleFlow$Initiator, net.corda.core.flows.ContractUpgradeFlow$Authorise, net.corda.core.flows.ContractUpgradeFlow$Deauthorise, net.corda.core.flows.ContractUpgradeFlow$Initiate, net.corda.finance.flows.CashConfigDataFlow, net.corda.finance.flows.CashExitFlow, net.corda.finance.flows.CashIssueAndPaymentFlow, net.corda.finance.flows.CashIssueFlow, net.corda.finance.flows.CashPaymentFlow]
Platform version: 2
Current Network Map Status -->
-- O=PartyA, L=London, C=GB @ localhost
-- O=Controller, L=London, C=GB @ localhost
-- O=PartyB, L=New York, C=US @ localhost
-- O=PartyC, L=Paris, C=FR @ localhost
-- O=Notary, L=London, C=GB @ localhost
Registered Notaries -->
-- O=Notary, L=London, C=GB
-- O=Controller, L=London, C=GB
```

### Errors

`Exception in thread "main" ActiveMQSecurityException[errorType=SECURITY_EXCEPTION message=AMQ119031: Unable to validate user]`

Caused by: Wrong RPC credentials. Check the node.conf file and try again.

`Exception in thread "main" ActiveMQNotConnectedException[errorType=NOT_CONNECTED message=AMQ119007: Cannot connect to server(s). Tried with all available servers.]`

Caused by: Network connectivity issues or node not running.
