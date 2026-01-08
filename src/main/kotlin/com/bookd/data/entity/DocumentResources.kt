package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object DocumentResources : IntIdTable("document_resources") {
    val bookId = reference("book_id", Books, onDelete = ReferenceOption.CASCADE)
    val path = varchar("path", 500) // 资源路径（相对于书籍内部）
    val storedPath = varchar("stored_path", 500) // 存储路径（服务器文件系统）
    val mediaType = varchar("media_type", 100) // MIME 类型
    val size = long("size").default(0) // 文件大小
    
    init {
        uniqueIndex(bookId, path)
    }
}
