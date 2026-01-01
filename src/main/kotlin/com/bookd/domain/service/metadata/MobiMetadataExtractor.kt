package com.bookd.domain.service.metadata

import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MOBI 元数据提取器
 * 直接解析 MOBI 文件的 EXTH Header
 */
class MobiMetadataExtractor : MetadataExtractor {
    
    private val logger = LoggerFactory.getLogger(MobiMetadataExtractor::class.java)
    
    override fun extractMetadata(file: File): BookMetadata? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                // 验证 MOBI 文件头
                val magic = ByteArray(8)
                raf.read(magic)
                
                // 查找 EXTH header
                val exthData = findExthHeader(raf)
                if (exthData != null) {
                    parseExthHeader(exthData)
                } else {
                    logger.debug("No EXTH header found in MOBI: ${file.name}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to extract MOBI metadata from ${file.name}: ${e.message}")
            null
        }
    }
    
    override fun extractCover(file: File, bookId: Int): String? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                // 尝试从 EXTH header 中查找封面索引
                val coverOffset = findCoverImageOffset(raf)
                
                if (coverOffset != null) {
                    // 读取封面图片数据
                    raf.seek(coverOffset)
                    
                    // 读取图片前几个字节判断格式
                    val header = ByteArray(8)
                    raf.read(header)
                    
                    val extension = when {
                        header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> "jpg"
                        header[0] == 0x89.toByte() && header[1] == 0x50.toByte() -> "png"
                        header[0] == 0x47.toByte() && header[1] == 0x49.toByte() -> "gif"
                        else -> {
                            logger.debug("Unknown image format in MOBI")
                            return null
                        }
                    }
                    
                    // 估算图片大小 (读取到文件末尾或下一个记录)
                    val fileSize = raf.length()
                    val remainingSize = (fileSize - coverOffset).toInt()
                    val imageSize = minOf(remainingSize, 5 * 1024 * 1024) // 最大 5MB
                    
                    // 读取图片数据
                    raf.seek(coverOffset)
                    val imageData = ByteArray(imageSize)
                    val bytesRead = raf.read(imageData)
                    
                    // 保存封面
                    val coversDir = File("covers")
                    if (!coversDir.exists()) {
                        coversDir.mkdirs()
                    }
                    
                    val outputFile = File(coversDir, "book_${bookId}.$extension")
                    outputFile.writeBytes(imageData.copyOf(bytesRead))
                    
                    logger.info("Extracted MOBI cover for book $bookId")
                    return "/covers/book_${bookId}.$extension"
                } else {
                    logger.debug("No cover image found in MOBI: ${file.name}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to extract MOBI cover from ${file.name}: ${e.message}")
            null
        }
    }
    
    /**
     * 查找封面图片的文件偏移量
     */
    private fun findCoverImageOffset(raf: RandomAccessFile): Long? {
        try {
            // MOBI 文件结构: PalmDB Header (78 bytes) + Record Info (8 bytes per record)
            raf.seek(76)
            val recordCountBytes = ByteArray(2)
            raf.read(recordCountBytes)
            val recordCount = ByteBuffer.wrap(recordCountBytes).order(ByteOrder.BIG_ENDIAN).short.toInt()
            
            if (recordCount !in 1..10000) {
                return null
            }
            
            // 读取第一个图片记录 (通常在 record 1 之后)
            // 简化实现: 查找 JPEG/PNG 文件头
            for (i in 0 until minOf(recordCount, 100)) {
                val recordOffset = 78 + i * 8
                raf.seek(recordOffset.toLong())
                
                val offsetBytes = ByteArray(4)
                raf.read(offsetBytes)
                val offset = ByteBuffer.wrap(offsetBytes).order(ByteOrder.BIG_ENDIAN).int.toLong()
                
                if (offset > 0 && offset < raf.length()) {
                    raf.seek(offset)
                    val header = ByteArray(4)
                    raf.read(header)
                    
                    // 检查是否是图片文件头
                    if ((header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) ||  // JPEG
                        (header[0] == 0x89.toByte() && header[1] == 0x50.toByte())) {  // PNG
                        return offset
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to find cover offset: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 查找 EXTH header 位置
     */
    private fun findExthHeader(raf: RandomAccessFile): ByteArray? {
        try {
            // 1. 读取第一个 record 的偏移量 (MOBI header 位置)
            raf.seek(78) // PalmDB header 后的第一个 record offset
            val buf = ByteArray(4)
            raf.read(buf)
            val mobiHeaderOffset = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int.toLong()
            
            if (mobiHeaderOffset <= 0 || mobiHeaderOffset > raf.length()) {
                logger.debug("Invalid MOBI header offset: $mobiHeaderOffset")
                return null
            }
            
            // 2. 读取 MOBI header 确认 EXTH 标志
            raf.seek(mobiHeaderOffset + 128) // MOBI header offset + 128 = EXTH flag position
            raf.read(buf)
            val exthFlag = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
            
            if (exthFlag and 0x40 == 0) {
                logger.debug("EXTH flag not set in MOBI header")
                return null
            }
            
            // 3. 计算 EXTH 位置: MOBI header offset + MOBI header length
            raf.seek(mobiHeaderOffset + 20) // MOBI header length position
            raf.read(buf)
            val mobiHeaderLength = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
            
            val exthOffset = mobiHeaderOffset + mobiHeaderLength + 16 // +16 for full MOBI header
            
            if (exthOffset < 0 || exthOffset > raf.length() - 12) {
                logger.debug("Invalid EXTH offset calculated: $exthOffset")
                return null
            }
            
            // 4. 验证 EXTH 签名
            raf.seek(exthOffset)
            val exthMagic = ByteArray(4)
            raf.read(exthMagic)
            
            if (String(exthMagic, Charsets.US_ASCII) != "EXTH") {
                logger.debug("EXTH signature not found at calculated offset $exthOffset")
                // 备选方案: 在文件前 100KB 搜索 EXTH
                return searchForExth(raf)
            }
            
            // 5. 读取 EXTH header 长度
            val exthLengthBytes = ByteArray(4)
            raf.read(exthLengthBytes)
            val exthLength = ByteBuffer.wrap(exthLengthBytes).order(ByteOrder.BIG_ENDIAN).int
            
            if (exthLength < 12 || exthLength > 100000) {
                logger.debug("Invalid EXTH length: $exthLength")
                return null
            }
            
            // 6. 读取完整的 EXTH data
            val exthData = ByteArray(exthLength)
            raf.seek(exthOffset)
            raf.read(exthData)
            
            logger.debug("Found EXTH at offset 0x${exthOffset.toString(16)}, length: $exthLength")
            return exthData
            
        } catch (e: Exception) {
            logger.debug("Failed to find EXTH header: ${e.message}")
            // 尝试备选搜索方案
            return try {
                searchForExth(raf)
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    /**
     * 备选方案: 在文件中搜索 EXTH 签名
     */
    private fun searchForExth(raf: RandomAccessFile): ByteArray? {
        try {
            raf.seek(0)
            val searchSize = minOf(200000, raf.length()).toInt() // 搜索前 200KB
            val buffer = ByteArray(searchSize)
            raf.read(buffer)
            
            // 搜索 "EXTH" 签名
            for (i in 0 until buffer.size - 12) {
                if (buffer[i] == 'E'.code.toByte() &&
                    buffer[i + 1] == 'X'.code.toByte() &&
                    buffer[i + 2] == 'T'.code.toByte() &&
                    buffer[i + 3] == 'H'.code.toByte()) {
                    
                    // 读取 EXTH 长度
                    val exthLength = ByteBuffer.wrap(buffer, i + 4, 4).order(ByteOrder.BIG_ENDIAN).int
                    
                    if (exthLength in 12..100000 && i + exthLength <= buffer.size) {
                        logger.debug("Found EXTH by search at offset 0x${i.toString(16)}")
                        return buffer.copyOfRange(i, i + exthLength)
                    }
                }
            }
            
            logger.debug("EXTH not found in first ${searchSize} bytes")
        } catch (e: Exception) {
            logger.debug("Search for EXTH failed: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 解析 EXTH header 提取元数据
     */
    private fun parseExthHeader(exthData: ByteArray): BookMetadata? {
        try {
            var title: String? = null
            var author: String? = null
            var publisher: String? = null
            var description: String? = null
            
            // EXTH 记录从 offset 12 开始
            var offset = 12
            val buffer = ByteBuffer.wrap(exthData).order(ByteOrder.BIG_ENDIAN)
            buffer.position(8)
            val recordCount = buffer.int
            
            for (i in 0 until recordCount) {
                if (offset + 8 > exthData.size) break
                
                buffer.position(offset)
                val recordType = buffer.int
                val recordLength = buffer.int
                
                if (recordLength < 8 || offset + recordLength > exthData.size) break
                
                val dataLength = recordLength - 8
                val data = ByteArray(dataLength)
                buffer.get(data)
                
                val value = String(data, Charsets.UTF_8).trim()
                
                when (recordType) {
                    100 -> author = value  // Author
                    105 -> description = value  // Description
                    106 -> publisher = value  // Publisher
                    503 -> title = value  // Title
                }
                
                offset += recordLength
            }
            
            return if (title != null || author != null) {
                BookMetadata(title, author, publisher, description)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse EXTH header: ${e.message}")
            return null
        }
    }
}
