package com.coparently.app.di.koin

import com.coparently.app.data.mlkit.MlKitReceiptTextRecognizer
import com.coparently.app.domain.receipts.ReceiptTextRecognizer
import org.koin.dsl.module

/** The on-device receipt text recognizer (item 10: nothing leaves the phone). */
val receiptModule = module {
    single<ReceiptTextRecognizer> { get<MlKitReceiptTextRecognizer>() }
}
