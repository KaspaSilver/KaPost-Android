package com.kachat.app.util

import com.kachat.app.services.UtxoEntry

/**
 * The Send screens' fee model, as on iOS (SendKaspaComponents / SpendingAddressWithdrawView) and
 * the chat Send KAS sheet: a base fee priced at the quoted network rate on the REAL input count,
 * plus Fast / Priority / a custom fee as a flat extra on top, paid exactly
 * ([com.kachat.app.services.KaspaWalletEngine.sendKaspa]'s `extraFeeSompi`).
 *
 * The screens used to turn the chosen total fee into a rate (total / one-input mass) and pass
 * that rate to the engine, which charges `rate * realMass`. Every extra input then multiplied the
 * fee: a 0.1 KAS custom fee on a 20-input send paid about 1.14 KAS. A flat extra cannot scale
 * with the input count, so the fee shown is the fee paid.
 *
 * Pure, so the screens, the view models and the unit tests all price a send the same way.
 */
object SendFeeModel {
    /** Standard P2PK scriptPublicKey length, for a recipient or change output whose exact script
     *  is not known yet. */
    const val STANDARD_SCRIPT_LEN = 34

    /** Passes of [previewAutomaticSelection]'s fixed point. Each pass that does not settle adds at
     *  least one input, so this only bounds pathological coin sets. */
    private const val MAX_PREVIEW_PASSES = 16

    /** The extra over [baseFeeSompi]: the custom extra when one is set, else what the speed adds
     *  (Fast 2x, Priority 5x the base). */
    fun extraFee(baseFeeSompi: Long, feeMultiplier: Long, customExtraFeeSompi: Long?): Long =
        customExtraFeeSompi?.coerceAtLeast(0L) ?: (baseFeeSompi * (feeMultiplier - 1)).coerceAtLeast(0L)

    /** The coins one transaction can spend: the [maxInputs] largest when there are more, else all
     *  of them (order kept). Mirrors the engine's refusal of anything over
     *  [KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION] inputs. */
    fun largestSpendable(
        utxos: List<UtxoEntry>,
        maxInputs: Int = KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION,
    ): List<UtxoEntry> =
        if (utxos.size > maxInputs) utxos.sortedByDescending { it.utxoEntry.amount }.take(maxInputs) else utxos

    /** A Max (iOS d816404 `estimateMaxWithdrawalAmount`'s answer). [cappedAtInputLimit]: the
     *  address holds more coins than one transaction can spend, so this is the most its largest
     *  [KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION] coins can send (what the send itself picks,
     *  largest first) - not the whole balance, which no single transaction could carry. */
    data class MaxAmount(val amountSompi: Long, val cappedAtInputLimit: Boolean)

    /** Compound's one transaction: the coins it pins, what they consolidate to, and
     *  [MaxAmount.cappedAtInputLimit]'s meaning for the rest of the address's coins. */
    data class ConsolidationChunk(val amountSompi: Long, val utxos: List<UtxoEntry>, val cappedAtInputLimit: Boolean)

    /** Base fee for [inputCount] inputs paying a recipient plus change. */
    fun baseFee(
        inputCount: Int,
        feeRateSompiPerGram: Long,
        payloadSize: Int = 0,
        recipientScriptLen: Int = STANDARD_SCRIPT_LEN,
        changeScriptLen: Int = STANDARD_SCRIPT_LEN,
        /** Per-input signature-script size: a quantum-safe (KIP-22) source's inputs are bigger
         *  ([KaspaMass.KIP22_SIGNATURE_SCRIPT_LEN]). */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
    ): Long =
        KaspaMass.calculateFee(
            KaspaMass.calculateMass(
                numInputs = inputCount.coerceAtLeast(1),
                outputScriptLens = listOf(recipientScriptLen, changeScriptLen),
                payloadSize = payloadSize,
                sigScriptLens = KaspaMass.sigScriptLensFor(inputCount.coerceAtLeast(1), signatureScriptSize),
            ),
            feeRateSompiPerGram,
        )

