package com.personal.inout

import android.app.Application
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.PocketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InOutApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Rule 36: Bootstrap physical Cash in Hand pocket into room database on launch
        val db = AppDatabase.getInstance(this)
        CoroutineScope(Dispatchers.IO).launch {
            val existing = db.ledgerDao().getDefaultCashInHandPocket()
            if (existing == null) {
                db.ledgerDao().insertPocket(
                    LedgerPocket(
                        name = "Cash in Hand",
                        type = PocketType.LIQUID,
                        currency = "INR"
                    )
                )
            }
        }
    }
}
