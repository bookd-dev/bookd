package com.bookd.domain.service

import com.bookd.data.repository.BookChapterRepository
import com.bookd.data.repository.BookRepository
import com.bookd.domain.model.*
import com.bookd.domain.service.parser.EpubParser
import com.bookd.domain.service.parser.TxtParser
import com.bookd.domain.service.parser.PdfParser
import com.bookd.domain.service.parser.MobiParser
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class BookContentService(
    private val bookRepository: BookRepository,
    private val chapterRepository: BookChapterRepository
) {
    private val logger = LoggerFactory.getLogger(BookContentService::class.java)
    
    private val epubParser = EpubParser()
    private val txtParser = TxtParser()
    private val pdfParser = PdfParser()
    private val mobiParser = MobiParser()
    
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
                parseBookContent(bookId, filePath)
                logger.info("Completed content parsing for book ID: $bookId")
            } catch (e: Exception) {
                logger.error("Failed to parse book content for ID: $bookId", e)
            } finally {
                synchronized(parsingBooks) {
                    parsingBooks.remove(bookId)
                }
            }
        }
    }
    
    /**
     * 解析书籍内容（同步）
     */
    private suspend fun parseBookContent(bookId: Int, filePath: String) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            logger.warn("File does not exist: $filePath")
            return
        }
        
        val format = file.extension.lowercase()
        
        withContext(Dispatchers.IO) {
            try {
                when (format) {
                    "epub" -> parseEpubContent(bookId, file)
                    "txt" -> parseTxtContent(bookId, file)
                    "pdf" -> parsePdfContent(bookId, file)
                    "mobi", "azw3" -> parseMobiContent(bookId, file)
                    else -> {
                        logger.warn("Unsupported format: $format")
                    }
                }
            } catch (e: Exception) {
                logger.error("Error parsing book content: ${file.name}", e)
            }
        }
    }
    
    /**
     * 解析 EPUB 内容
     */
    private fun parseEpubContent(bookId: Int, file: File) {
        logger.info("Parsing EPUB content for book ID: $bookId")
        
        val structure = epubParser.parseStructure(file) ?: run {
            logger.error("Failed to parse EPUB structure")
            return
        }
        
        try {
            // 使用单个事务处理删除和插入，避免并发冲突
            transaction {
                // 1. 清理旧章节数据（在同一事务中）
                logger.info("Deleting old chapters for book ID: $bookId")
                chapterRepository.deleteByBookId(bookId)
                chapterRepository.deleteResourcesByBookId(bookId)
                
                // 2. 批量保存章节信息（在同一事务中）
                logger.info("Creating ${structure.chapters.size} chapters for book ID: $bookId")
                structure.chapters.forEach { chapterInfo ->
                    chapterRepository.create(
                        bookId = bookId,
                        index = chapterInfo.index,
                        title = chapterInfo.title,
                        level = chapterInfo.level,
                        href = chapterInfo.href
                    )
                }
            }
            
            logger.info("Successfully created ${structure.chapters.size} chapters for book ID: $bookId")
            
            // 3. 解析章节内容（在事务外，避免长事务）
            structure.chapters.forEach { chapterInfo ->
                try {
                    val chapter = chapterRepository.findByBookIdAndIndex(bookId, chapterInfo.index)
                    if (chapter == null) {
                        logger.error("Chapter not found after creation: bookId=$bookId, index=${chapterInfo.index}")
                        return@forEach
                    }
                    
                    val elements = epubParser.parseChapterContent(file, chapterInfo.href) ?: emptyList()
                    
                    // 统计字数和图片数
                    val wordCount = countWords(elements)
                    val imageCount = countImages(elements)
                    
                    chapterRepository.saveChapterContent(chapter.id, elements)
                    chapterRepository.updateStats(chapter.id, wordCount, imageCount)
                    
                    logger.debug("Saved chapter ${chapter.index}: ${chapter.title} ($wordCount words, $imageCount images)")
                } catch (e: Exception) {
                    logger.error("Failed to parse chapter ${chapterInfo.index}", e)
                }
            }
            
            // 4. 保存资源文件
            val resourcesDir = File("book_resources/${bookId}")
            resourcesDir.mkdirs()
            
            structure.resources.forEach { (path, bytes) ->
                try {
                    val extension = path.substringAfterLast(".")
                    val storedFileName = "${UUID.randomUUID()}.$extension"
                    val storedFile = File(resourcesDir, storedFileName)
                    storedFile.writeBytes(bytes)
                    
                    val mediaType = detectMediaType(extension)
                    chapterRepository.saveResource(
                        bookId = bookId,
                        path = path,
                        storedPath = storedFile.absolutePath,
                        mediaType = mediaType,
                        size = bytes.size.toLong()
                    )
                    
                    logger.debug("Saved resource: $path -> $storedFileName")
                } catch (e: Exception) {
                    logger.error("Failed to save resource: $path", e)
                }
            }
            
            logger.info("EPUB parsing completed for book ID: $bookId - ${structure.chapters.size} chapters, ${structure.resources.size} resources")
        } catch (e: Exception) {
            logger.error("Failed to parse EPUB content for book ID: $bookId", e)
        }
    }
    
    /**
     * 解析 TXT 内容
     */
    private fun parseTxtContent(bookId: Int, file: File) {
        logger.info("Parsing TXT content for book ID: $bookId")
        
        val structure = txtParser.parseStructure(file) ?: run {
            logger.error("Failed to parse TXT structure")
            return
        }
        
        try {
            // 使用单个事务处理删除和插入，避免并发冲突
            transaction {
                // 1. 清理旧章节数据
                logger.info("Deleting old chapters for book ID: $bookId")
                chapterRepository.deleteByBookId(bookId)
                
                // 2. 批量保存章节信息
                logger.info("Creating ${structure.chapters.size} chapters for book ID: $bookId")
                structure.chapters.forEach { chapterInfo ->
                    chapterRepository.create(
                        bookId = bookId,
                        index = chapterInfo.index,
                        title = chapterInfo.title,
                        level = chapterInfo.level
                    )
                }
            }
            
            logger.info("Successfully created ${structure.chapters.size} chapters for book ID: $bookId")
            
            // 3. 提取并保存章节内容（在事务外）
            structure.chapters.forEach { chapterInfo ->
                try {
                    val chapter = chapterRepository.findByBookIdAndIndex(bookId, chapterInfo.index)
                    if (chapter == null) {
                        logger.error("Chapter not found after creation: bookId=$bookId, index=${chapterInfo.index}")
                        return@forEach
                    }
                    
                    val elements = txtParser.extractChapterContent(structure.fullText, chapterInfo)
                    val chapterText = structure.fullText.substring(chapterInfo.startPos, chapterInfo.endPos)
                    val wordCount = txtParser.countWords(chapterText)
                    
                    chapterRepository.saveChapterContent(chapter.id, elements)
                    chapterRepository.updateStats(chapter.id, wordCount, 0)
                    
                    logger.debug("Saved chapter ${chapter.index}: ${chapter.title} ($wordCount words)")
                } catch (e: Exception) {
                    logger.error("Failed to parse chapter ${chapterInfo.index}", e)
                }
            }
            
            logger.info("TXT parsing completed for book ID: $bookId - ${structure.chapters.size} chapters")
        } catch (e: Exception) {
            logger.error("Failed to parse TXT content for book ID: $bookId", e)
        }
    }
    
    /**
     * 解析 PDF 内容
     */
    private fun parsePdfContent(bookId: Int, file: File) {
        logger.info("Parsing PDF content for book ID: $bookId")
        
        val structure = pdfParser.parseStructure(file, extractImages = false) ?: run {
            logger.error("Failed to parse PDF structure")
            return
        }
        
        // 1. 清理旧章节数据和资源
        chapterRepository.deleteByBookId(bookId)
        chapterRepository.deleteResourcesByBookId(bookId)
        
        // 2. 提取并保存图片资源
        try {
            val images = pdfParser.extractImages(file)
            logger.info("Extracted ${images.size} images from PDF")
            
            val resourcesDir = File("book_resources/${bookId}")
            resourcesDir.mkdirs()
            
            images.forEach { (originalName, bytes) ->
                try {
                    val extension = originalName.substringAfterLast(".")
                    val storedFileName = "${UUID.randomUUID()}.$extension"
                    val storedFile = File(resourcesDir, storedFileName)
                    storedFile.writeBytes(bytes)
                    
                    val mediaType = detectMediaType(extension)
                    chapterRepository.saveResource(
                        bookId = bookId,
                        path = originalName,
                        storedPath = storedFile.absolutePath,
                        mediaType = mediaType,
                        size = bytes.size.toLong()
                    )
                    
                    logger.debug("Saved PDF image: $originalName -> $storedFileName")
                } catch (e: Exception) {
                    logger.error("Failed to save PDF image: $originalName", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to extract images from PDF", e)
        }
        
        // 3. 保存章节信息
        structure.chapters.forEach { chapterInfo ->
            val chapter = chapterRepository.create(
                bookId = bookId,
                index = chapterInfo.index,
                title = chapterInfo.title,
                level = chapterInfo.level
            )
            
            // 4. 提取并保存章节内容
            try {
                val elements = pdfParser.extractChapterContent(file, chapterInfo) ?: emptyList()
                
                // 计算字数（从元素中提取文本）
                val wordCount = countWords(elements)
                val imageCount = countImages(elements)
                
                chapterRepository.saveChapterContent(chapter.id, elements)
                chapterRepository.updateStats(chapter.id, wordCount, imageCount)
                
                logger.debug("Saved PDF chapter ${chapter.index}: ${chapter.title} ($wordCount words)")
            } catch (e: Exception) {
                logger.error("Failed to parse PDF chapter ${chapterInfo.index}", e)
            }
        }
        
        logger.info("PDF parsing completed for book ID: $bookId - ${structure.chapters.size} chapters")
    }
    
    /**
     * 解析 MOBI/AZW3 内容
     */
    private fun parseMobiContent(bookId: Int, file: File) {
        logger.info("Parsing MOBI/AZW3 content for book ID: $bookId")
        
        val structure = mobiParser.parseStructure(file) ?: run {
            logger.error("Failed to parse MOBI/AZW3 structure")
            return
        }
        
        // 1. 清理旧章节数据
        chapterRepository.deleteByBookId(bookId)
        
        // 2. 保存章节信息
        structure.chapters.forEach { chapterInfo ->
            val chapter = chapterRepository.create(
                bookId = bookId,
                index = chapterInfo.index,
                title = chapterInfo.title,
                level = chapterInfo.level
            )
            
            // 3. 提取并保存章节内容
            try {
                val elements = mobiParser.extractChapterContent(structure.htmlContent, chapterInfo) ?: emptyList()
                
                // 计算字数
                val wordCount = countWords(elements)
                val imageCount = countImages(elements)
                
                chapterRepository.saveChapterContent(chapter.id, elements)
                chapterRepository.updateStats(chapter.id, wordCount, imageCount)
                
                logger.debug("Saved MOBI chapter ${chapter.index}: ${chapter.title} ($wordCount words)")
            } catch (e: Exception) {
                logger.error("Failed to parse MOBI chapter ${chapterInfo.index}", e)
            }
        }
        
        logger.info("MOBI/AZW3 parsing completed for book ID: $bookId - ${structure.chapters.size} chapters")
    }
    
    /**
     * 获取书籍清单
     */
    fun getBookManifest(bookId: Int): BookManifest? {
        val book = bookRepository.findById(bookId) ?: return null
        val chapters = chapterRepository.findByBookId(bookId)
        
        if (chapters.isEmpty()) {
            logger.warn("No chapters found for book ID: $bookId")
            return null
        }
        
        // 构建目录树
        val toc = buildTocTree(chapters)
        
        return BookManifest(
            id = book.id,
            title = book.title,
            author = book.author,
            format = book.format,
            totalChapters = chapters.size,
            toc = toc,
            spine = chapters.map { it.index },
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
     * 获取章节内容
     */
    fun getChapterContent(bookId: Int, index: Int): ChapterContent? {
        val chapter = chapterRepository.findByBookIdAndIndex(bookId, index) ?: return null
        val elements = chapterRepository.getChapterContent(chapter.id) ?: emptyList()
        
        val allChapters = chapterRepository.findByBookId(bookId)
        val prevIndex = allChapters.find { it.index == index - 1 }?.index
        val nextIndex = allChapters.find { it.index == index + 1 }?.index
        
        return ChapterContent(
            index = index,
            title = chapter.title,
            elements = elements,
            prevIndex = prevIndex,
            nextIndex = nextIndex
        )
    }
    
    /**
     * 获取资源文件
     */
    fun getResource(bookId: Int, path: String): Pair<File, String>? {
        val (storedPath, mediaType) = chapterRepository.findResource(bookId, path) ?: return null
        val file = File(storedPath)
        
        if (!file.exists()) {
            logger.warn("Resource file not found: $storedPath")
            return null
        }
        
        return file to mediaType
    }
    
    /**
     * 构建目录树
     */
    private fun buildTocTree(chapters: List<BookChapter>): List<TocItem> {
        val rootItems = mutableListOf<TocItem>()
        val stack = mutableListOf<Pair<Int, MutableList<TocItem>>>() // (level, children list)
        
        chapters.forEach { chapter ->
            val tocItem = TocItem(
                index = chapter.index,
                title = chapter.title ?: "第${chapter.index + 1}章",
                level = chapter.level,
                wordCount = chapter.wordCount,
                imageCount = chapter.imageCount,
                children = mutableListOf()
            )
            
            when {
                chapter.level == 0 -> {
                    rootItems.add(tocItem)
                    stack.clear()
                    stack.add(chapter.level to (tocItem.children as MutableList))
                }
                chapter.level > 0 && stack.isNotEmpty() -> {
                    // 找到合适的父级
                    while (stack.isNotEmpty() && stack.last().first >= chapter.level) {
                        stack.removeLast()
                    }
                    
                    if (stack.isNotEmpty()) {
                        stack.last().second.add(tocItem)
                        stack.add(chapter.level to (tocItem.children as MutableList))
                    } else {
                        rootItems.add(tocItem)
                        stack.add(chapter.level to (tocItem.children as MutableList))
                    }
                }
            }
        }
        
        return rootItems
    }
    
    /**
     * 统计内容元素中的字数
     */
    private fun countWords(elements: List<ContentElement>): Int {
        var count = 0
        
        elements.forEach { element ->
            when (element) {
                is ContentElement.Paragraph -> {
                    element.spans.forEach { span ->
                        count += span.text.length
                    }
                }
                is ContentElement.Heading -> {
                    count += element.text.length
                }
                is ContentElement.Quote -> {
                    element.spans.forEach { span ->
                        count += span.text.length
                    }
                }
                is ContentElement.Code -> {
                    count += element.text.length
                }
                is ContentElement.ListBlock -> {
                    element.items.forEach { item ->
                        item.spans.forEach { span ->
                            count += span.text.length
                        }
                    }
                }
                else -> {}
            }
        }
        
        return count
    }
    
    /**
     * 统计内容元素中的图片数量
     */
    private fun countImages(elements: List<ContentElement>): Int {
        return elements.count { it is ContentElement.Image }
    }
    
    /**
     * 检测媒体类型
     */
    private fun detectMediaType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * 关闭服务
     */
    fun shutdown() {
        scope.cancel()
        contentDispatcher.close()
    }
}
