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
