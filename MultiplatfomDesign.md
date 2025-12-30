现在我对你的项目结构有了清晰的了解。基于你的 Kotlin + Ktor + Exposed 技术栈，给你一个完整的设计方案：

   -------------------------------------------------------------------------------------------------------------------

📖 电子书阅读功能后端设计方案

一、功能模块概览

┌────────────┬──────────────────────────────────────────────────┐
│ 模块       │ 说明                                             │
├────────────┼──────────────────────────────────────────────────┤
│ 阅读进度   │ 跨端同步当前阅读位置（章节/页码/百分比/CFI定位） │
├────────────┼──────────────────────────────────────────────────┤
│ 书签       │ 标记特定位置，支持备注                           │
├────────────┼──────────────────────────────────────────────────┤
│ 阅读器样式 │ 字体、字号、主题、行距等个人偏好设置             │
└────────────┴──────────────────────────────────────────────────┘

   -------------------------------------------------------------------------------------------------------------------

二、数据库表设计

1. 阅读进度表（增强现有）

     -- 建议改造现有 reading_progress 表
     ALTER TABLE reading_progress ADD COLUMN cfi_location VARCHAR(500);  -- EPUB CFI定位
     ALTER TABLE reading_progress ADD COLUMN chapter_id VARCHAR(100);    -- 章节标识
     ALTER TABLE reading_progress ADD COLUMN device_id VARCHAR(100);     -- 设备标识
     ALTER TABLE reading_progress ADD COLUMN total_pages INTEGER;        -- 总页数(PDF)

2. 新增书签表

     CREATE TABLE bookmarks (
         id SERIAL PRIMARY KEY,
         user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
         book_id INTEGER REFERENCES books(id) ON DELETE CASCADE,
         position_type VARCHAR(20) NOT NULL,    -- 'cfi' | 'page' | 'percentage'
         position_value VARCHAR(500) NOT NULL,  -- CFI字符串 或 页码 或 百分比
         chapter_id VARCHAR(100),               -- 章节标识
         title VARCHAR(255),                    -- 书签标题（可选）
         note TEXT,                             -- 书签备注
         color VARCHAR(20) DEFAULT '#FFD700',   -- 书签颜色
         created_at TIMESTAMP NOT NULL,

         UNIQUE(user_id, book_id, position_value)
     );

3. 新增阅读器设置表

     CREATE TABLE reader_settings (
         id SERIAL PRIMARY KEY,
         user_id INTEGER REFERENCES users(id) ON DELETE CASCADE UNIQUE,

         -- 字体设置
         font_family VARCHAR(100) DEFAULT 'system-ui',
         font_size INTEGER DEFAULT 16,            -- px
         font_weight INTEGER DEFAULT 400,

         -- 排版设置
         line_height DECIMAL(3,2) DEFAULT 1.6,
         letter_spacing DECIMAL(3,2) DEFAULT 0,
         paragraph_spacing INTEGER DEFAULT 16,    -- px
         text_align VARCHAR(20) DEFAULT 'justify', -- left|right|center|justify

         -- 主题设置
         theme VARCHAR(20) DEFAULT 'light',       -- light|dark|sepia|custom
         background_color VARCHAR(20) DEFAULT '#FFFFFF',
         text_color VARCHAR(20) DEFAULT '#333333',

         -- 阅读模式
         page_mode VARCHAR(20) DEFAULT 'scroll',  -- scroll|paginated
         brightness INTEGER DEFAULT 100,          -- 0-100

         -- 边距
         margin_horizontal INTEGER DEFAULT 20,    -- px
         margin_vertical INTEGER DEFAULT 40,      -- px

         updated_at TIMESTAMP NOT NULL
     );

   -------------------------------------------------------------------------------------------------------------------

三、Kotlin 实体类设计

     src/main/kotlin/com/bookd/data/entity/
     ├── ReadingProgress.kt  (已存在，需增强)
     ├── Bookmarks.kt        (新增)
     └── ReaderSettings.kt   (新增)

   -------------------------------------------------------------------------------------------------------------------

四、API 接口设计

阅读进度 API

┌────────┬──────────────────────────────┬──────────────┐
│ Method │ Path                         │ 说明         │
├────────┼──────────────────────────────┼──────────────┤
│ GET    │ /api/books/{bookId}/progress │ 获取阅读进度 │
├────────┼──────────────────────────────┼──────────────┤
│ PUT    │ /api/books/{bookId}/progress │ 更新阅读进度 │
├────────┼──────────────────────────────┼──────────────┤
│ GET    │ /api/user/reading-history    │ 最近阅读列表 │
└────────┴──────────────────────────────┴──────────────┘

书签 API

