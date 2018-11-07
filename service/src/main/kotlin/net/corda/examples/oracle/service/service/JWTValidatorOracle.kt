package net.corda.examples.oracle.service.service

import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.examples.oracle.base.contract.PrimeContract

@CordaService
class JWTValidatorOracle(val services: ServiceHub) : SingletonSerializeAsToken() {
    private val myKey = services.myInfo.legalIdentities.first().owningKey

    fun sign(ftx: FilteredTransaction): TransactionSignature {
        // Check the partial Merkle tree is valid.
        ftx.verify()

        /**
         * Returns true if the component is an Create command and the oracle is listed as signer
         */
        fun iAmSigner(elem: Any) = when {
            elem is Command<*> -> {
                myKey in elem.signers
            }
            else -> false
        }

        /**
         * For Validates just one token.
         *
         */
        fun validateOneToken() : Boolean {
            return true
        }

        val isSignedByMe = ftx.checkWithFun(::iAmSigner)


        // Is it a Merkle tree we are willing to sign over?
        val isValidMerkleTree = isSignedByMe

        if (isValidMerkleTree) {
            return services.createSignature(ftx, myKey)
        } else {
            throw IllegalArgumentException("Oracle signature requested over invalid transaction.")
        }
    }
}