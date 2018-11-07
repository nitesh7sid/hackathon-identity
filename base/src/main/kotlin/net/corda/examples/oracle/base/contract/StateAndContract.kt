package net.corda.examples.oracle.base.contract

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

const val PRIME_PROGRAM_ID: ContractClassName = "net.corda.examples.oracle.base.contract.PrimeContract"

class TokenContract : Contract {
    // Commands signed by oracles must contain the facts the oracle is attesting to.
    class Issue(val request: RequestToken) : CommandData

    // Our contract does not check that the Nth prime is correct. Instead, it checks that the
    // information in the command and state match.
    override fun verify(tx: LedgerTransaction) = requireThat {

         "There are no inputs" using (tx.inputs.isEmpty())
        val output = tx.outputsOfType<RequestToken>().single()
        val command = tx.commands.requireSingleCommand<Create>().value
        "The prime in the output does not match the prime in the command." using
                (command.n == output.n && command.nthPrime == output.nthPrime)
    }
    }
}

// Type of identity. For poc its just PASSPORT that will be submitted by the requester

enum class IdentityType{ PASSPORT }

// Identity document
data class DID(val id: String,
               val type: IdentityType
               )

data class RequestToken(val identity: DID,
                        val token: String,
                        val issuer: Party,
                        val requester: Party,
                        override val linearId: UniqueIdentifier = UniqueIdentifier(),
                        override val participants: List<AbstractParty> = listOf(issuer, requester)): LinearState