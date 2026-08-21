package com.bookd.data.repository

import com.bookd.data.entity.Books
import com.bookd.data.entity.DocumentResources
import com.bookd.infrastructure.database.DatabaseExecutor.dbQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ImageDimensionMigrationRepository {
    suspend fun findResourcesMissingDimensions(): List<ResourceDimensionCandidate> = dbQuery {
        DocumentResources.selectAll()
            .where {
                (DocumentResources.width.isNull()) or (DocumentResources.height.isNull())
            }
            .map { row ->
                ResourceDimensionCandidate(
                    id = row[DocumentResources.id].value,
                    storedPath = row[DocumentResources.storedPath]
                )
            }
    }

    suspend fun updateResourceDimensions(updates: List<ResourceDimensionUpdate>) = dbQuery {
        updates.forEach { update ->
            DocumentResources.update({ DocumentResources.id eq update.id }) {
                it[DocumentResources.width] = update.width
                it[DocumentResources.height] = update.height
            }
        }
    }

    suspend fun findCoversMissingDimensions(): List<CoverDimensionCandidate> = dbQuery {
        Books.selectAll()
            .where {
                Books.coverPath.isNotNull() and
                    ((Books.coverWidth.isNull()) or (Books.coverHeight.isNull()))
            }
            .mapNotNull { row ->
                val coverPath = row[Books.coverPath] ?: return@mapNotNull null
                CoverDimensionCandidate(
                    bookId = row[Books.id].value,
                    coverPath = coverPath
                )
            }
    }

    suspend fun updateCoverDimensions(updates: List<CoverDimensionUpdate>) = dbQuery {
        updates.forEach { update ->
            Books.update({ Books.id eq update.bookId }) {
                it[Books.coverWidth] = update.width
                it[Books.coverHeight] = update.height
            }
        }
    }
}

data class ResourceDimensionCandidate(
    val id: Int,
    val storedPath: String
)

data class ResourceDimensionUpdate(
    val id: Int,
    val width: Int,
    val height: Int
)

data class CoverDimensionCandidate(
    val bookId: Int,
    val coverPath: String
)

data class CoverDimensionUpdate(
    val bookId: Int,
    val width: Int,
    val height: Int
)
