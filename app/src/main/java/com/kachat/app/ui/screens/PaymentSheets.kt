package com.kachat.app.ui.screens

/** Longest memo a payment carries. It rides encrypted in the payment payload and shows in the
 *  payment bubble, so it stays note-sized (iOS `maxPaymentMemoLength`, 8d208b2). */
const val MAX_PAYMENT_MEMO_LENGTH = 140

/** 0.10000001 KAS - the network dust limit below which a payment may be refused. */
internal const val DUST_LIMIT_SOMPI = 10_000_001L