    /**
     * Max for a send that spends [totalSompi] over [inputCount] inputs: what is left after the
     * base fee and the extra. The extra is derived from this same base (or is the custom extra),
     * never from a fee estimated for some other amount - so Max always leaves room for exactly the
     * extra a Fast / Priority send of that amount adds.
     */
    fun maxAfterFees(
        totalSompi: Long,
        inputCount: Int,
        feeRateSompiPerGram: Long,
        feeMultiplier: Long = 1L,
        customExtraFeeSompi: Long? = null,
        payloadSize: Int = 0,
        /** See [baseFee]. */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
        /** The recipient's real script length: a P2SH or ECDSA address's is a byte longer than
         *  the standard one, and the send prices it (iOS `estimateSendAllFee` takes the real
         *  recipient script). */
        recipientScriptLen: Int = STANDARD_SCRIPT_LEN,
        /** The change address's script length, as the send prices it. */
        changeScriptLen: Int = STANDARD_SCRIPT_LEN,
        /** Kept back on top of the fees: iOS `estimateSendAllFee`'s `+ 3`
         *  ([KaspaUtxoSelector.FEE_BUFFER_SOMPI]) on a Max. */
        bufferSompi: Long = 0L,
    ): Long {
        val base = baseFee(inputCount, feeRateSompiPerGram, payloadSize, recipientScriptLen, changeScriptLen, signatureScriptSize)
        val extra = extraFee(base, feeMultiplier, customExtraFeeSompi)
        return (totalSompi - base - bufferSompi - extra).coerceAtLeast(0L)
    }

    /** What an automatic-selection send of a given amount spends, and what it pays. Passing
     *  [utxos] as coin control and [extraFeeSompi] as the extra reproduces exactly this fee.
     *  [foldedChangeSompi] is change too small to keep, which goes to the fee on top of that, so it
     *  counts in what the Review shows (iOS e5a9b11 `SendFeeEstimate`). */
    data class Preview(
        val utxos: List<UtxoEntry>,
        val baseFeeSompi: Long,
        val extraFeeSompi: Long,
        val foldedChangeSompi: Long = 0L,
    ) {
        val totalFeeSompi: Long get() = baseFeeSompi + extraFeeSompi
    }

    /**
     * Change too small to keep on a coin-control send of exactly [utxos], with [extraFeeSompi] on
     * top: it goes to the fee and the Review counts it, as [Preview.foldedChangeSompi] does for
     * automatic selection. 0 when the coins' amounts are not real values ([UtxoMath]); the send
     * refuses those itself.
     */
    fun manualFoldedChange(
        utxos: List<UtxoEntry>,
        amountSompi: Long,
        feeRateSompiPerGram: Long,
        extraFeeSompi: Long,
        recipientScriptLen: Int = STANDARD_SCRIPT_LEN,
        changeScriptLen: Int = STANDARD_SCRIPT_LEN,
        /** See [baseFee]. */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
        /** See [previewAutomaticSelection]. */
        payloadSize: Int = 0,
    ): Long = try {
        KaspaUtxoSelector.selectManualUtxosAndCalculateFee(
            utxos = utxos,
            amountSompi = amountSompi,
            feeRateSompiPerGram = feeRateSompiPerGram,
            recipientScriptLen = recipientScriptLen,
            changeScriptLen = changeScriptLen,
            payloadBytes = payloadStandIn(payloadSize),
            extraFeeSompi = extraFeeSompi,
            signatureScriptSize = signatureScriptSize,
        ).foldedChangeSompi
    } catch (e: IllegalStateException) {
        0L
    }

