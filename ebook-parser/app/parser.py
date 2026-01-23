"""EPUB parser using ebooklib - core parsing logic."""

import logging
from pathlib import Path
from typing import Optional, List, Tuple
import io
import hashlib

from ebooklib import epub
from PIL import Image

from app.models import (
    BookMetadataResponse, 
    CoverExtractionResponse,
    BookStructureResponse,
    ChapterContentResponse,
    ContentElement
)
from app.config import config
from app.structure_parser import StructureParser
from app.content_parser import ContentParser

logger = logging.getLogger(__name__)


class EpubParser:
    """EPUB file parser using ebooklib library."""
    
    def __init__(self):
        self.structure_parser = StructureParser()
        self.content_parser = ContentParser()
    
    def extract_metadata(self, file_path: str) -> BookMetadataResponse:
        """Extract metadata from EPUB file.
        
        Args:
            file_path: Absolute path to EPUB file
            
        Returns:
            BookMetadataResponse with extracted metadata
            
        Raises:
            FileNotFoundError: If file doesn't exist
            Exception: If parsing fails
        """
        file_path_obj = Path(file_path)
        if not file_path_obj.exists():
            logger.error(f"File not found: {file_path}")
            raise FileNotFoundError(f"File not found: {file_path}")
        
        try:
            logger.info(f"Parsing EPUB metadata from: {file_path}")
            book = epub.read_epub(str(file_path_obj))
            
            # Extract DC metadata
            title = self._get_metadata_value(book, 'DC', 'title')
            author = self._get_metadata_value(book, 'DC', 'creator')
            publisher = self._get_metadata_value(book, 'DC', 'publisher')
            description = self._get_metadata_value(book, 'DC', 'description')
            isbn = self._get_metadata_value(book, 'DC', 'identifier')
            
            # Extract tags from dc:subject
            tags = self._extract_tags(book)
            
            # Limit description length
            if description and len(description) > config.MAX_DESCRIPTION_LENGTH:
                description = description[:config.MAX_DESCRIPTION_LENGTH]
            
            logger.info(f"Successfully extracted metadata: title={title}, author={author}, tags={len(tags)}")
            
            return BookMetadataResponse(
                title=title,
                author=author,
                publisher=publisher,
                description=description,
                isbn=isbn,
                tags=tags
            )
            
        except Exception as e:
            logger.error(f"Failed to extract metadata from {file_path}: {e}")
            raise
    
    def extract_cover(self, file_path: str, book_id: int) -> CoverExtractionResponse:
        """Extract cover image from EPUB and save to shared volume.
        
        Args:
            file_path: Absolute path to EPUB file
            book_id: Book ID for naming the cover file
            
        Returns:
            CoverExtractionResponse with cover path and dimensions
        """
        file_path_obj = Path(file_path)
        if not file_path_obj.exists():
            logger.error(f"File not found: {file_path}")
            return CoverExtractionResponse(
                success=False,
                error=f"File not found: {file_path}"
            )
        
        try:
            logger.info(f"Extracting cover from: {file_path}")
            book = epub.read_epub(str(file_path_obj))
            
            # Try to get cover image using ebooklib
            cover_data, cover_type = self._get_cover_data(book)
            
            if not cover_data:
                logger.warning(f"No cover found in EPUB: {file_path}")
                return CoverExtractionResponse(
                    success=False,
                    error="No cover image found in EPUB"
                )
            
            # Determine file extension from cover type
            extension = self._get_extension_from_type(cover_type)
            
            # Save cover to shared volume
            cover_filename = f"book_{book_id}{extension}"
            cover_path = config.get_covers_dir() / cover_filename
            
            with open(cover_path, 'wb') as f:
                f.write(cover_data)
            
            # Get image dimensions using Pillow
            width, height, aspect_ratio = self._get_image_dimensions(cover_data)
            
            relative_path = f"/covers/{cover_filename}"
            logger.info(f"Successfully extracted cover to: {relative_path} ({width}x{height})")
            
            return CoverExtractionResponse(
                cover_path=relative_path,
                width=width,
                height=height,
                aspect_ratio=aspect_ratio,
                success=True
            )
            
        except Exception as e:
            logger.error(f"Failed to extract cover from {file_path}: {e}")
            return CoverExtractionResponse(
                success=False,
                error=str(e)
            )
    
    def _get_metadata_value(self, book: epub.EpubBook, namespace: str, key: str) -> Optional[str]:
        """Get metadata value from EPUB book.
        
        Args:
            book: EpubBook instance
            namespace: Metadata namespace (e.g., 'DC')
            key: Metadata key (e.g., 'title', 'creator')
            
        Returns:
            Metadata value or None
        """
        try:
            values = book.get_metadata(namespace, key)
            if values and len(values) > 0:
                # values is a list of tuples like [('Title', {})]
                value = values[0][0] if isinstance(values[0], tuple) else values[0]
                return str(value).strip() if value else None
            return None
        except Exception as e:
            logger.debug(f"Failed to get metadata {namespace}:{key}: {e}")
            return None
    
    def _extract_tags(self, book: epub.EpubBook) -> List[str]:
        """Extract subject tags from EPUB metadata.
        
        Args:
            book: EpubBook instance
            
        Returns:
            List of tag strings
        """
        tags = []
        try:
            subjects = book.get_metadata('DC', 'subject')
            if subjects:
                for subject in subjects:
                    # subject can be tuple or string
                    tag = subject[0] if isinstance(subject, tuple) else subject
                    tag_str = str(tag).strip()
                    
                    # Validate tag
                    if tag_str and len(tag_str) <= config.MAX_TAG_LENGTH:
                        tags.append(tag_str)
                    
                    # Limit total number of tags
                    if len(tags) >= config.MAX_TAGS_COUNT:
                        break
            
            logger.debug(f"Extracted {len(tags)} tags: {tags}")
            return tags
            
        except Exception as e:
            logger.debug(f"Failed to extract tags: {e}")
            return []
    
    def _get_cover_data(self, book: epub.EpubBook) -> Tuple[Optional[bytes], Optional[str]]:
        """Get cover image data from EPUB.
        
        Args:
            book: EpubBook instance
            
        Returns:
            Tuple of (cover_data, media_type) or (None, None)
        """
        try:
            # Method 1: Use ebooklib's get_item_with_id for 'cover' or 'cover-image'
            for cover_id in ['cover', 'cover-image', 'cover_image', 'coverimage']:
                try:
                    cover_item = book.get_item_with_id(cover_id)
                    if cover_item and cover_item.get_content():
                        return cover_item.get_content(), cover_item.get_type()
                except:
                    continue
            
            # Method 2: Search for cover in metadata
            covers = book.get_metadata('OPF', 'cover')
            if covers:
                cover_id = covers[0][0] if isinstance(covers[0], tuple) else covers[0]
                try:
                    cover_item = book.get_item_with_id(cover_id)
                    if cover_item and cover_item.get_content():
                        return cover_item.get_content(), cover_item.get_type()
                except:
                    pass
            
            # Method 3: Find images with 'cover' in the name
            for item in book.get_items():
                if item.get_type() and item.get_type().startswith('image/'):
                    item_name = item.get_name().lower()
                    if 'cover' in item_name:
                        return item.get_content(), item.get_type()
            
            # Method 4: Get first image as fallback
            for item in book.get_items():
                if item.get_type() and item.get_type().startswith('image/'):
                    logger.debug(f"Using first image as cover: {item.get_name()}")
                    return item.get_content(), item.get_type()
            
            return None, None
            
        except Exception as e:
            logger.error(f"Error getting cover data: {e}")
            return None, None
    
    def _get_extension_from_type(self, media_type: Optional[str]) -> str:
        """Get file extension from MIME type.
        
        Args:
            media_type: MIME type (e.g., 'image/jpeg')
            
        Returns:
            File extension with dot (e.g., '.jpg')
        """
        if not media_type:
            return '.jpg'
        
        type_map = {
            'image/jpeg': '.jpg',
            'image/jpg': '.jpg',
            'image/png': '.png',
            'image/gif': '.gif',
            'image/webp': '.webp'
        }
        
        return type_map.get(media_type.lower(), '.jpg')
    
    def _get_image_dimensions(self, image_data: bytes) -> Tuple[Optional[int], Optional[int], Optional[float]]:
        """Get image dimensions using Pillow.
        
        Args:
            image_data: Image binary data
            
        Returns:
            Tuple of (width, height, aspect_ratio)
        """
        try:
            image = Image.open(io.BytesIO(image_data))
            width, height = image.size
            aspect_ratio = width / height if height > 0 else None
            return width, height, aspect_ratio
        except Exception as e:
            logger.warning(f"Failed to get image dimensions: {e}")
            return None, None, None
    
    def parse_structure(self, file_path: str, book_id: int) -> BookStructureResponse:
        """Parse EPUB structure (chapters and TOC).
        
        Args:
            file_path: Absolute path to EPUB file
            book_id: Book ID
            
        Returns:
            BookStructureResponse with chapter list
        """
        try:
            logger.info(f"Parsing structure from: {file_path}")
            chapters, total = self.structure_parser.parse_structure(file_path)
            
            logger.info(f"Successfully parsed structure: {total} chapters")
            return BookStructureResponse(
                chapters=chapters,
                total_chapters=total,
                success=True
            )
            
        except FileNotFoundError as e:
            logger.error(f"File not found: {file_path}")
            return BookStructureResponse(
                chapters=[],
                total_chapters=0,
                success=False,
                error=str(e)
            )
        except Exception as e:
            logger.error(f"Failed to parse structure: {e}", exc_info=True)
            return BookStructureResponse(
                chapters=[],
                total_chapters=0,
                success=False,
                error=str(e)
            )
    
    def parse_chapter_content(self, file_path: str, book_id: int, chapter_href: str) -> ChapterContentResponse:
        """Parse chapter content to structured elements.
        
        Args:
            file_path: Absolute path to EPUB file
            book_id: Book ID
            chapter_href: Chapter file path (e.g., 'chapter1.xhtml')
            
        Returns:
            ChapterContentResponse with content elements
        """
        file_path_obj = Path(file_path)
        if not file_path_obj.exists():
            logger.error(f"File not found: {file_path}")
            return ChapterContentResponse(
                elements=[],
                word_count=0,
                image_count=0,
                success=False,
                error=f"File not found: {file_path}"
            )
        
        try:
            logger.info(f"Parsing chapter content: {chapter_href}")
            book = epub.read_epub(str(file_path_obj))
            
            # Get chapter item
            chapter_item = book.get_item_with_href(chapter_href)
            if not chapter_item:
                # Try without leading slash
                chapter_href = chapter_href.lstrip('/')
                chapter_item = book.get_item_with_href(chapter_href)
            
            if not chapter_item:
                logger.error(f"Chapter not found: {chapter_href}")
                return ChapterContentResponse(
                    elements=[],
                    word_count=0,
                    image_count=0,
                    success=False,
                    error=f"Chapter not found: {chapter_href}"
                )
            
            # Get HTML content
            html = chapter_item.get_content().decode('utf-8')
            
            # Parse HTML to ContentElement list
            elements = self.content_parser.parse_html(html, chapter_href)
            
            # Persist images and update their paths
            elements = self._persist_chapter_images(book, book_id, elements)
            
            # Count words and images
            word_count = self.content_parser.count_words(elements)
            image_count = self.content_parser.count_images(elements)
            
            logger.info(f"Successfully parsed chapter: {len(elements)} elements, {word_count} words, {image_count} images")
            
            return ChapterContentResponse(
                elements=elements,
                word_count=word_count,
                image_count=image_count,
                success=True
            )
            
        except Exception as e:
            logger.error(f"Failed to parse chapter content: {e}", exc_info=True)
            return ChapterContentResponse(
                elements=[],
                word_count=0,
                image_count=0,
                success=False,
                error=str(e)
            )
    
    def _persist_chapter_images(self, book: epub.EpubBook, book_id: int, elements: List[ContentElement]) -> List[ContentElement]:
        """Persist images found in chapter content and update their paths.
        
        Args:
            book: EpubBook instance
            book_id: Book ID for organizing images
            elements: List of ContentElement objects
            
        Returns:
            Updated list of ContentElement with persisted image paths
        """
        updated_elements = []
        
        for element in elements:
            if element.type == 'image' and element.src:
                # Try to get image data from EPUB
                image_data = self._get_image_data_from_epub(book, element.src)
                
                if image_data:
                    # Persist image and get new path
                    persisted_path = self._save_chapter_image(book_id, element.src, image_data)
                    
                    if persisted_path:
                        # Get image dimensions
                        width, height, aspect_ratio = self._get_image_dimensions(image_data)
                        
                        # Create updated image element
                        updated_element = ContentElement(
                            type='image',
                            src=persisted_path,
                            alt=element.alt,
                            width=width,
                            height=height,
                            aspect_ratio=aspect_ratio
                        )
                        updated_elements.append(updated_element)
                        logger.debug(f"Persisted image: {element.src} -> {persisted_path}")
                    else:
                        # Failed to persist, keep original
                        updated_elements.append(element)
                        logger.warning(f"Failed to persist image: {element.src}")
                else:
                    # Image not found in EPUB, keep original
                    updated_elements.append(element)
                    logger.warning(f"Image not found in EPUB: {element.src}")
            else:
                # Not an image element, keep as is
                updated_elements.append(element)
        
        return updated_elements
    
    def _get_image_data_from_epub(self, book: epub.EpubBook, image_path: str) -> Optional[bytes]:
        """Extract image data from EPUB by path.
        
        Args:
            book: EpubBook instance
            image_path: Image path within EPUB (e.g., 'images/chapter1-img1.png')
            
        Returns:
            Image binary data or None if not found
        """
        try:
            # Try to get image item by href
            image_item = book.get_item_with_href(image_path)
            
            if not image_item:
                # Try without leading slash
                image_path_stripped = image_path.lstrip('/')
                image_item = book.get_item_with_href(image_path_stripped)
            
            if not image_item:
                # Try with leading slash
                if not image_path.startswith('/'):
                    image_item = book.get_item_with_href('/' + image_path)
            
            if image_item:
                image_data = image_item.get_content()
                if image_data:
                    return image_data
            
            # If still not found, search through all items
            for item in book.get_items():
                if item.get_type() and item.get_type().startswith('image/'):
                    item_name = item.get_name()
                    # Check if the item name ends with our image path (handles different base paths)
                    if item_name.endswith(image_path) or item_name.endswith(image_path.lstrip('/')):
                        return item.get_content()
            
            return None
            
        except Exception as e:
            logger.error(f"Failed to extract image data for {image_path}: {e}")
            return None
    
    def _save_chapter_image(self, book_id: int, original_path: str, image_data: bytes) -> Optional[str]:
        """Save chapter image to persistent storage.
        
        Args:
            book_id: Book ID
            original_path: Original image path (used to extract extension)
            image_data: Image binary data
            
        Returns:
            Relative path to saved image (e.g., '16/abc123.jpg') or None if failed
        """
        try:
            # Create book-specific directory
            book_images_dir = config.get_book_images_dir() / str(book_id)
            book_images_dir.mkdir(parents=True, exist_ok=True)
            
            # Get extension from original path
            extension = Path(original_path).suffix
            if not extension or extension not in ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.svg', '.bmp']:
                extension = '.jpg'  # Default to jpg
            
            # Use SHA-256 hash of image data as filename (avoid duplicates)
            hash_obj = hashlib.sha256(image_data)
            hash_hex = hash_obj.hexdigest()[:16]  # Use first 16 characters
            
            filename = f"{hash_hex}{extension}"
            image_file_path = book_images_dir / filename
            
            # Check if file already exists (same content)
            if image_file_path.exists():
                logger.debug(f"Image already exists: {book_id}/{filename}")
                return f"{book_id}/{filename}"
            
            # Save image data
            with open(image_file_path, 'wb') as f:
                f.write(image_data)
            
            logger.debug(f"Saved chapter image: {book_id}/{filename} ({len(image_data)} bytes)")
            
            # Return relative path (bookId/filename)
            return f"{book_id}/{filename}"
            
        except Exception as e:
            logger.error(f"Failed to save chapter image: {e}", exc_info=True)
            return None

