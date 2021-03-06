package net.corda.examples.oracle.client.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.examples.oracle.base.contract.*
import net.corda.examples.oracle.base.flow.QueryIdentity
import net.corda.examples.oracle.base.flow.SignPrime
import java.util.function.Predicate

// The client-side flow that:
// - Uses 'QueryPrime' to request the Nth prime number
// - Adds it to a transaction and signs it
// - Uses 'SignPrime' to gather the oracle's signature attesting that this really is the Nth prime
// - Finalises the transaction
@InitiatingFlow
@StartableByRPC
class RequestForToken(val token: RequestToken) : FlowLogic<SignedTransaction>() {

    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flow.")
        object QUERYING_THE_ORACLE : ProgressTracker.Step("Querying oracle for the Nth prime.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object ORACLE_SIGNS : ProgressTracker.Step("Requesting oracle signature.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SET_UP, QUERYING_THE_ORACLE, BUILDING_THE_TX,
                VERIFYING_THE_TX, WE_SIGN, ORACLE_SIGNS, FINALISING)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = SET_UP
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        // In Corda v1.0, we identify oracles we want to use by name.
        val oracleName = CordaX500Name("Oracle", "New York","US")
        val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleName)?.legalIdentities?.first()
                 ?: throw IllegalArgumentException("Requested oracle $oracleName not found on network.")

        progressTracker.currentStep = QUERYING_THE_ORACLE
        val nthPrimeRequestedFromOracle = subFlow(QueryIdentity(oracle, token))

        progressTracker.currentStep = BUILDING_THE_TX
        val tokenCmdData = TokenContract.Commands.Issue(token)
        // By listing the oracle here, we make the oracle a required signer.
        val tokenCmdRequiredSigners = listOf(oracle.owningKey, ourIdentity.owningKey)
        val builder = TransactionBuilder(notary)
                .addOutputState(token, TOKEN_CONTRACT_ID)
                .addCommand(tokenCmdData, tokenCmdRequiredSigners)

        progressTracker.currentStep = VERIFYING_THE_TX
        builder.verify(serviceHub)

        progressTracker.currentStep = WE_SIGN
        val ptx = serviceHub.signInitialTransaction(builder)

        progressTracker.currentStep = ORACLE_SIGNS
        // For privacy reasons, we only want to expose to the oracle any commands of type `Prime.Create`
        // that require its signature.
        val ftx = ptx.buildFilteredTransaction(Predicate {
            when (it) {
                is Command<*> -> oracle.owningKey in it.signers && it.value is TokenContract.Commands.Issue
                else -> false
            }
        })

        val oracleSignature = subFlow(SignPrime(oracle, ftx))
        val stx = ptx.withAdditionalSignature(oracleSignature)

        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(stx))
    }
}