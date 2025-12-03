package com.example.doodhsethu.utils

import android.content.Context
import com.example.doodhsethu.data.repository.BillingCycleRepository
import com.example.doodhsethu.data.repository.DailyMilkCollectionRepository
import com.example.doodhsethu.data.repository.FarmerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

object TestDataCreator {
    
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    
    /**
     * Create sample test data for billing cycles and milk collections
     */
    suspend fun createSampleData(context: Context) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("TestDataCreator", "Creating sample test data...")
            
            val farmerRepository = FarmerRepository(context)
            val dailyMilkCollectionRepository = DailyMilkCollectionRepository(context)
            val billingCycleRepository = BillingCycleRepository(context)
            
            // Get existing farmers
            val farmers = farmerRepository.getAllFarmers()
            android.util.Log.d("TestDataCreator", "Found ${farmers.size} farmers")
            
            if (farmers.isEmpty()) {
                android.util.Log.w("TestDataCreator", "No farmers found. Please add farmers first.")
                return@withContext
            }
            
            // Create sample milk collections for the past 2 weeks
            val calendar = Calendar.getInstance()
            val today = calendar.time
            
            // Create collections for the past 14 days
            for (i in 14 downTo 1) {
                calendar.time = today
                calendar.add(Calendar.DAY_OF_MONTH, -i)
                val collectionDate = calendar.time
                val dateStr = dateFormat.format(collectionDate)
                
                // Create collections for each farmer
                for (farmer in farmers.take(3)) { // Only use first 3 farmers
                    try {
                        val amMilk = Random.nextDouble(2.0, 8.0)
                        val amFat = Random.nextDouble(3.0, 6.0)
                        val pmMilk = Random.nextDouble(1.0, 6.0)
                        val pmFat = Random.nextDouble(3.0, 6.0)
                        
                        // Calculate prices based on fat content (simplified calculation)
                        val amPrice = amMilk * amFat * 2.5 // Simple rate calculation
                        val pmPrice = pmMilk * pmFat * 2.5
                        
                        dailyMilkCollectionRepository.createTodayCollection(
                            farmerId = farmer.id,
                            farmerName = farmer.name,
                            amMilk = amMilk,
                            amFat = amFat,
                            amPrice = amPrice,
                            pmMilk = pmMilk,
                            pmFat = pmFat,
                            pmPrice = pmPrice
                        )
                        
                        android.util.Log.d("TestDataCreator", "Created collection for ${farmer.name} on $dateStr")
                        
                    } catch (e: Exception) {
                        android.util.Log.e("TestDataCreator", "Error creating collection for ${farmer.name}: ${e.message}")
                    }
                }
            }
            
            // Create a billing cycle for the past 2 weeks
            calendar.time = today
            calendar.add(Calendar.DAY_OF_MONTH, -14)
            val startDate = calendar.time
            
            calendar.time = today
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            val endDate = calendar.time
            
            try {
                val billingCycle = billingCycleRepository.createBillingCycle(startDate, endDate)
                android.util.Log.d("TestDataCreator", "Created billing cycle: ${billingCycle.name}")
                android.util.Log.d("TestDataCreator", "Billing cycle total amount: ₹${billingCycle.totalAmount}")
            } catch (e: Exception) {
                android.util.Log.e("TestDataCreator", "Error creating billing cycle: ${e.message}")
            }
            
            android.util.Log.d("TestDataCreator", "Sample data creation completed!")
            
        } catch (e: Exception) {
            android.util.Log.e("TestDataCreator", "Error creating sample data: ${e.message}")
        }
    }
    
    /**
     * Clear all test data
     */
    suspend fun clearTestData(context: Context) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("TestDataCreator", "Clearing test data...")
            
            val billingCycleRepository = BillingCycleRepository(context)
            
            // Clear all milk collections using DAO directly
            val db = com.example.doodhsethu.data.models.DatabaseManager.getDatabase(context)
            db.dailyMilkCollectionDao().deleteAllDailyMilkCollections()
            
            // Clear all billing cycles
            val billingCycles = billingCycleRepository.getAllBillingCycles().first()
            for (cycle in billingCycles) {
                billingCycleRepository.deleteBillingCycle(cycle)
            }
            
            android.util.Log.d("TestDataCreator", "Test data cleared!")
            
        } catch (e: Exception) {
            android.util.Log.e("TestDataCreator", "Error clearing test data: ${e.message}")
        }
    }
}
