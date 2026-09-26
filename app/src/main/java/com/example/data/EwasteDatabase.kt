package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope

/**
 * Local Room cache for the ECOBRIDGES app.
 *
 * IMPORTANT: this database is a *cache only*. The single source of truth is the
 * shared Supabase (PostgreSQL) project. Rows are pulled from the role-based
 * views on sign-in / refresh and pushed back on creation and handover. There is
 * deliberately no on-device seed data here — the previous mock lots, prices,
 * recyclers and transactions were removed so every role observes the same
 * authoritative cloud records.
 */
@Database(
    entities = [
        MaterialLotEntity::class,
        PriceRecordEntity::class,
        RecyclerEntity::class,
        TransactionLedgerEntity::class,
        SafetyGuidelineEntity::class,
        LotPhotoEntity::class,
        CollectorLocationEntity::class,
        ConnectionRequestEntity::class,
        QuotationEntity::class,
        AuditLogEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class EwasteDatabase : RoomDatabase() {
    abstract fun materialLotDao(): MaterialLotDao
    abstract fun priceDao(): PriceDao
    abstract fun recyclerDao(): RecyclerDao
    abstract fun transactionLedgerDao(): TransactionLedgerDao
    abstract fun safetyGuidelineDao(): SafetyGuidelineDao
    abstract fun lotPhotoDao(): LotPhotoDao
    abstract fun collectorLocationDao(): CollectorLocationDao
    abstract fun connectionRequestDao(): ConnectionRequestDao
    abstract fun quotationDao(): QuotationDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: EwasteDatabase? = null

        /** v5 -> v6: mandatory recycler dataset columns (status, area, mail). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE authorized_recyclers ADD COLUMN authorizationStatus TEXT NOT NULL DEFAULT 'active'")
                db.execSQL("ALTER TABLE authorized_recyclers ADD COLUMN serviceArea TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE authorized_recyclers ADD COLUMN contactEmail TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v6 -> v7: the localized `_hi` / `_mr` siblings of the prose columns,
         * mirroring supabase/migrations/0008_localized_content.sql.
         *
         * Every new column is nullable, so existing cached rows stay valid and
         * the resolver in `LocalizedContent` falls back to the English column
         * until a refresh pulls the translations down. This must stay a real
         * migration rather than relying on the destructive fallback, which
         * would discard lots a collector created while offline.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE price_records ADD COLUMN subCategoryHi TEXT")
                db.execSQL("ALTER TABLE price_records ADD COLUMN subCategoryMr TEXT")
                db.execSQL("ALTER TABLE price_records ADD COLUMN locationHi TEXT")
                db.execSQL("ALTER TABLE price_records ADD COLUMN locationMr TEXT")
                db.execSQL("ALTER TABLE price_records ADD COLUMN keyMetalsJoinedHi TEXT")
                db.execSQL("ALTER TABLE price_records ADD COLUMN keyMetalsJoinedMr TEXT")

                db.execSQL("ALTER TABLE authorized_recyclers ADD COLUMN serviceAreaHi TEXT")
                db.execSQL("ALTER TABLE authorized_recyclers ADD COLUMN serviceAreaMr TEXT")

                db.execSQL("ALTER TABLE safety_guidelines ADD COLUMN practiceTitleHi TEXT")
                db.execSQL("ALTER TABLE safety_guidelines ADD COLUMN practiceTitleMr TEXT")
                db.execSQL("ALTER TABLE safety_guidelines ADD COLUMN whyUnsafeHi TEXT")
                db.execSQL("ALTER TABLE safety_guidelines ADD COLUMN whyUnsafeMr TEXT")
                db.execSQL("ALTER TABLE safety_guidelines ADD COLUMN whatIsLostHi TEXT")
                db.execSQL("ALTER TABLE safety_guidelines ADD COLUMN whatIsLostMr TEXT")
                db.execSQL("ALTER TABLE safety_guidelines ADD COLUMN safeFormalAlternativeHi TEXT")
                db.execSQL("ALTER TABLE safety_guidelines ADD COLUMN safeFormalAlternativeMr TEXT")
            }
        }

        /**
         * Adds the payment detail columns to the local ledger, mirroring
         * supabase/migrations/0011_lot_payments.sql. Both are nullable so existing
         * unsettled claims keep working untouched.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transaction_ledger ADD COLUMN paymentReference TEXT")
                db.execSQL("ALTER TABLE transaction_ledger ADD COLUMN paidAt INTEGER")
            }
        }

        fun getDatabase(context: Context, @Suppress("UNUSED_PARAMETER") scope: CoroutineScope): EwasteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EwasteDatabase::class.java,
                    "ewaste_moefcc_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
