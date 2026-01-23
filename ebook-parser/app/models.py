"""Pydantic models for API request/response."""

from pydantic import BaseModel, Field
from typing import Optional, List


class ParseRequest(BaseModel):
    """Request model for parsing operations."""
    file_path: str = Field(..., description="Absolute path to the EPUB file")
    book_id: int = Field(..., description="Book ID for cover naming")


class BookMetadataResponse(BaseModel):
    """Response model matching Kotlin BookMetadata data class.
    
    This matches the structure in:
    bookd/src/main/kotlin/com/bookd/domain/service/metadata/MetadataExtractor.kt
    """
    title: Optional[str] = Field(None, description="Book title")
    author: Optional[str] = Field(None, description="Book author")
    publisher: Optional[str] = Field(None, description="Publisher name")
    description: Optional[str] = Field(None, description="Book description")
    isbn: Optional[str] = Field(None, description="ISBN number")
    tags: List[str] = Field(default_factory=list, description="Subject tags from dc:subject")


class CoverExtractionResponse(BaseModel):
    """Response model for cover extraction."""
    cover_path: Optional[str] = Field(None, description="Relative path to saved cover (e.g., /covers/book_123.jpg)")
    width: Optional[int] = Field(None, description="Cover image width in pixels")
    height: Optional[int] = Field(None, description="Cover image height in pixels")
    aspect_ratio: Optional[float] = Field(None, description="Width / Height ratio")
    success: bool = Field(default=False, description="Whether extraction succeeded")
    error: Optional[str] = Field(None, description="Error message if failed")


class HealthResponse(BaseModel):
    """Health check response."""
    status: str = Field(..., description="Service status")
    version: str = Field(..., description="Service version")


# ==================== Chapter Structure Models ====================

class ChapterInfo(BaseModel):
    """Chapter information model."""
    index: int = Field(..., description="Chapter index in spine")
    href: str = Field(..., description="Chapter file path (e.g., chapter1.xhtml)")
    in_toc: bool = Field(..., description="Whether chapter appears in TOC")
    title: Optional[str] = Field(None, description="Chapter title from TOC")
    level: int = Field(default=0, description="Chapter level in TOC hierarchy (0=not in TOC)")


class BookStructureResponse(BaseModel):
    """Response model for book structure parsing."""
    chapters: List[ChapterInfo] = Field(default_factory=list, description="List of chapters")
    total_chapters: int = Field(..., description="Total number of chapters")
    success: bool = Field(default=True, description="Whether parsing succeeded")
    error: Optional[str] = Field(None, description="Error message if failed")


# ==================== Chapter Content Models ====================

class TextSpan(BaseModel):
    """Text span with styling information."""
    text: str = Field(..., description="Text content")
    styles: List[str] = Field(default_factory=list, description="Text styles: BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, CODE")
    link: Optional[str] = Field(None, description="Link URL if this is a hyperlink")
    footnote_id: Optional[str] = Field(None, description="Footnote ID if this is a footnote reference")
    footnote_image: Optional[str] = Field(None, description="Original footnote image path")


class ListItemModel(BaseModel):
    """List item model."""
    spans: List[TextSpan] = Field(default_factory=list, description="Text spans in list item")


class ContentElement(BaseModel):
    """Base content element model.
    
    Different element types have different fields:
    - paragraph: spans
    - heading: level, text
    - image: src, alt, width, height, aspect_ratio
    - quote: spans
    - code: text, language
    - listBlock: ordered, items
    - divider: (no additional fields)
    - footnote: id, spans
    """
    
    type: str = Field(..., description="Element type: paragraph, heading, image, quote, code, listBlock, divider, footnote")
    
    # Heading fields
    level: Optional[int] = Field(None, description="Heading level (1-6)")
    text: Optional[str] = Field(None, description="Text content for heading/code")
    
    # Paragraph, Quote, Footnote fields
    spans: Optional[List[TextSpan]] = Field(None, description="Text spans with styling")
    
    # Image fields
    src: Optional[str] = Field(None, description="Image source path")
    alt: Optional[str] = Field(None, description="Image alt text")
    width: Optional[int] = Field(None, description="Image width in pixels")
    height: Optional[int] = Field(None, description="Image height in pixels")
    aspect_ratio: Optional[float] = Field(None, description="Image aspect ratio (width/height)")
    
    # Code fields
    language: Optional[str] = Field(None, description="Programming language for code block")
    
    # ListBlock fields
    ordered: Optional[bool] = Field(None, description="Whether list is ordered (ol) or unordered (ul)")
    items: Optional[List[ListItemModel]] = Field(None, description="List items")
    
    # Footnote fields
    id: Optional[str] = Field(None, description="Footnote ID")


class ParseContentRequest(BaseModel):
    """Request model for parsing chapter content."""
    file_path: str = Field(..., description="Absolute path to the EPUB file")
    book_id: int = Field(..., description="Book ID")
    chapter_href: str = Field(..., description="Chapter file path (e.g., chapter1.xhtml)")


class ChapterContentResponse(BaseModel):
    """Response model for chapter content parsing."""
    elements: List[ContentElement] = Field(default_factory=list, description="Parsed content elements")
    word_count: int = Field(default=0, description="Word count in chapter")
    image_count: int = Field(default=0, description="Number of images in chapter")
    success: bool = Field(default=True, description="Whether parsing succeeded")
    error: Optional[str] = Field(None, description="Error message if failed")
