package com.example.rpcclient

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.RPCConnection
import net.corda.client.rpc.ext.MultiRPCClient
import net.corda.client.rpc.proxy.FlowRPCOps
import net.corda.client.rpc.proxy.NodeFlowStatusRpcOps
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.nodeapi.flow.hospital.FlowStatusQuery
import java.util.*
import java.util.concurrent.CompletableFuture

class RPCClient {
    companion object {
        private fun printExecutedFlowsForClass(currentConn: RPCConnection<NodeFlowStatusRpcOps>, flowClassName: String) {
            val flowStatusQuery = FlowStatusQuery(flowClassName)
            val flowsMatching = currentConn.proxy.getFlowsMatching(flowStatusQuery)
//            println("Flows found for $flowClassName: ")// + flowsMatching)
            flowsMatching.forEach() {
                println("$it : " + currentConn.proxy.getFlowStatus(it)?.flowState.toString())
            }
        }

        private fun getFlowClassFromName(name: String): Class<out FlowLogic<Any>> {
            return Class.forName(name) as Class<out FlowLogic<Any>>
        }

        private fun startFlow(args: Array<String>) {
            require(args.size == 7) { "Usage: rpcclient start-flow <node address> <username> <password> <flow_class> <iou value> <other party X500>" }
            val hostAndPort: NetworkHostAndPort = parse(args[1])
            val username: String = args[2]
            val password: String = args[3]
            val flowString: String = args[4]
            val clientId: String = UUID.randomUUID().toString()
            val flow = getFlowClassFromName(flowString)
            val cordaRPCClientConfiguration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
            val multiRPCClientCordaRPCOps: MultiRPCClient<CordaRPCOps> = MultiRPCClient(hostAndPort, CordaRPCOps::class.java, username, password, cordaRPCClientConfiguration)
            val iouValue: Int = args[5].toInt()
            val otherPartyString = args[6]
            val x500Name: CordaX500Name = CordaX500Name.parse(otherPartyString);
            val proxy = multiRPCClientCordaRPCOps.start().get().proxy
            val otherParty: Party? = proxy.wellKnownPartyFromX500Name(x500Name)
            val flowHandle = proxy.startFlowDynamicWithClientId(clientId, flow, iouValue, otherParty)
            val flowID = flowHandle.id.uuid.toString()
            println("Flow ID: $flowID")
            multiRPCClientCordaRPCOps.close()
        }

        private fun queryFlows(args: Array<String>) {
            require(args.size == 5) { "Usage: rpcclient query-flows <node address> <username> <password> <flow>" }
            val hostAndPort: NetworkHostAndPort = parse(args[1])
            val username: String = args[2]
            val password: String = args[3]
            val flowString: String = args[4]
            val flowClass = getFlowClassFromName(flowString)
            val cordaRPCClientConfiguration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
            val multiRPCClientNodeFlowStatusRpcOps: MultiRPCClient<NodeFlowStatusRpcOps> = MultiRPCClient(hostAndPort, NodeFlowStatusRpcOps::class.java, username, password, cordaRPCClientConfiguration)
            multiRPCClientNodeFlowStatusRpcOps.use {
                val connFuture: CompletableFuture<RPCConnection<NodeFlowStatusRpcOps>> = multiRPCClientNodeFlowStatusRpcOps.start()
                val conn: RPCConnection<NodeFlowStatusRpcOps>? = connFuture.get()
                conn.use { itconn ->
                    if (itconn != null) {
                        printExecutedFlowsForClass(itconn, flowClass.name)
                    }
                }
            }
            multiRPCClientNodeFlowStatusRpcOps.close()
        }

//        private fun killFlow(args: Array<String>) {
//            require(args.size == 5) { "Usage: rpcclient kill-flow <node address> <username> <password> <flow ID>" }
//            val hostAndPort: NetworkHostAndPort = parse(args[1])
//            val username: String = args[2]
//            val password: String = args[3]
//            val flowID = StateMachineRunId(UUID.fromString(args[4]))
//            val cordaRPCClientConfiguration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
//            val multiRPCClientCordaRPCOps: MultiRPCClient<CordaRPCOps> = MultiRPCClient(hostAndPort, CordaRPCOps::class.java, username, password, cordaRPCClientConfiguration)
//            val proxy = multiRPCClientCordaRPCOps.start().get().proxy
//            if (proxy.killFlow(flowID)) {
//                println("Killed ${flowID.uuid}")
//            } else {
//                println("Killing of ${flowID.uuid} went wrong.")
//            }
//            multiRPCClientCordaRPCOps.close()
//        }

//        private fun pauseFlow(args: Array<String>) {
//            require(args.size == 5) { "Usage: rpcclient pause-flow <node address> <username> <password> <flow ID>" }
//            val hostAndPort: NetworkHostAndPort = parse(args[1])
//            val username: String = args[2]
//            val password: String = args[3]
//            val flowID = StateMachineRunId(UUID.fromString(args[4]))
//            val cordaRPCClientConfiguration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
//            val multiRPCClientFlowRPCOps: MultiRPCClient<FlowRPCOps> = MultiRPCClient(hostAndPort, FlowRPCOps::class.java, username, password, cordaRPCClientConfiguration)
//            val proxy = multiRPCClientFlowRPCOps.start().get().proxy
//            if (proxy.pauseFlow(flowID)) {
//                println("Paused ${flowID.uuid}")
//            } else {
//                println("Pausing of ${flowID.uuid} went wrong.")
//            }
//            multiRPCClientFlowRPCOps.close()
//        }

//        private fun retryFlow(args: Array<String>) {
//            require(args.size == 5) { "Usage: rpcclient retry-flow <node address> <username> <password> <flow ID>" }
//            val hostAndPort: NetworkHostAndPort = parse(args[1])
//            val username: String = args[2]
//            val password: String = args[3]
//            val flowID = StateMachineRunId(UUID.fromString(args[4]))
//            val cordaRPCClientConfiguration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT
//            val multiRPCClientFlowRPCOps: MultiRPCClient<FlowRPCOps> = MultiRPCClient(hostAndPort, FlowRPCOps::class.java, username, password, cordaRPCClientConfiguration)
//            val proxy = multiRPCClientFlowRPCOps.start().get().proxy
//            if (proxy.retryFlow(flowID)) {
//                println("Retried ${flowID.uuid}")
//            } else {
//                println("Retrying of ${flowID.uuid} went wrong.")
//            }
//            multiRPCClientFlowRPCOps.close()
//        }
        @JvmStatic
        fun main(args: Array<String>) {
//            require(args.size == 3) { "Usage: rpcclient <node address> <username> <password>" }
            require(args.isNotEmpty()) { "Usage: rpcclient <command> <...>" }
            when (args[0]) {
                "start-flow" -> startFlow(args)
                "query-flows" -> queryFlows(args)
//                "kill-flow" -> killFlow(args)
//                "pause-flow" -> pauseFlow(args)
//                "retry-flow" -> retryFlow(args)
                else -> println("Don't know what to do.")
            }
        }
    }
}