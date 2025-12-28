package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable

object FolderPermissions : IntIdTable("folder_permissions") {
    val userId = reference("user_id", Users)
    val folderPath = varchar("folder_path", 500)
    val canAccess = bool("can_access").default(true)
    
    init {
        uniqueIndex(userId, folderPath)
    }
}
