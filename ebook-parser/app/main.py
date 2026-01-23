"""FastAPI application for eBook parsing microservice."""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

from app.models import (
    ParseRequest, 
    BookMetadataResponse, 
    CoverExtractionResponse,
    HealthResponse
)
from app.parser import EpubParser
from app.config import config
from app import __version__

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan events."""
    # Startup
    config.setup_logging()
    config.ensure_directories()
    logger.info("eBook Parser Service starting up...")
    logger.info(f"Covers directory: {config.get_covers_dir()}")
    logger.info(f"Book images directory: {config.get_book_images_dir()}")
    
    yield
    
    # Shutdown
    logger.info("eBook Parser Service shutting down...")


app = FastAPI(
    title="eBook Parser Service",
    description="Microservice for parsing eBook files (EPUB, etc.) using specialized libraries",
    version=__version__,
    lifespan=lifespan
)

# Initialize parser
parser = EpubParser()


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint.
    
    Returns service status and version information.
    """
    return HealthResponse(
        status="healthy",
        version=__version__
    )


@app.post("/api/parse/metadata", response_model=BookMetadataResponse)
async def parse_metadata(request: ParseRequest):
    """Extract metadata from EPUB file.
    
    Args:
        request: ParseRequest with file_path and book_id
        
    Returns:
        BookMetadataResponse with extracted metadata
        
    Raises:
        HTTPException: If file not found or parsing fails
    """
    try:
        logger.info(f"Received metadata extraction request for: {request.file_path}")
        metadata = parser.extract_metadata(request.file_path)
        logger.info(f"Successfully extracted metadata for book ID {request.book_id}")
        return metadata
        
    except FileNotFoundError as e:
        logger.error(f"File not found: {request.file_path}")
        raise HTTPException(status_code=404, detail=str(e))
        
    except Exception as e:
        logger.error(f"Failed to extract metadata: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to extract metadata: {str(e)}")


@app.post("/api/parse/cover", response_model=CoverExtractionResponse)
async def extract_cover(request: ParseRequest):
    """Extract cover image from EPUB file.
    
    Args:
        request: ParseRequest with file_path and book_id
        
    Returns:
        CoverExtractionResponse with cover path and dimensions
    """
    try:
        logger.info(f"Received cover extraction request for: {request.file_path}")
        result = parser.extract_cover(request.file_path, request.book_id)
        
        if result.success:
            logger.info(f"Successfully extracted cover for book ID {request.book_id}")
        else:
            logger.warning(f"Failed to extract cover for book ID {request.book_id}: {result.error}")
        
        return result
        
    except Exception as e:
        logger.error(f"Unexpected error extracting cover: {e}", exc_info=True)
        return CoverExtractionResponse(
            success=False,
            error=f"Unexpected error: {str(e)}"
        )


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """Global exception handler."""
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"}
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=config.HOST,
        port=config.PORT,
        reload=True,
        log_level=config.LOG_LEVEL.lower()
    )
