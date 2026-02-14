package com.example.pitwise.di

import com.example.pitwise.data.importer.BaseDataValidator
import com.example.pitwise.data.importer.ExcelBaseDataParser
import com.example.pitwise.domain.calculator.CoalTonnageCalculator
import com.example.pitwise.domain.calculator.CutFillCalculator
import com.example.pitwise.domain.calculator.DelayLossCalculator
import com.example.pitwise.domain.calculator.HaulingCycleCalculator
import com.example.pitwise.domain.calculator.MatchFactorCalculator
import com.example.pitwise.domain.calculator.ObVolumeCalculator
import com.example.pitwise.domain.calculator.ProductivityCalculator
import com.example.pitwise.domain.calculator.RoadGradeCalculator
import com.example.pitwise.domain.map.DxfParser
import com.example.pitwise.domain.ruleengine.AdvisorEngine
import com.example.pitwise.domain.ruleengine.FishboneEngine
import com.example.pitwise.domain.reporting.ShareCardGenerator
import com.example.pitwise.domain.reporting.ShareIntentHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing all domain-layer calculators, engines, and reporting utilities.
 * All instances are singletons — stateless, pure calculation objects.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    // ── Calculators ───────────────────────────────────
    @Provides
    @Singleton
    fun provideObVolumeCalculator(): ObVolumeCalculator = ObVolumeCalculator()

    @Provides
    @Singleton
    fun provideCoalTonnageCalculator(): CoalTonnageCalculator = CoalTonnageCalculator()

    @Provides
    @Singleton
    fun provideHaulingCycleCalculator(): HaulingCycleCalculator = HaulingCycleCalculator()

    @Provides
    @Singleton
    fun provideRoadGradeCalculator(): RoadGradeCalculator = RoadGradeCalculator()

    @Provides
    @Singleton
    fun provideCutFillCalculator(): CutFillCalculator = CutFillCalculator()

    @Provides
    @Singleton
    fun provideProductivityCalculator(): ProductivityCalculator = ProductivityCalculator()

    @Provides
    @Singleton
    fun provideMatchFactorCalculator(): MatchFactorCalculator = MatchFactorCalculator()

    @Provides
    @Singleton
    fun provideDelayLossCalculator(): DelayLossCalculator = DelayLossCalculator()

    // ── Engines ───────────────────────────────────────
    @Provides
    @Singleton
    fun provideFishboneEngine(): FishboneEngine = FishboneEngine()

    @Provides
    @Singleton
    fun provideAdvisorEngine(): AdvisorEngine = AdvisorEngine()

    // ── Map ───────────────────────────────────────────
    @Provides
    @Singleton
    fun provideDxfParser(): DxfParser = DxfParser()

    // ── Reporting ─────────────────────────────────────
    @Provides
    @Singleton
    fun provideShareCardGenerator(): ShareCardGenerator = ShareCardGenerator()

    @Provides
    @Singleton
    fun provideShareIntentHelper(): ShareIntentHelper = ShareIntentHelper()

    // ── Import Engine ─────────────────────────────────
    @Provides
    @Singleton
    fun provideExcelBaseDataParser(): ExcelBaseDataParser = ExcelBaseDataParser()

    @Provides
    @Singleton
    fun provideBaseDataValidator(): BaseDataValidator = BaseDataValidator()
}

