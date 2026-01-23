"""Configuration management for the eBook parser service."""

import os
import logging
from pathlib import Path
import tempfile


class Config:
    """Application configuration."""
    
    # Service configuration
    HOST: str = os.getenv("HOST", "0.0.0.0")
    PORT: int = int(os.getenv("PORT", "7920"))
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")
    
    # Limits
    MAX_DESCRIPTION_LENGTH: int = 1000
    MAX_TAG_LENGTH: int = 50
    MAX_TAGS_COUNT: int = 20
    
    @classmethod
    def get_covers_dir(cls) -> Path:
        """Get covers directory path."""
        default_path = "/app/covers"
        # Check if running in Docker or dev environment
        if not os.path.exists("/app") or not os.access("/app", os.W_OK):
            default_path = os.path.join(tempfile.gettempdir(), "ebook-parser-covers")
        return Path(os.getenv("COVERS_DIR", default_path))
    
    @classmethod
    def get_book_images_dir(cls) -> Path:
        """Get book images directory path."""
        default_path = "/app/book_images"
        # Check if running in Docker or dev environment
        if not os.path.exists("/app") or not os.access("/app", os.W_OK):
            default_path = os.path.join(tempfile.gettempdir(), "ebook-parser-book-images")
        return Path(os.getenv("BOOK_IMAGES_DIR", default_path))
    
    @classmethod
    def setup_logging(cls):
        """Configure logging."""
        logging.basicConfig(
            level=getattr(logging, cls.LOG_LEVEL.upper()),
            format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
        )
    
    @classmethod
    def ensure_directories(cls):
        """Ensure required directories exist."""
        cls.get_covers_dir().mkdir(parents=True, exist_ok=True)
        cls.get_book_images_dir().mkdir(parents=True, exist_ok=True)


config = Config()
