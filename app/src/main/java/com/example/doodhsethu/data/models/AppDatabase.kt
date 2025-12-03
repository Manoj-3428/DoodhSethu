package com.example.doodhsethu.data.models

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.doodhsethu.utils.DateConverter
import com.example.doodhsethu.data.models.DailyMilkCollection
import com.example.doodhsethu.data.models.Farmer
import com.example.doodhsethu.data.models.FatRangeRow
import com.example.doodhsethu.data.models.MilkCollection
import com.example.doodhsethu.data.models.User
import com.example.doodhsethu.data.models.BillingCycle
import com.example.doodhsethu.data.models.FarmerBillingDetail

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add synced column with default value false
        database.execSQL("ALTER TABLE farmers ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS fat_table (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `from` REAL NOT NULL, `to` REAL NOT NULL, price INTEGER NOT NULL)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS milk_collections (id TEXT PRIMARY KEY NOT NULL, farmerId TEXT NOT NULL, farmerName TEXT NOT NULL, quantity REAL NOT NULL, fatPercentage REAL NOT NULL, basePrice REAL NOT NULL, totalPrice REAL NOT NULL, collectedBy TEXT NOT NULL, collectedAt INTEGER NOT NULL, isSynced INTEGER NOT NULL DEFAULT 0)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS users (userId TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, password TEXT NOT NULL, role TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isSynced INTEGER NOT NULL DEFAULT 0)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Handle the synced field change for farmers table
        // Check if isSynced column exists and rename it to synced
        try {
            database.execSQL("ALTER TABLE farmers RENAME COLUMN isSynced TO synced")
        } catch (e: Exception) {
            // If isSynced doesn't exist, add synced column
            database.execSQL("ALTER TABLE farmers ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        }
        
        // Add isSynced column to fat_table if it doesn't exist
        try {
            database.execSQL("ALTER TABLE fat_table ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
        } catch (e: Exception) {
            // Column might already exist, ignore
        }
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add session column to milk_collections table
        try {
            database.execSQL("ALTER TABLE milk_collections ADD COLUMN session TEXT NOT NULL DEFAULT 'AM'")
        } catch (e: Exception) {
            // Column might already exist, ignore
        }
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create billing_cycles table
        database.execSQL("CREATE TABLE IF NOT EXISTS billing_cycles (id TEXT PRIMARY KEY NOT NULL, startDate INTEGER NOT NULL, endDate INTEGER NOT NULL, totalAmount REAL NOT NULL, isPaid INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, isSynced INTEGER NOT NULL DEFAULT 0)")
        
        // Create farmer_billing_details table
        database.execSQL("CREATE TABLE IF NOT EXISTS farmer_billing_details (id TEXT PRIMARY KEY NOT NULL, billingCycleId TEXT NOT NULL, farmerId TEXT NOT NULL, farmerName TEXT NOT NULL, totalAmount REAL NOT NULL, paidAmount REAL NOT NULL DEFAULT 0, isPaid INTEGER NOT NULL DEFAULT 0, isSynced INTEGER NOT NULL DEFAULT 0)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Update farmer_billing_details table structure
        // Rename totalAmount to originalAmount and add balanceAmount column
        try {
            // Create new table with updated schema
            database.execSQL("""
                CREATE TABLE farmer_billing_details_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    billingCycleId TEXT NOT NULL,
                    farmerId TEXT NOT NULL,
                    farmerName TEXT NOT NULL,
                    originalAmount REAL NOT NULL,
                    paidAmount REAL NOT NULL DEFAULT 0,
                    balanceAmount REAL NOT NULL DEFAULT 0,
                    isPaid INTEGER NOT NULL DEFAULT 0,
                    paymentDate INTEGER,
                    isSynced INTEGER NOT NULL DEFAULT 0
                )
            """)
            
            // Copy data from old table to new table
            database.execSQL("""
                INSERT INTO farmer_billing_details_new (
                    id, billingCycleId, farmerId, farmerName, 
                    originalAmount, paidAmount, balanceAmount, 
                    isPaid, isSynced
                )
                SELECT 
                    id, billingCycleId, farmerId, farmerName,
                    totalAmount, paidAmount, (totalAmount - paidAmount),
                    isPaid, isSynced
                FROM farmer_billing_details
            """)
            
            // Drop old table and rename new table
            database.execSQL("DROP TABLE farmer_billing_details")
            database.execSQL("ALTER TABLE farmer_billing_details_new RENAME TO farmer_billing_details")
            
        } catch (e: Exception) {
            // If table doesn't exist, create it with new schema
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS farmer_billing_details (
                    id TEXT PRIMARY KEY NOT NULL,
                    billingCycleId TEXT NOT NULL,
                    farmerId TEXT NOT NULL,
                    farmerName TEXT NOT NULL,
                    originalAmount REAL NOT NULL,
                    paidAmount REAL NOT NULL DEFAULT 0,
                    balanceAmount REAL NOT NULL DEFAULT 0,
                    isPaid INTEGER NOT NULL DEFAULT 0,
                    paymentDate INTEGER,
                    isSynced INTEGER NOT NULL DEFAULT 0
                )
            """)
        }
        
        // Add isActive column to billing_cycles table
        try {
            database.execSQL("ALTER TABLE billing_cycles ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
        } catch (e: Exception) {
            // Column might already exist, ignore
        }
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isActive column to billing_cycles table if it doesn't exist
        try {
            database.execSQL("ALTER TABLE billing_cycles ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
        } catch (e: Exception) {
            // Column might already exist, ignore
        }
        
        // Update default values for existing columns
        try {
            database.execSQL("UPDATE billing_cycles SET isPaid = 1 WHERE isPaid IS NULL")
            database.execSQL("UPDATE billing_cycles SET isSynced = 0 WHERE isSynced IS NULL")
        } catch (e: Exception) {
            // Ignore errors
        }
        
        // Ensure farmer_billing_details table exists with correct schema
        try {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS farmer_billing_details (
                    id TEXT PRIMARY KEY NOT NULL,
                    billingCycleId TEXT NOT NULL,
                    farmerId TEXT NOT NULL,
                    farmerName TEXT NOT NULL,
                    originalAmount REAL NOT NULL,
                    paidAmount REAL NOT NULL DEFAULT 0,
                    balanceAmount REAL NOT NULL DEFAULT 0,
                    isPaid INTEGER NOT NULL DEFAULT 0,
                    paymentDate INTEGER,
                    isSynced INTEGER NOT NULL DEFAULT 0
                )
            """)
        } catch (e: Exception) {
            // Table might already exist, ignore
        }
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add name column to billing_cycles table
        try {
            database.execSQL("ALTER TABLE billing_cycles ADD COLUMN name TEXT NOT NULL DEFAULT ''")
        } catch (e: Exception) {
            // Column might already exist, ignore
        }
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add name column to billing_cycles table if it doesn't exist
        try {
            database.execSQL("ALTER TABLE billing_cycles ADD COLUMN name TEXT NOT NULL DEFAULT ''")
        } catch (e: Exception) {
            // Column might already exist, ignore
        }
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new columns to farmers table
        try {
            database.execSQL("ALTER TABLE farmers ADD COLUMN totalAmount REAL NOT NULL DEFAULT 0.0")
        } catch (e: Exception) {
            // Column might already exist, ignore
        }
        try {
            database.execSQL("ALTER TABLE farmers ADD COLUMN pendingAmount REAL NOT NULL DEFAULT 0.0")
        } catch (e: Exception) {
            // Column might already exist, ignore
        }
        try {
            database.execSQL("ALTER TABLE farmers ADD COLUMN billingCycles TEXT NOT NULL DEFAULT ''")
        } catch (e: Exception) {
            // Column might already exist, ignore
        }
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS daily_milk_collections (
                    id TEXT PRIMARY KEY NOT NULL,
                    date TEXT NOT NULL,
                    farmerId TEXT NOT NULL,
                    farmerName TEXT NOT NULL,
                    amMilk REAL NOT NULL DEFAULT 0.0,
                    amFat REAL NOT NULL DEFAULT 0.0,
                    amPrice REAL NOT NULL DEFAULT 0.0,
                    pmMilk REAL NOT NULL DEFAULT 0.0,
                    pmFat REAL NOT NULL DEFAULT 0.0,
                    pmPrice REAL NOT NULL DEFAULT 0.0,
                    totalMilk REAL NOT NULL DEFAULT 0.0,
                    totalFat REAL NOT NULL DEFAULT 0.0,
                    totalAmount REAL NOT NULL DEFAULT 0.0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    isSynced INTEGER NOT NULL DEFAULT 0
                )
            """)
        } catch (e: Exception) { /* ignore */ }
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        android.util.Log.d("Migration", "Starting MIGRATION_14_15")
        
        // Clean up existing duplicates by keeping only the most recent entry for each farmer-date combination
        try {
            android.util.Log.d("Migration", "Cleaning up duplicate entries...")
            database.execSQL("""
                DELETE FROM daily_milk_collections 
                WHERE id NOT IN (
                    SELECT MAX(id) 
                    FROM daily_milk_collections 
                    GROUP BY farmerId, date
                )
            """)
            android.util.Log.d("Migration", "Duplicate cleanup completed")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error during duplicate cleanup: ${e.message}")
            // Table might not exist yet, ignore
        }
        
        android.util.Log.d("Migration", "MIGRATION_14_15 completed")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        android.util.Log.d("Migration", "Starting MIGRATION_15_16 - Converting fat_table price to REAL")
        
        try {
            // Create new table with REAL price column
            database.execSQL("""
                CREATE TABLE fat_table_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `from` REAL NOT NULL,
                    `to` REAL NOT NULL,
                    price REAL NOT NULL,
                    isSynced INTEGER NOT NULL DEFAULT 0
                )
            """)
            
            // Copy data from old table to new table
            database.execSQL("""
                INSERT INTO fat_table_new (id, `from`, `to`, price, isSynced)
                SELECT id, `from`, `to`, CAST(price AS REAL), isSynced
                FROM fat_table
            """)
            
            // Drop old table
            database.execSQL("DROP TABLE fat_table")
            
            // Rename new table to original name
            database.execSQL("ALTER TABLE fat_table_new RENAME TO fat_table")
            
            android.util.Log.d("Migration", "MIGRATION_15_16 completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error during MIGRATION_15_16: ${e.message}")
            throw e
        }
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        android.util.Log.d("Migration", "Starting MIGRATION_16_17 - Adding addedBy column to fat_table")
        
        try {
            // Add addedBy column to fat_table
            database.execSQL("ALTER TABLE fat_table ADD COLUMN addedBy TEXT NOT NULL DEFAULT ''")
            android.util.Log.d("Migration", "Added addedBy column to fat_table")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error adding addedBy column to fat_table: ${e.message}")
        }
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        android.util.Log.d("Migration", "Starting MIGRATION_17_18 - Adding addedBy column to billing_cycles")
        
        try {
            // Add addedBy column to billing_cycles
            database.execSQL("ALTER TABLE billing_cycles ADD COLUMN addedBy TEXT NOT NULL DEFAULT ''")
            android.util.Log.d("Migration", "Added addedBy column to billing_cycles")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error adding addedBy column to billing_cycles: ${e.message}")
        }
    }
}

@Database(entities = [Farmer::class, FatRangeRow::class, MilkCollection::class, User::class, BillingCycle::class, FarmerBillingDetail::class, DailyMilkCollection::class], version = 18, exportSchema = false)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun farmerDao(): FarmerDao
    abstract fun fatTableDao(): FatTableDao
    abstract fun milkCollectionDao(): MilkCollectionDao
    abstract fun userDao(): UserDao
    abstract fun billingCycleDao(): BillingCycleDao
    abstract fun farmerBillingDetailDao(): FarmerBillingDetailDao
    abstract fun dailyMilkCollectionDao(): DailyMilkCollectionDao
}