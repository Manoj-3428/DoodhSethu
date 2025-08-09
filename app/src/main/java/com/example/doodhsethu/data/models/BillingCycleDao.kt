package com.example.doodhsethu.data.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BillingCycleDao {
    @Query("SELECT * FROM billing_cycles ORDER BY startDate DESC")
    fun getAllBillingCycles(): Flow<List<BillingCycle>>

    @Query("SELECT * FROM billing_cycles WHERE startDate <= :date AND endDate >= :date")
    suspend fun getBillingCycleForDate(date: java.util.Date): BillingCycle?

    @Query("SELECT * FROM billing_cycles WHERE ((startDate <= :startDate AND endDate >= :startDate) OR (startDate <= :endDate AND endDate >= :endDate) OR (startDate >= :startDate AND endDate <= :endDate))")
    suspend fun getOverlappingBillingCycles(startDate: java.util.Date, endDate: java.util.Date): List<BillingCycle>

    @Query("SELECT * FROM billing_cycles WHERE isSynced = 0")
    suspend fun getUnsyncedBillingCycles(): List<BillingCycle>

    @Query("UPDATE billing_cycles SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markBillingCyclesAsSynced(ids: List<String>)

    @Query("UPDATE billing_cycles SET isSynced = 1 WHERE id = :id")
    suspend fun markBillingCycleAsSynced(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillingCycle(billingCycle: BillingCycle)

    @Update
    suspend fun updateBillingCycle(billingCycle: BillingCycle)

    @Delete
    suspend fun deleteBillingCycle(billingCycle: BillingCycle)

    @Query("SELECT * FROM billing_cycles WHERE id = :id")
    suspend fun getBillingCycleById(id: String): BillingCycle?
}

@Dao
interface FarmerBillingDetailDao {
    @Query("SELECT * FROM farmer_billing_details WHERE billingCycleId = :billingCycleId")
    suspend fun getFarmerBillingDetails(billingCycleId: String): List<FarmerBillingDetail>

    @Query("SELECT * FROM farmer_billing_details WHERE billingCycleId = :billingCycleId")
    suspend fun getFarmerBillingDetailsByBillingCycleId(billingCycleId: String): List<FarmerBillingDetail>

    @Query("SELECT * FROM farmer_billing_details ORDER BY billingCycleId DESC")
    suspend fun getAllFarmerBillingDetails(): List<FarmerBillingDetail>

    @Query("SELECT * FROM farmer_billing_details WHERE farmerId = :farmerId ORDER BY billingCycleId DESC")
    fun getFarmerBillingDetailsByFarmer(farmerId: String): Flow<List<FarmerBillingDetail>>

    @Query("SELECT * FROM farmer_billing_details WHERE farmerId = :farmerId AND billingCycleId = :billingCycleId")
    suspend fun getFarmerBillingDetail(farmerId: String, billingCycleId: String): FarmerBillingDetail?

    @Query("SELECT * FROM farmer_billing_details WHERE isSynced = 0")
    suspend fun getUnsyncedFarmerBillingDetails(): List<FarmerBillingDetail>

    @Query("UPDATE farmer_billing_details SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markFarmerBillingDetailsAsSynced(ids: List<String>)

    @Query("UPDATE farmer_billing_details SET isSynced = 1 WHERE id = :id")
    suspend fun markFarmerBillingDetailsAsSynced(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarmerBillingDetail(farmerBillingDetail: FarmerBillingDetail)

    @Update
    suspend fun updateFarmerBillingDetail(farmerBillingDetail: FarmerBillingDetail)

    @Delete
    suspend fun deleteFarmerBillingDetail(farmerBillingDetail: FarmerBillingDetail)
} 