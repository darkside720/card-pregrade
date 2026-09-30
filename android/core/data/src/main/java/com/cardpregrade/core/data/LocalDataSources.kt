package com.cardpregrade.core.data

import android.content.Context
import com.cardpregrade.core.data.db.CardPregradeDatabase
import com.cardpregrade.core.data.repository.InspectionRepository
import com.cardpregrade.core.data.repository.RoomInspectionRepository
import com.cardpregrade.core.data.repository.RoomScanRepository
import com.cardpregrade.core.data.repository.ScanRepository

/**
 * Entry point to on-device persistence. Exposes repository interfaces only, so Room stays an
 * implementation detail of this module.
 */
class LocalDataSources private constructor(database: CardPregradeDatabase) {
    val scanRepository: ScanRepository = RoomScanRepository(database.cardDao(), database.scanSessionDao())
    val inspectionRepository: InspectionRepository = RoomInspectionRepository(database.inspectionDao())

    companion object {
        fun create(context: Context): LocalDataSources = LocalDataSources(CardPregradeDatabase.create(context))
    }
}
