package com.bookd.domain.service

import com.bookd.data.repository.BookDocumentRepository
import com.bookd.data.repository.BookRepository
import com.bookd.domain.model.*
import com.bookd.domain.service.parser.*
import com.bookd.infrastructure.cache.BookCacheService
import com.bookd.infrastructure.storage.BookImageStorage
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class BookContentService(
    private val bookRepository: BookRepository,
    private val documentRepository: BookDocumentRepository,
    private val txtParser: TxtParser,
    private val imageStorage: BookImageStorage,
    private val cacheService: BookCacheService?
) {
    private val logger = LoggerFactory.getLogger(BookContentService::class.java)
    
    // 解析器工厂
    private val parserFactory = ParserFactory(txtParser)
    
    // 专用的内容解析线程池
    private val contentDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    private val scope = CoroutineScope(contentDispatcher + SupervisorJob())
    
    // 跟踪正在解析的书籍，避免重复解析
    private val parsingBooks = mutableSetOf<Int>()
    
    /**
     * 异步解析书籍内容
     */
    fun parseBookContentAsync(bookId: Int, filePath: String) {
        synchronized(parsingBooks) {
            if (parsingBooks.contains(bookId)) {
                logger.warn("Book ID $bookId is already being parsed, skipping")
                return
            }
            parsingBooks.add(bookId)
        }
        
        scope.launch {
            delay(1000) // 延迟以避免影响扫描流程
            try {
                logger.info("Starting content parsing for book ID: $bookId")
                // 更新状态为正在解析
                bookRepository.updateParseStatus(bookId, "parsing", 0)
                parseBookContent(bookId, filePath)
                logger.info("Completed content parsing for book ID: $bookId")
            } catch (e: Exception) {
                logger.error("Failed to parse book content for ID: $bookId", e)
                bookRepository.updateParseStatus(bookId, "failed", 0)
            } finally {
                synchronized(parsingBooks) {
                    parsingBooks.remove(bookId)
                }
            }
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
            parseBookContent(bookId, filePath)
            
            // 6. 更新数据库和缓存（这里可能不需要，因为解析方法内部已经更新了）
            val documentCount = documentRepository.countByBookId(bookId)
            cacheService?.setBookParsed(bookId, true)
            
            logger.info("Book $bookId parsed successfully, documents: $documentCount")
            true
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
        synchronized(parsingBooks) {
            if (parsingBooks.contains(bookId)) {
                logger.info("Book $bookId is already being parsed")
                return false
            }
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
    private suspend fun parseBookContent(bookId: Int, filePath: String) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            logger.warn("File does not exist: $filePath")
            return
        }
        
        // 使用工厂创建对应格式的解析器
        val parser = parserFactory.createParser(file)
        if (parser == null) {
            logger.warn("Unsupported format: ${file.extension}")
            bookRepository.updateParseStatus(bookId, "failed", 0)
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                parseWithParser(bookId, file, parser)
            } catch (e: Exception) {
                logger.error("Error parsing book content: ${file.name}", e)
                bookRepository.updateParseStatus(bookId, "failed", 0)
            }
        }
    }
    
    /**
     * 使用解析器解析书籍内容（统一流程）
     */
    private suspend fun parseWithParser(bookId: Int, file: File, parser: BookParser) {
        logger.info("Parsing ${file.extension.uppercase()} content for book ID: $bookId")
        
        // 1. 解析书籍结构
        val structure = parser.parseStructure(file) ?: run {
            logger.error("Failed to parse book structure")
            bookRepository.updateParseStatus(bookId, "failed", 0)
            return
        }
        
        // 2. 清理旧数据并创建文档
        try {
            // 在独立事务中清理旧文档数据
            transaction {
                logger.info("Deleting old documents for book ID: $bookId")
                val count = documentRepository.deleteByBookId(bookId)
                logger.info("Deleted $count old documents for book ID: $bookId")
                documentRepository.deleteResourcesByBookId(bookId)
            }
            
            // 批量保存文档信息（在新事务中）
            val createdCount = transaction {
                logger.info("Creating ${structure.chapters.size} documents for book ID: $bookId")
                var successCount = 0
                structure.chapters.forEach { chapterInfo ->
                    documentRepository.create(
                        bookId = bookId,
                        index = chapterInfo.index,
                        href = chapterInfo.href,
                        inToc = chapterInfo.inToc,
                        title = chapterInfo.title,
                        level = chapterInfo.level
                    )
                    successCount++
                }
                successCount
            }
            
            logger.info("Successfully created $createdCount documents for book ID: $bookId")
            
            // 3. 解析并保存文档内容（在事务外，避免长事务）
            structure.chapters.forEach { chapterInfo ->
                parseAndSaveDocumentContent(bookId, file, parser, chapterInfo)
            }
            
            // 4. 保存资源文件（如果有）
            if (structure.resources.isNotEmpty()) {
                saveResources(bookId, structure.resources)
            }
            
            // 5. 更新书籍解析状态
            bookRepository.updateChaptersParsed(bookId, structure.chapters.size)
            
            logger.info("Parsing completed for book ID: $bookId - ${structure.chapters.size} chapters")
        } catch (e: Exception) {
            logger.error("Failed to parse book content for book ID: $bookId", e)
            bookRepository.updateParseStatus(bookId, "failed", 0)
        }
    }
    
    /**
     * 解析并保存单个文档内容
     */
    private suspend fun parseAndSaveDocumentContent(
        bookId: Int,
        file: File,
        parser: BookParser,
        chapterInfo: BookParser.ChapterInfo
    ) {
        try {
            val document = documentRepository.findByBookIdAndIndex(bookId, chapterInfo.index)
            if (document == null) {
                logger.error("Document not found after creation: bookId=$bookId, index=${chapterInfo.index}")
                return
            }
            
            // 解析文档内容
            val elements = parser.parseChapterContent(file, chapterInfo)
            
            // 统计字数和图片数
            val wordCount = parser.countWords(elements)
            val imageCount = parser.countImages(elements)
            
            // 保存内容和统计信息
            documentRepository.saveDocumentContent(document.id, elements)
            documentRepository.updateStats(document.id, wordCount, imageCount)
            
            logger.debug("Saved document ${document.index}: ${document.title} ($wordCount words, $imageCount images)")
        } catch (e: Exception) {
            logger.error("Failed to parse document ${chapterInfo.index}", e)
        }
    }
    
    /**
     * 保存资源文件（使用新的图片存储服务）
     */
    private fun saveResources(bookId: Int, resources: Map<String, ByteArray>) {
        resources.forEach { (path, bytes) ->
            try {
                // 使用 BookImageStorage 保存图片
                val storedPath = imageStorage.saveImage(bookId, path, bytes)
                val extension = path.substringAfterLast(".")
                val mediaType = imageStorage.detectMediaType(extension)
                
                // 保存到数据库，记录原始路径和存储路径的映射
                documentRepository.saveResource(
                    bookId = bookId,
                    path = path,
                    storedPath = storedPath,  // 存储相对路径，如 "14/abc123.jpg"
                    mediaType = mediaType,
                    size = bytes.size.toLong()
                )
                
                logger.debug("Saved resource: $path -> $storedPath")
            } catch (e: Exception) {
                logger.error("Failed to save resource: $path", e)
            }
        }
    }
    
    /**
     * 获取书籍清单
     */
    fun getBookManifest(bookId: Int): BookManifest? {
        val book = bookRepository.findById(bookId) ?: return null
        val allDocuments = documentRepository.findByBookId(bookId)
        
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
     * 获取文档内容
     * @param baseUrl 可选的基础 URL，用于构建完整的图片 URL
     */
    fun getChapterContent(bookId: Int, index: Int, baseUrl: String? = null): ChapterContent? {
        val document = documentRepository.findByBookIdAndIndex(bookId, index) ?: return null
        val elements = documentRepository.getDocumentContent(document.id) ?: emptyList()
        
        // 转换图片路径为可访问的URL
        val transformedElements = elements.map { element ->
            transformElement(element, bookId, baseUrl)
        }
        
        // 使用所有文档（spine）计算前后导航
        val allDocuments = documentRepository.findByBookId(bookId)
        val currentIdx = allDocuments.indexOfFirst { it.index == index }
        val prevIndex = if (currentIdx > 0) allDocuments[currentIdx - 1].index else null
        val nextIndex = if (currentIdx < allDocuments.size - 1) allDocuments[currentIdx + 1].index else null
        
        return ChapterContent(
            index = index,
            title = document.title,
            elements = transformedElements,
            prevIndex = prevIndex,
            nextIndex = nextIndex
        )
    }
    
    /**
     * 转换 ContentElement 中的图片路径
     */
    private fun transformElement(element: ContentElement, bookId: Int, baseUrl: String?): ContentElement {
        return when (element) {
            is ContentElement.Image -> {
                val resource = documentRepository.findResource(bookId, element.src)
                if (resource != null) {
                    val (storedPath, _) = resource
                    val imagePath = "/book_images/$storedPath"
                    val fullPath = if (baseUrl != null) "$baseUrl$imagePath" else imagePath
                    element.copy(src = fullPath)
                } else {
                    element
                }
            }
            is ContentElement.Paragraph -> {
                element.copy(spans = element.spans.map { transformSpan(it, bookId, baseUrl) })
            }
            is ContentElement.Quote -> {
                element.copy(spans = element.spans.map { transformSpan(it, bookId, baseUrl) })
            }
            is ContentElement.ListBlock -> {
                element.copy(items = element.items.map { item ->
                    ListItem(item.spans.map { transformSpan(it, bookId, baseUrl) })
                })
            }
            is ContentElement.Footnote -> {
                element.copy(spans = element.spans.map { transformSpan(it, bookId, baseUrl) })
            }
            else -> element
        }
    }
    
    /**
     * 转换 TextSpan 中的 footnoteImage 路径为完整 URL
     */
    private fun transformSpan(span: TextSpan, bookId: Int, baseUrl: String?): TextSpan {
        if (span.footnoteImage == null) return span
        
        val resource = documentRepository.findResource(bookId, span.footnoteImage)
        return if (resource != null) {
            val (storedPath, _) = resource
            val imagePath = "/book_images/$storedPath"
            val fullPath = if (baseUrl != null) "$baseUrl$imagePath" else imagePath
            span.copy(footnoteImage = fullPath)
        } else {
            span
        }
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
        scope.cancel()
        contentDispatcher.close()
    }
}
