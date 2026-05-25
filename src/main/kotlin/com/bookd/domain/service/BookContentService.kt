package com.bookd.domain.service

import com.bookd.data.repository.BookDocumentRepository
import com.bookd.data.repository.BookDocumentDraft
import com.bookd.data.repository.BookRepository
import com.bookd.data.repository.DocumentContentDraft
import com.bookd.data.repository.DocumentResourceDraft
import com.bookd.data.repository.ReadingProgressRepository
import com.bookd.data.repository.ResourceInfo
import com.bookd.domain.model.*
import com.bookd.domain.service.parser.*
import com.bookd.infrastructure.cache.BookCacheService
import com.bookd.infrastructure.storage.BookImageStorage
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File

class BookContentService(
    private val bookRepository: BookRepository,
    private val documentRepository: BookDocumentRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val txtParser: TxtParser,
    private val imageStorage: BookImageStorage,
    private val cacheService: BookCacheService?,
    private val taskCoordinator: BookTaskCoordinator = BookTaskCoordinator()
) {
    private val logger = LoggerFactory.getLogger(BookContentService::class.java)
    
    // 解析器工厂
    private val parserFactory = ParserFactory(txtParser)
    
    /**
     * 异步解析书籍内容
     */
    fun parseBookContentAsync(bookId: Int, filePath: String) {
        val launched = taskCoordinator.launchContentParse(bookId) {
            try {
                logger.info("Starting content parsing for book ID: $bookId")
                // 更新状态为正在解析
                bookRepository.updateParseStatus(bookId, "parsing", 0)
                when (val result = parseBookContent(bookId, filePath)) {
                    is ContentParseResult.Success -> {
                        logger.info("Completed content parsing for book ID: $bookId, documents: ${result.documentCount}")
                    }
                    is ContentParseResult.Failed -> {
                        logger.warn("Content parsing did not complete for book ID: $bookId: ${result.reason}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to parse book content for ID: $bookId", e)
                bookRepository.updateParseStatus(bookId, "failed", 0)
            }
        }

        if (!launched) {
            logger.warn("Book ID $bookId is already being parsed or coordinator is closed, skipping")
        }
    }
    
    /**
     * 按需解析：检查缓存和数据库，如果需要则同步解析
     * 用于用户打开书籍时
     */
    suspend fun parseOnDemand(bookId: Int, filePath: String): Boolean {
        // 1. 先检查 Redis 缓存
        val cachedParsed = cacheService?.isBookParsed(bookId)
        if (cachedParsed == true) {
            logger.debug("Book $bookId already parsed (from cache)")
            return true
        }
        
        // 2. 检查数据库状态
        val book = bookRepository.findById(bookId)
        if (book == null) {
            logger.warn("Book not found: $bookId")
            return false
        }
        
        if (book.chaptersParsed) {
            // 更新缓存
            cacheService?.setBookParsed(bookId, true)
            logger.debug("Book $bookId already parsed (from database)")
            return true
        }
        
        // 3. 尝试获取分布式锁
        val lockAcquired = cacheService?.tryAcquireParseLock(bookId) ?: true
        if (!lockAcquired) {
            logger.info("Book $bookId is being parsed by another process, waiting...")
            // 等待一段时间后重新检查
            delay(2000)
            val recheckBook = bookRepository.findById(bookId)
            return recheckBook?.chaptersParsed ?: false
        }
        
        return try {
            logger.info("Parsing book on-demand: $bookId")
            
            // 4. 更新状态为正在解析
            bookRepository.updateParseStatus(bookId, "parsing", 0)
            
            // 5. 同步解析（用户等待）
            when (val result = parseBookContent(bookId, filePath)) {
                is ContentParseResult.Success -> {
                    cacheService?.setBookParsed(bookId, true)
                    logger.info("Book $bookId parsed successfully, documents: ${result.documentCount}")
                    true
                }
                is ContentParseResult.Failed -> {
                    logger.warn("Book $bookId parse failed: ${result.reason}")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse book on-demand: $bookId", e)
            bookRepository.updateParseStatus(bookId, "failed", 0)
            false
        } finally {
            // 7. 释放锁
            cacheService?.releaseParseLock(bookId)
        }
    }
    
    /**
     * 强制重新解析：清除缓存和现有数据，重新解析书籍内容
     * 用于用户手动触发重新解析
     */
    fun queueForReparse(bookId: Int): Boolean {
        val book = bookRepository.findById(bookId)
        if (book == null) {
            logger.warn("Book not found for reparse: $bookId")
            return false
        }
        
        // 检查是否正在解析
        if (taskCoordinator.isContentParseInProgress(bookId)) {
            logger.info("Book $bookId is already being parsed")
            return false
        }
        
        // 清除所有相关缓存
        cacheService?.clearBookCache(bookId)
        
        // 重置解析状态
        bookRepository.updateParseStatus(bookId, "pending", 0)
        bookRepository.updateChaptersParsed(bookId, 0)
        
        // 异步开始解析
        parseBookContentAsync(bookId, book.filePath)
        
        logger.info("Book $bookId queued for reparse")
        return true
    }
    
    /**
     * 解析书籍内容（同步）- 使用工厂模式统一处理
     */
    private suspend fun parseBookContent(bookId: Int, filePath: String): ContentParseResult {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            logger.warn("File does not exist: $filePath")
            bookRepository.updateParseStatus(bookId, "failed", 0)
            return ContentParseResult.Failed("file_missing")
        }
        
        // 使用工厂创建对应格式的解析器
        val parser = parserFactory.createParser(file)
        if (parser == null) {
            logger.warn("Unsupported format: ${file.extension}")
            bookRepository.updateParseStatus(bookId, "failed", 0)
            return ContentParseResult.Failed("unsupported_format")
        }
        
        return withContext(Dispatchers.IO) {
            try {
                parseWithParser(bookId, file, parser)
            } catch (e: Exception) {
                logger.error("Error parsing book content: ${file.name}", e)
                bookRepository.updateParseStatus(bookId, "failed", 0)
                ContentParseResult.Failed("unexpected_error")
            }
        }
    }
    
    /**
     * 使用解析器解析书籍内容（统一流程）
     */
    private suspend fun parseWithParser(bookId: Int, file: File, parser: BookParser): ContentParseResult {
        logger.info("Parsing ${file.extension.uppercase()} content for book ID: $bookId")
        
        // 1. 解析书籍结构
        val structure = parser.parseStructure(file) ?: run {
            logger.error("Failed to parse book structure")
            bookRepository.updateParseStatus(bookId, "failed", 0)
            return ContentParseResult.Failed("structure_parse_failed")
        }

        if (structure.chapters.isEmpty()) {
            logger.error("Parsed book structure has no chapters for book ID: $bookId")
            bookRepository.updateParseStatus(bookId, "failed", 0)
            return ContentParseResult.Failed("no_chapters")
        }
        
        // 2. 清理旧数据并创建文档
        try {
            logger.info("Replacing ${structure.chapters.size} documents for book ID: $bookId")
            val documentsByIndex = documentRepository.replaceDocumentsForBook(
                bookId = bookId,
                documents = structure.chapters.map { chapterInfo ->
                    BookDocumentDraft(
                        index = chapterInfo.index,
                        href = chapterInfo.href,
                        inToc = chapterInfo.inToc,
                        title = chapterInfo.title,
                        level = chapterInfo.level
                    )
                }
            )
            logger.info("Successfully created ${documentsByIndex.size} documents for book ID: $bookId")
            
            // 3. 解析并保存文档内容（在事务外，避免长事务）
            val contentDrafts = mutableListOf<DocumentContentDraft>()
            structure.chapters.forEach { chapterInfo ->
                val document = documentsByIndex[chapterInfo.index]
                if (document == null) {
                    logger.error("Document not found after creation: bookId=$bookId, index=${chapterInfo.index}")
                    return@forEach
                }
                parseDocumentContent(file, parser, chapterInfo, document.id)?.let { contentDrafts.add(it) }
            }

            if (contentDrafts.isEmpty()) {
                logger.error("No chapter content parsed for book ID: $bookId")
                bookRepository.updateParseStatus(bookId, "failed", 0)
                return ContentParseResult.Failed("no_chapter_content")
            }

            documentRepository.replaceDocumentContentsAndStats(contentDrafts)
            
            // 4. 保存资源文件（如果有）
            if (structure.resources.isNotEmpty()) {
                saveResources(bookId, structure.resources)
            }
            
            // 5. 更新书籍解析状态
            bookRepository.updateChaptersParsed(
                id = bookId,
                chaptersCount = structure.chapters.size,
                tocChapterCount = structure.chapters.count { it.inToc },
                totalWordCount = contentDrafts.sumOf { it.wordCount },
                totalImageCount = contentDrafts.sumOf { it.imageCount }
            )
            
            logger.info("Parsing completed for book ID: $bookId - ${structure.chapters.size} chapters")
            return ContentParseResult.Success(documentCount = documentsByIndex.size)
        } catch (e: Exception) {
            logger.error("Failed to parse book content for book ID: $bookId", e)
            bookRepository.updateParseStatus(bookId, "failed", 0)
            return ContentParseResult.Failed("save_failed")
        }
    }
    
    /**
     * 解析并保存单个文档内容
     */
    private suspend fun parseDocumentContent(
        file: File,
        parser: BookParser,
        chapterInfo: BookParser.ChapterInfo,
        documentId: Int
    ): DocumentContentDraft? {
        return try {
            // 解析文档内容
            val elements = parser.parseChapterContent(file, chapterInfo)
            
            // 统计字数和图片数
            val wordCount = parser.countWords(elements)
            val imageCount = parser.countImages(elements)
            
            logger.debug("Parsed document ${chapterInfo.index}: ${chapterInfo.title} ($wordCount words, $imageCount images)")
            DocumentContentDraft(
                documentId = documentId,
                elements = elements,
                wordCount = wordCount,
                imageCount = imageCount
            )
        } catch (e: Exception) {
            logger.error("Failed to parse document ${chapterInfo.index}", e)
            null
        }
    }
    
    /**
     * 保存资源文件（使用新的图片存储服务）
     */
    private fun saveResources(bookId: Int, resources: Map<String, ByteArray>) {
        val resourceDrafts = mutableListOf<DocumentResourceDraft>()
        resources.forEach { (path, bytes) ->
            try {
                // 使用 BookImageStorage 保存图片
                val storedPath = imageStorage.saveImage(bookId, path, bytes)
                val extension = path.substringAfterLast(".")
                val mediaType = imageStorage.detectMediaType(extension)
                
                // 提取图片尺寸
                val dimensions = imageStorage.extractImageDimensions(bytes)
                val (width, height) = dimensions ?: (null to null)
                
                resourceDrafts.add(
                    DocumentResourceDraft(
                    bookId = bookId,
                    path = path,
                    storedPath = storedPath,  // 存储相对路径，如 "14/abc123.jpg"
                    mediaType = mediaType,
                    size = bytes.size.toLong(),
                    width = width,
                    height = height
                    )
                )
                
                if (width != null && height != null) {
                    logger.debug("Saved resource: $path -> $storedPath (${width}x${height})")
                } else {
                    logger.debug("Saved resource: $path -> $storedPath (no dimensions)")
                }
            } catch (e: Exception) {
                logger.error("Failed to save resource: $path", e)
            }
        }
        documentRepository.saveResources(resourceDrafts)
    }
    
    /**
     * 获取书籍清单
     */
    suspend fun getBookManifest(bookId: Int): BookManifest? {
        val book = bookRepository.findByIdAsync(bookId) ?: return null
        val allDocuments = documentRepository.findByBookIdAsync(bookId)
        
        if (allDocuments.isEmpty()) {
            logger.warn("No documents found for book ID: $bookId")
            return null
        }
        
        // 目录项：仅 inToc=true
        val tocDocuments = allDocuments.filter { it.inToc }
        
        // 构建目录树
        val toc = buildTocTree(tocDocuments)
        
        return BookManifest(
            id = book.id,
            title = book.title,
            author = book.author,
            format = book.format,
            totalChapters = tocDocuments.size,
            toc = toc,
            spine = allDocuments.map { it.index }, // 完整阅读顺序
            metadata = BookMetadata(
                publisher = book.publisher,
                language = null,
                publishDate = null,
                description = book.description,
                isbn = book.isbn
            )
        )
    }
    
    /**
     * 获取带阅读进度的书籍清单
     * 根据用户的阅读进度，为每个目录项填充 readStatus 和 readProgress
     * 
     * @param bookId 书籍ID
     * @param userId 用户ID
     * @return 带阅读进度的 BookManifest，如果书籍不存在则返回 null
     */
    suspend fun getBookManifestWithProgress(bookId: Int, userId: Int): BookManifest? {
        val manifest = getBookManifest(bookId) ?: return null
        val progress = readingProgressRepository.findByUserAndBook(userId, bookId)
        
        // 如果没有阅读进度，直接返回原始清单
        if (progress == null) {
            return manifest
        }
        
        val enrichedToc = enrichTocWithProgress(manifest.toc, progress)
        return manifest.copy(toc = enrichedToc)
    }
    
    /**
     * 为目录项填充阅读状态和进度
     * 
     * 逻辑：
     * - 当前阅读的章节 (currentPage == index)：状态为 "reading"，进度根据 chapterPageIndex/chapterScrollPercent 计算
     * - 已跳过的章节 (index < currentPage)：状态为 "read"，进度 100%
     * - 未读章节 (index > currentPage)：状态为 "unread"，进度 0%
     */
    private fun enrichTocWithProgress(
        toc: List<TocItem>,
        progress: ReadingProgressResponse
    ): List<TocItem> {
        val currentChapterIndex = progress.currentPage // currentPage 存储的是当前章节 index
        
        return toc.map { item ->
            // 递归处理子目录
            val enrichedChildren = enrichTocWithProgress(item.children, progress)
            
            when {
                item.index == currentChapterIndex -> {
                    // 当前正在阅读的章节
                    val chapterProgress = calculateChapterProgress(progress)
                    item.copy(
                        readStatus = "reading",
                        readProgress = chapterProgress,
                        children = enrichedChildren
                    )
                }
                item.index < currentChapterIndex -> {
                    // 已经读过（或跳过）的章节
                    item.copy(
                        readStatus = "read",
                        readProgress = 1.0,
                        children = enrichedChildren
                    )
                }
                else -> {
                    // 还未读的章节
                    item.copy(
                        readStatus = "unread",
                        readProgress = 0.0,
                        children = enrichedChildren
                    )
                }
            }
        }
    }
    
    /**
     * 计算章节内的阅读进度
     * 
     * 优先使用页面模式的进度（chapterPageIndex / chapterTotalPages）
     * 如果没有则使用滚动模式的进度（chapterScrollPercent）
     * 
     * @return 0.0-1.0 的进度值
     */
    private fun calculateChapterProgress(progress: ReadingProgressResponse): Double {
        // 页面模式：使用 chapterPageIndex / chapterTotalPages
        val totalPages = progress.chapterTotalPages
        if (totalPages != null && totalPages > 0) {
            val pageIndex = progress.chapterPageIndex ?: 0
            // pageIndex 从 0 开始，当前页为 pageIndex，已读完 pageIndex 页
            // 进度 = (pageIndex + 1) / totalPages
            return ((pageIndex + 1).toDouble() / totalPages).coerceIn(0.0, 1.0)
        }
        
        // 滚动模式：直接使用 chapterScrollPercent
        val scrollPercent = progress.chapterScrollPercent
        if (scrollPercent != null) {
            return scrollPercent.coerceIn(0.0, 1.0)
        }
        
        // 如果都没有，返回 0
        return 0.0
    }
    
    /**
     * 获取文档内容
     * @param baseUrl 可选的基础 URL，用于构建完整的图片 URL
     */
    suspend fun getChapterContent(bookId: Int, index: Int, baseUrl: String? = null): ChapterContent? {
        val document = documentRepository.findByBookIdAndIndexAsync(bookId, index) ?: return null
        val elements = documentRepository.getDocumentContentAsync(document.id) ?: emptyList()
        val resourcesByPath = documentRepository.findResourcesByBookIdAndPaths(bookId, collectImagePaths(elements))
        
        // 转换图片路径为可访问的URL
        val transformedElements = elements.map { element ->
            transformElement(element, resourcesByPath, baseUrl)
        }
        
        val adjacentIndexes = documentRepository.findAdjacentIndexes(bookId, index)
        
        return ChapterContent(
            index = index,
            title = document.title,
            elements = transformedElements,
            prevIndex = adjacentIndexes.prevIndex,
            nextIndex = adjacentIndexes.nextIndex
        )
    }

    suspend fun getChapterList(bookId: Int): ChapterListResult {
        val book = bookRepository.findByIdAsync(bookId) ?: return ChapterListResult.NotFound
        if (!book.chaptersParsed) {
            val parsed = try {
                parseOnDemand(bookId, book.filePath)
            } catch (e: Exception) {
                logger.error("Failed to parse chapters for book ID: $bookId", e)
                false
            }
            if (!parsed) return ChapterListResult.ParseFailed
        }

        return ChapterListResult.Success(documentRepository.findTocByBookIdAsync(bookId))
    }
    
    /**
     * 转换 ContentElement 中的图片路径
     */
    private fun transformElement(
        element: ContentElement,
        resourcesByPath: Map<String, ResourceInfo>,
        baseUrl: String?
    ): ContentElement {
        return when (element) {
            is ContentElement.Image -> {
                val resource = resourcesByPath[element.src]
                if (resource != null) {
                    val imagePath = "/book_images/${resource.storedPath}"
                    val fullPath = if (baseUrl != null) "$baseUrl$imagePath" else imagePath
                    
                    // 计算宽高比
                    val aspectRatio = if (resource.width != null && resource.height != null && resource.height > 0) {
                        resource.width.toDouble() / resource.height
                    } else {
                        null
                    }
                    
                    element.copy(
                        src = fullPath,
                        width = resource.width,
                        height = resource.height,
                        aspectRatio = aspectRatio
                    )
                } else {
                    element
                }
            }
            is ContentElement.Paragraph -> {
                element.copy(spans = element.spans.map { transformSpan(it) })
            }
            is ContentElement.Quote -> {
                element.copy(spans = element.spans.map { transformSpan(it) })
            }
            is ContentElement.ListBlock -> {
                element.copy(items = element.items.map { item ->
                    ListItem(item.spans.map { transformSpan(it) })
                })
            }
            is ContentElement.Footnote -> {
                // 转换脚注内容文本
                val transformedContentSpans = element.contentSpans.map { transformSpan(it) }
                
                // 转换脚注图片路径和尺寸
                if (element.footnoteImage != null) {
                    val resource = resourcesByPath[element.footnoteImage]
                    if (resource != null) {
                        val imagePath = "/book_images/${resource.storedPath}"
                        val fullPath = if (baseUrl != null) "$baseUrl$imagePath" else imagePath
                        
                        // 计算宽高比
                        val aspectRatio = if (resource.width != null && resource.height != null && resource.height > 0) {
                            resource.width.toDouble() / resource.height
                        } else {
                            null
                        }
                        
                        element.copy(
                            footnoteImage = fullPath,
                            width = resource.width,
                            height = resource.height,
                            aspectRatio = aspectRatio,
                            contentSpans = transformedContentSpans
                        )
                    } else {
                        element.copy(contentSpans = transformedContentSpans)
                    }
                } else {
                    element.copy(contentSpans = transformedContentSpans)
                }
            }
            else -> element
        }
    }
    
    /**
     * 转换 TextSpan（目前只是占位符，未来可能需要转换其他属性）
     */
    private fun transformSpan(span: TextSpan): TextSpan {
        // TextSpan 不再包含 footnoteImage，直接返回
        return span
    }

    private fun collectImagePaths(elements: List<ContentElement>): Set<String> {
        val paths = linkedSetOf<String>()
        elements.forEach { element ->
            when (element) {
                is ContentElement.Image -> paths.add(element.src)
                is ContentElement.Footnote -> element.footnoteImage?.let { paths.add(it) }
                else -> Unit
            }
        }
        return paths
    }
    
    /**
     * 构建目录树
     */
    private fun buildTocTree(documents: List<BookDocument>): List<TocItem> {
        val rootItems = mutableListOf<TocItem>()
        val stack = mutableListOf<Pair<Int, MutableList<TocItem>>>() // (level, children list)
        
        documents.forEach { document ->
            val tocItem = TocItem(
                index = document.index,
                title = document.title ?: "第${document.index + 1}章",
                level = document.level,
                wordCount = document.wordCount,
                imageCount = document.imageCount,
                children = mutableListOf()
            )
            
            when {
                document.level == 0 -> {
                    rootItems.add(tocItem)
                    stack.clear()
                    stack.add(document.level to (tocItem.children as MutableList))
                }
                document.level > 0 && stack.isNotEmpty() -> {
                    // 找到合适的父级
                    while (stack.isNotEmpty() && stack.last().first >= document.level) {
                        stack.removeLast()
                    }
                    
                    if (stack.isNotEmpty()) {
                        stack.last().second.add(tocItem)
                        stack.add(document.level to (tocItem.children as MutableList))
                    } else {
                        rootItems.add(tocItem)
                        stack.add(document.level to (tocItem.children as MutableList))
                    }
                }
            }
        }
        
        return rootItems
    }
    
    /**
     * 关闭服务
     */
    fun shutdown() {
        taskCoordinator.close()
    }
}

sealed class ChapterListResult {
    data class Success(val documents: List<BookDocument>) : ChapterListResult()
    data object NotFound : ChapterListResult()
    data object ParseFailed : ChapterListResult()
}

private sealed class ContentParseResult {
    data class Success(val documentCount: Int) : ContentParseResult()
    data class Failed(val reason: String) : ContentParseResult()
}
