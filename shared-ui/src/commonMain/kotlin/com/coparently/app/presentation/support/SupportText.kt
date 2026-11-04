package com.coparently.app.presentation.support

import com.coparently.app.domain.money.DecimalText
import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.support.SupportTerms
import com.coparently.app.utils.ReaderMoneyText
import com.coparently.app.utils.readerMonthTitle
import kotlinx.datetime.YearMonth

/*
 * How child support's figures read, for the Child support screen and the Requests inbox's support
 * row (MON-24 phase 2, slice 11d, which moved the inbox to `:shared-ui`; the screen follows). On
 * Android each is the call `SupportScreen.kt` made, so the text is what it was.
 */

/**
 * [terms]' monthly amount in the reader's conventions: `currencyFormat` over
 * `BigDecimal.valueOf(amountMinor, 2)` on Android, through the money seam.
 */
fun supportAmount(terms: SupportTerms): String =
    ReaderMoneyText.amount(DecimalText.plain(terms.amountMinor, PaymentQr.MONEY_SCALE), terms.currency).orEmpty()

/**
 * [month] as the reader's language names it, capitalised: "October 2026", "Říjen 2026" — the
 * month's own form "LLLL yyyy" in the language the app speaks.
 */
fun supportMonth(month: YearMonth): String = readerMonthTitle(month)