┌────────┬───────────────────────────────┬──────────────────┐
│ Method │ Path                          │ 说明             │
├────────┼───────────────────────────────┼──────────────────┤
│ GET    │ /api/books/{bookId}/bookmarks │ 获取书籍所有书签 │
├────────┼───────────────────────────────┼──────────────────┤
│ POST   │ /api/books/{bookId}/bookmarks │ 添加书签         │
├────────┼───────────────────────────────┼──────────────────┤
│ PUT    │ /api/bookmarks/{id}           │ 更新书签         │
├────────┼───────────────────────────────┼──────────────────┤
│ DELETE │ /api/bookmarks/{id}           │ 删除书签         │
├────────┼───────────────────────────────┼──────────────────┤
│ GET    │ /api/user/bookmarks           │ 用户所有书签     │
└────────┴───────────────────────────────┴──────────────────┘

阅读器设置 API

┌────────┬───────────────────────────┬──────────┐
│ Method │ Path                      │ 说明     │
├────────┼───────────────────────────┼──────────┤
│ GET    │ /api/user/reader-settings │ 获取设置 │
├────────┼───────────────────────────┼──────────┤
│ PUT    │ /api/user/reader-settings │ 更新设置 │
├────────┼───────────────────────────┼──────────┤
│ PATCH  │ /api/user/reader-settings │ 部分更新 │
└────────┴───────────────────────────┴──────────┘

   -------------------------------------------------------------------------------------------------------------------

五、核心 DTO 设计

     // 阅读进度
     @Serializable
     data class ReadingProgressDTO(
         val bookId: Int,
         val progress: Double,           // 0.0 - 100.0
         val currentPage: Int?,
         val totalPages: Int?,
         val cfiLocation: String?,       // EPUB 定位
         val chapterId: String?,
         val deviceId: String?,
         val lastReadAt: String
     )

     // 书签
     @Serializable
     data class BookmarkDTO(
         val id: Int? = null,
         val bookId: Int,
         val positionType: String,       // cfi | page | percentage
         val positionValue: String,
         val chapterId: String? = null,
         val title: String? = null,
         val note: String? = null,
         val color: String = "#FFD700",
         val createdAt: String? = null
     )

     // 阅读器设置
     @Serializable
     data class ReaderSettingsDTO(
         val fontFamily: String = "system-ui",
         val fontSize: Int = 16,
         val fontWeight: Int = 400,
         val lineHeight: Double = 1.6,
         val letterSpacing: Double = 0.0,
         val paragraphSpacing: Int = 16,
         val textAlign: String = "justify",
         val theme: String = "light",
         val backgroundColor: String = "#FFFFFF",
         val textColor: String = "#333333",
         val pageMode: String = "scroll",
         val brightness: Int = 100,
         val marginHorizontal: Int = 20,
         val marginVertical: Int = 40
     )

   -------------------------------------------------------------------------------------------------------------------

六、跨端同步策略

     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
     │   iOS App   │     │ Android App │     │   Web App   │
     └──────┬──────┘     └──────┬──────┘     └──────┬──────┘
            │                   │                   │
            │    deviceId       │    deviceId       │    deviceId
            └───────────────────┼───────────────────┘
                                ▼
                      ┌─────────────────┐
                      │   后端 API      │
                      │  last_read_at   │
                      │  比较时间戳     │
                      └─────────────────┘

冲突解决策略: 以 last_read_at 时间戳最新的为准

   -------------------------------------------------------------------------------------------------------------------

七、实现优先级建议

┌──────┬─────────────────────────────┬────────────┐
│ 阶段 │ 任务                        │ 预估工作量 │
├──────┼─────────────────────────────┼────────────┤
│ P0   │ 增强 ReadingProgress 表结构 │ 0.5d       │
├──────┼─────────────────────────────┼────────────┤
│ P0   │ 阅读进度 CRUD API           │ 1d         │
├──────┼─────────────────────────────┼────────────┤
│ P1   │ 书签表 + API                │ 1d         │
├──────┼─────────────────────────────┼────────────┤
│ P1   │ 阅读器设置表 + API          │ 0.5d       │
├──────┼─────────────────────────────┼────────────┤
│ P2   │ 阅读历史接口                │ 0.5d       │
└──────┴─────────────────────────────┴────────────┘

   -------------------------------------------------------------------------------------------------------------------

八、文件结构规划

     src/main/kotlin/com/bookd/
     ├── data/
     │   ├── entity/
     │   │   ├── ReadingProgress.kt  ← 增强
     │   │   ├── Bookmarks.kt        ← 新增
     │   │   └── ReaderSettings.kt   ← 新增
     │   └── repository/
     │       ├── ReadingProgressRepository.kt  ← 新增
     │       ├── BookmarkRepository.kt         ← 新增
     │       └── ReaderSettingsRepository.kt   ← 新增
     ├── domain/
     │   ├── model/
     │   │   ├── ReadingProgress.kt  ← 新增 DTO
     │   │   ├── Bookmark.kt         ← 新增 DTO
     │   │   └── ReaderSettings.kt   ← 新增 DTO
     │   └── service/
     │       └── ReadingService.kt   ← 新增（统一处理）
     └── routes/
         └── ReadingRoutes.kt        ← 新增

   -------------------------------------------------------------------------------------------------------------------

需要我帮你开始实现某个模块吗？可以从 增强 ReadingProgress 实体 或 新建 Bookmarks 实体 开始。