package com.example.pitwise.domain.repository

import com.example.pitwise.data.local.entity.UnitBrand
import com.example.pitwise.data.local.entity.UnitModel
import com.example.pitwise.data.local.entity.UnitSpec
import com.example.pitwise.domain.model.UnitModelWithBrand
import kotlinx.coroutines.flow.Flow

interface UnitRepository {

    // Brands
    fun getAllBrands(): Flow<List<UnitBrand>>
    fun getBrandsByCategory(category: String): Flow<List<UnitBrand>>

    // Models
    fun getAllModels(): Flow<List<UnitModel>>
    fun getModelsByBrand(brandId: Long): Flow<List<UnitModel>>

    // Models with brand name (for picker UI)
    suspend fun getModelsWithBrandNames(): List<UnitModelWithBrand>
    suspend fun getModelsByUnitType(type: com.example.pitwise.domain.model.UnitType): List<UnitModelWithBrand>

    // Materials
    suspend fun getMaterials(): List<String>

    // Specs
    fun getSpecByModel(modelId: Long): Flow<UnitSpec?>
    suspend fun getSpecByModelSync(modelId: Long): UnitSpec?
    suspend fun findUnitSpec(type: com.example.pitwise.domain.model.UnitType, className: String, material: String): UnitSpec?

    // Local Override operations
    suspend fun copyToLocalOverride(modelId: Long): Long
    suspend fun saveLocalSpec(spec: UnitSpec)
    suspend fun deleteSpec(specId: Long)

    // Publish to global (superadmin only)
    suspend fun publishToGlobal(modelId: Long): Result<Unit>
}
