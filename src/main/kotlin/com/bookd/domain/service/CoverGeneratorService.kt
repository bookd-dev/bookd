package com.bookd.domain.service

import com.bookd.infrastructure.storage.BookImageStorage
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class CoverGeneratorService(
    private val imageStorage: BookImageStorage
) {
    private val logger = LoggerFactory.getLogger(CoverGeneratorService::class.java)
    
    // Cover dimensions
    private val coverWidth = 400
    private val coverHeight = 600
    
    // Color palette for backgrounds
    private val colorPalette = listOf(
        Color(102, 126, 234), // Purple-Blue
        Color(118, 75, 162),  // Purple
        Color(17, 153, 142),  // Teal
        Color(56, 239, 125),  // Green
        Color(240, 147, 251), // Pink
        Color(245, 87, 108),  // Red
        Color(255, 165, 0),   // Orange
        Color(52, 152, 219),  // Blue
        Color(155, 89, 182),  // Dark Purple
        Color(46, 204, 113)   // Emerald
    )
    
    // Get a font that supports Chinese characters
    private fun getChineseFont(style: Int, size: Int): Font {
        // Try to find system fonts that support Chinese
        val preferredFonts = listOf(
            "WenQuanYi Zen Hei",  // Installed in our Docker image
            "Noto Sans CJK SC",
            "Source Han Sans CN",
            "Microsoft YaHei",
            "SimHei",
            "STHeiti",
            "PingFang SC",
            "Hiragino Sans GB",
            "WenQuanYi Micro Hei",
            "Droid Sans Fallback",
            "Arial Unicode MS"
        )
        
        val availableFonts = java.awt.GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .availableFontFamilyNames
        
        logger.info("Available fonts in system: ${availableFonts.take(10).joinToString(", ")}")
        
        // Find first available font that supports Chinese
        for (fontName in preferredFonts) {
            if (availableFonts.contains(fontName)) {
                logger.info("Using font: $fontName for Chinese text")
                return Font(fontName, style, size)
            }
        }
        
        // Fallback: use logical fonts which should support Unicode
        logger.warn("No preferred Chinese font found, using fallback: Dialog")
        return Font(Font.DIALOG, style, size)
    }
    
    /**
     * Generate a text-based cover image for a book
     */
    fun generateCover(bookId: Int, title: String, author: String?): String? {
        try {
            // Create a new buffered image
            val image = BufferedImage(coverWidth, coverHeight, BufferedImage.TYPE_INT_RGB)
            val g2d = image.createGraphics()
            
            // Enable anti-aliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            
            // Select a color based on title hash (consistent color for same title)
            val backgroundColor = colorPalette[Math.abs(title.hashCode()) % colorPalette.size]
            
            // Create gradient background
            val gradient = java.awt.GradientPaint(
                0f, 0f, backgroundColor,
                coverWidth.toFloat(), coverHeight.toFloat(), backgroundColor.darker()
            )
            g2d.paint = gradient
            g2d.fillRect(0, 0, coverWidth, coverHeight)
            
            // Draw title
            g2d.color = Color.WHITE
            val titleFont = getChineseFont(Font.BOLD, 32)
            g2d.font = titleFont
            
            // Wrap and draw title text
            val titleLines = wrapText(title, g2d, titleFont, coverWidth - 60)
            var y = coverHeight / 2 - (titleLines.size * 40) / 2
            
            for (line in titleLines) {
                val lineWidth = g2d.fontMetrics.stringWidth(line)
                val x = (coverWidth - lineWidth) / 2
                g2d.drawString(line, x, y)
                y += 45
            }
            
            // Draw author name if available
            if (!author.isNullOrBlank()) {
                val authorFont = getChineseFont(Font.PLAIN, 20)
                g2d.font = authorFont
                val authorText = "作者: $author"
                val authorLines = wrapText(authorText, g2d, authorFont, coverWidth - 60)
                y += 30
                
                for (line in authorLines) {
                    val lineWidth = g2d.fontMetrics.stringWidth(line)
                    val x = (coverWidth - lineWidth) / 2
                    g2d.drawString(line, x, y)
                    y += 30
                }
            }
            
            g2d.dispose()
            
            // Convert image to byte array
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            val imageData = outputStream.toByteArray()
            
            // Save using BookImageStorage
            val coverPath = imageStorage.saveCover(bookId, "cover.png", imageData, isGenerated = true)
            
            logger.info("Generated cover for book ID $bookId: $coverPath")
            return coverPath
            
        } catch (e: Exception) {
            logger.error("Failed to generate cover for book ID $bookId: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Wrap text to fit within a given width
     */
    private fun wrapText(text: String, g2d: java.awt.Graphics2D, font: Font, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""
        
        g2d.font = font
        val metrics = g2d.fontMetrics
        
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val testWidth = metrics.stringWidth(testLine)
            
            if (testWidth > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine)
                currentLine = word
            } else {
                currentLine = testLine
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        
        // Limit to 3 lines for title
        return lines.take(3)
    }
}
