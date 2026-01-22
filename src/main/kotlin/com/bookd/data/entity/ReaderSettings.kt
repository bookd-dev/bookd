package com.bookd.data.entity

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object ReaderSettings : IntIdTable("reader_settings") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    
    // 字体设置
    val fontFamily = varchar("font_family", 100).default("system-ui")
    val fontSize = integer("font_size").default(16)
    val fontWeight = integer("font_weight").default(400)
    
    // 排版设置
    val lineHeight = decimal("line_height", 3, 2).default(java.math.BigDecimal("1.60"))
    val letterSpacing = decimal("letter_spacing", 3, 2).default(java.math.BigDecimal.ZERO)
    val paragraphSpacing = integer("paragraph_spacing").default(16)
    val textAlign = varchar("text_align", 20).default("justify")
    
    // 阅读模式
    val pageMode = varchar("page_mode", 20).default("scroll")
    val brightness = integer("brightness").default(100)
    val pageAnimationType = varchar("page_animation_type", 20).default("native")
    
    // 边距
    val marginHorizontal = integer("margin_horizontal").default(20)
    val marginVertical = integer("margin_vertical").default(40)
    
    // 首行缩进
    val firstLineIndent = bool("first_line_indent").default(true)
    
    val updatedAt = datetime("updated_at")
}