    /**
     * Whether a coin-control send of exactly [utxos] is refused for storage mass
     * ([KaspaUtxoSelector.SMALL_SEND_MASS_MESSAGE]): its change can neither stand as an output nor
     * fold with the recipient's output alone fitting (iOS d816404, the manual selector's guard).
     * False when the coins don't cover it (that is the funds error) or aren't real values.
     */
    fun manualStorageMassBlocked(
        utxos: List<UtxoEntry>,
        amountSompi: Long,
        feeRateSompiPerGram: Long,
        extraFeeSompi: Long,
        recipientScriptLen: Int = STANDARD_SCRIPT_LEN,
        changeScriptLen: Int = STANDARD_SCRIPT_LEN,
        /** See [baseFee]. */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
        /** See [previewAutomaticSelection]. */
        payloadSize: Int = 0,
    ): Boolean = try {
        val selection = KaspaUtxoSelector.selectManualUtxosAndCalculateFee(
            utxos = utxos,
            amountSompi = amountSompi,
            feeRateSompiPerGram = feeRateSompiPerGram,
            recipientScriptLen = recipientScriptLen,
            changeScriptLen = changeScriptLen,
            payloadBytes = payloadStandIn(payloadSize),
            extraFeeSompi = extraFeeSompi,
            signatureScriptSize = signatureScriptSize,
        )
        // the no-change shape's recipient output alone doesn't fit (the selector says so itself)
        val noChangeBlocked = selection.totalSelected >= selection.requiredAmount && selection.storageMassBlocked
        amountSompi > 0 && (noChangeBlocked || selection.changeAmount > 0 &&
            !KaspaUtxoSelector.changeIsKeptOrFoldable(selection.selectedUtxos.map { it.utxoEntry.amount }, listOf(amountSompi), selection.changeAmount))
    } catch (e: IllegalStateException) {
        false
    }

    /**
     * Coin control's [Preview]: a send of exactly [utxos] with [extraFeeSompi] on top, priced by
     * the selector the send runs ([KaspaUtxoSelector.selectManualUtxosAndCalculateFee]) - its
     * no-change shape included - so [Preview.totalFeeSompi] plus [Preview.foldedChangeSompi] is
     * what the send pays. Null when the coins don't cover it, or their amounts are not real
     * values ([UtxoMath]). Throws [KaspaUtxoSelector.tooManySelectedMessage] over
     * [KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION] coins, and
     * [KaspaUtxoSelector.SMALL_SEND_MASS_MESSAGE] when the change can neither stand nor fold
     * (iOS d816404 selectManualUtxosForPayment).
     */
    fun previewManualSelection(
        utxos: List<UtxoEntry>,
        amountSompi: Long,
        feeRateSompiPerGram: Long,
        extraFeeSompi: Long,
        recipientScriptLen: Int = STANDARD_SCRIPT_LEN,
        changeScriptLen: Int = STANDARD_SCRIPT_LEN,
        /** See [baseFee]. */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
        /** See [previewAutomaticSelection]. */
        payloadSize: Int = 0,
    ): Preview? {
        if (amountSompi <= 0 || utxos.isEmpty()) return null
        // a fixed set over the input cap would be rejected by the node (iOS selectManualUtxosForPayment)
        if (utxos.size > KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION) {
            throw IllegalStateException(KaspaUtxoSelector.tooManySelectedMessage(utxos.size))
        }
        val selection = try {
            KaspaUtxoSelector.selectManualUtxosAndCalculateFee(
                utxos = utxos,
                amountSompi = amountSompi,
                feeRateSompiPerGram = feeRateSompiPerGram,
                recipientScriptLen = recipientScriptLen,
                changeScriptLen = changeScriptLen,
                payloadBytes = payloadStandIn(payloadSize),
                extraFeeSompi = extraFeeSompi,
                signatureScriptSize = signatureScriptSize,
            )
        } catch (e: IllegalStateException) {
            return null // invalid node data (UtxoMath): no preview rather than a wrong one
        }
        if (selection.totalSelected < selection.requiredAmount) return null
        if (selection.storageMassBlocked || (selection.changeAmount > 0 &&
                !KaspaUtxoSelector.changeIsKeptOrFoldable(selection.selectedUtxos.map { it.utxoEntry.amount }, listOf(amountSompi), selection.changeAmount))
        ) {
            throw IllegalStateException(KaspaUtxoSelector.SMALL_SEND_MASS_MESSAGE)
        }
        return Preview(selection.selectedUtxos, selection.estimatedFee - extraFeeSompi, extraFeeSompi, selection.foldedChangeSompi)
    }

