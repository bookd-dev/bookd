package com.bookd.routes

import com.bookd.domain.service.ImageDimensionMigrationService
import com.bookd.extension.respondSuccess
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get

fun Route.adminRoutes() {
    route("/api/admin") {
        // 初次迁移：为所有缺失宽高的图片添加尺寸信息
        post("/migrate-image-dimensions") {
            val migrationService = get<ImageDimensionMigrationService>(ImageDimensionMigrationService::class.java)
            
            // Migrate document resources
            val resourcesResult = migrationService.migrateResourceDimensions()
            
            // Migrate book covers
            val coversResult = migrationService.migrateCoverDimensions()
            
            val response = com.bookd.domain.service.MigrationResponse(
                resourcesMigrated = resourcesResult.successCount,
                resourcesFailed = resourcesResult.failedCount,
                coversMigrated = coversResult.successCount,
                coversFailed = coversResult.failedCount,
                totalSuccess = resourcesResult.successCount + coversResult.successCount,
                totalFailed = resourcesResult.failedCount + coversResult.failedCount
            )
            
            call.respondSuccess(response)
        }
        
        // 重试失败的迁移：重新尝试提取之前失败的图片尺寸
        post("/retry-failed-image-dimensions") {
            val migrationService = get<ImageDimensionMigrationService>(ImageDimensionMigrationService::class.java)
            
            val response = migrationService.retryFailedMigrations()
            
            call.respondSuccess(response)
        }
    }
}
