package net.corda.samples.states

import groovy.util.GroovyTestCase.assertEquals
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.UniqueIdentifier
import net.corda.testing.node.MockServices
import org.junit.Test
import java.util.*

class StateTests {
    private val ledgerServices = MockServices()

    @Test
    fun AssethasCorrectFieldOfCorrectType() {
        //Check Asset fields and type
        Asset::class.java.getDeclaredField("linearId")
        assertEquals(Asset::class.java.getDeclaredField("linearId").type, UniqueIdentifier::class.java)
        Asset::class.java.getDeclaredField("title")
        assertEquals(Asset::class.java.getDeclaredField("title").type, String::class.java)
        Asset::class.java.getDeclaredField("description")
        assertEquals(Asset::class.java.getDeclaredField("description").type, String::class.java)
    }

    @Test
    fun AuctionStateHasCorrectFieldOfCorrectType(){
        AuctionState::class.java.getDeclaredField("auctionItem")
        assertEquals(AuctionState::class.java.getDeclaredField("auctionItem").type, LinearPointer::class.java)
        AuctionState::class.java.getDeclaredField("auctionId")
        assertEquals(AuctionState::class.java.getDeclaredField("auctionId").type, UUID::class.java)
        AuctionState::class.java.getDeclaredField("basePrice")
        assertEquals(AuctionState::class.java.getDeclaredField("basePrice").type, Amount::class.java)
    }
}