    /**
     * What automatic selection (largest-first, [KaspaUtxoSelector.selectUtxosAndCalculateFee] - the
     * selector the real send runs) picks for [amountSompi], with the base fee priced on those
     * inputs and the extra on top. Mirrors ColdStorageSendEngine.previewAutomaticSelection, which
     * the Send screen's display used to lack: it assumed one input.
     *
     * The extra changes how many inputs are needed (they must cover amount + base + extra), and a
     * tier's extra depends on the base, so this settles the two together. Whatever pass it ends
     * on, [Preview.baseFeeSompi] is priced on the inputs selected WITH [Preview.extraFeeSompi], so
     * a send passing that extra pays [Preview.totalFeeSompi]. Null when the coins cannot cover it,
     * or when their amounts are not real values ([UtxoMath]). Throws
     * [KaspaUtxoSelector.SMALL_SEND_MASS_MESSAGE] when they cover it but no set fits storage mass,
     * and [KaspaUtxoSelector.TOO_MANY_INPUTS_MESSAGE] when it takes more than
     * [KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION] coins.
     */
    fun previewAutomaticSelection(
        utxos: List<UtxoEntry>,
        amountSompi: Long,
        feeRateSompiPerGram: Long,
        feeMultiplier: Long = 1L,
        customExtraFeeSompi: Long? = null,
        recipientScriptLen: Int = STANDARD_SCRIPT_LEN,
        changeScriptLen: Int = STANDARD_SCRIPT_LEN,
        /** See [baseFee]. */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
        /** The payload the send carries, in bytes: a chat payment's `kchat:1:pay:` one
         *  ([MessageProtocol.estimatedPaymentPayloadSize], iOS `estimatePaymentFee(note:)`); 0 for
         *  a plain transfer. */
        payloadSize: Int = 0,
    ): Preview? {
        if (amountSompi <= 0 || utxos.isEmpty()) return null
        val payload = payloadStandIn(payloadSize)
        var extra = customExtraFeeSompi?.coerceAtLeast(0L) ?: 0L
        var preview: Preview? = null
        repeat(MAX_PREVIEW_PASSES) {
            val selection = try {
                KaspaUtxoSelector.selectUtxosAndCalculateFee(
                    utxos = utxos,
                    amountSompi = amountSompi,
                    feeRateSompiPerGram = feeRateSompiPerGram,
                    payloadBytes = payload,
                    recipientScriptLen = recipientScriptLen,
                    changeScriptLen = changeScriptLen,
                    extraFeeSompi = extra,
                    signatureScriptSize = signatureScriptSize,
                )
            } catch (e: IllegalStateException) {
                return null // invalid node data (UtxoMath): no preview rather than a wrong one
            }
            // More coins than one transaction can spend: the send refuses it, so the Review says
            // why (iOS selectUtxosForPayment stops at the input cap).
            if (selection.selectedUtxos.size > KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION) {
                throw IllegalStateException(KaspaUtxoSelector.TOO_MANY_INPUTS_MESSAGE)
            }
            if (selection.totalSelected < selection.requiredAmount) return null
            // No coin set fits storage mass without giving real change away: the send refuses
            // it, so the Review says why rather than showing a fee (iOS d816404, the estimate
            // runs the send's own selector).
            if (selection.storageMassBlocked) throw IllegalStateException(KaspaUtxoSelector.SMALL_SEND_MASS_MESSAGE)
            val base = selection.estimatedFee - extra
            val settled = Preview(selection.selectedUtxos, base, extra, selection.foldedChangeSompi)
            preview = settled
            val next = extraFee(base, feeMultiplier, customExtraFeeSompi)
            if (next == extra) return settled
            extra = next
        }
        return preview
    }

    /** A payload of [size] bytes for the selectors, which only price its length. */
    private fun payloadStandIn(size: Int): ByteArray? = if (size > 0) ByteArray(size) else null
}
