"""Structure parser - parses EPUB chapter structure using ebooklib."""

import logging
from pathlib import Path
from typing import List, Dict, Tuple

from ebooklib import epub

from app.models import ChapterInfo

logger = logging.getLogger(__name__)


class StructureParser:
    """Parser for EPUB book structure (spine and TOC).
    
    Uses ebooklib's built-in TOC parsing instead of manually parsing nav.xhtml/ncx.
    """
    
    def parse_structure(self, file_path: str) -> Tuple[List[ChapterInfo], int]:
        """Parse EPUB structure to get chapter list.
        
        Args:
            file_path: Path to EPUB file
            
        Returns:
            Tuple of (chapter_list, total_chapters)
            
        Raises:
            FileNotFoundError: If file doesn't exist
            Exception: If parsing fails
        """
        file_path_obj = Path(file_path)
        if not file_path_obj.exists():
            raise FileNotFoundError(f"File not found: {file_path}")
        
        try:
            logger.info(f"Parsing EPUB structure from: {file_path}")
            book = epub.read_epub(str(file_path_obj))
            
            # Get spine (chapter order)
            spine_items = self._get_spine_items(book)
            logger.info(f"Found {len(spine_items)} spine items")
            
            # Parse TOC using ebooklib's built-in toc
            toc_map = self._parse_toc(book, spine_items)
            logger.info(f"Found {len(toc_map)} TOC entries")
            
            # Build chapter list
            chapters = []
            for index, href in enumerate(spine_items):
                toc_info = toc_map.get(index)
                chapters.append(ChapterInfo(
                    index=index,
                    href=href,
                    in_toc=toc_info is not None,
                    title=toc_info['title'] if toc_info else None,
                    level=toc_info['level'] if toc_info else 0
                ))
            
            logger.info(f"Successfully parsed {len(chapters)} chapters")
            return chapters, len(chapters)
            
        except Exception as e:
            logger.error(f"Failed to parse EPUB structure: {e}", exc_info=True)
            raise
    
    def _get_spine_items(self, book: epub.EpubBook) -> List[str]:
        """Get ordered list of document hrefs from spine.
        
        Args:
            book: EpubBook instance
            
        Returns:
            List of document hrefs
        """
        spine_items = []
        
        for item_id, is_linear in book.spine:
            item = book.get_item_with_id(item_id)
            if item:
                href = item.get_name()
                # Normalize href (remove leading '/')
                href = href.lstrip('/')
                spine_items.append(href)
        
        return spine_items
    
    def _parse_toc(self, book: epub.EpubBook, spine_items: List[str]) -> Dict[int, dict]:
        """Parse TOC using ebooklib's book.toc.
        
        Args:
            book: EpubBook instance
            spine_items: List of spine hrefs
            
        Returns:
            Dict mapping spine index to TOC info {title, level}
        """
        # Build href to index map
        href_to_index = {href: idx for idx, href in enumerate(spine_items)}
        
        # Parse TOC entries
        toc_entries = []
        self._parse_toc_recursive(book.toc, href_to_index, toc_entries, level=0)
        
        # Convert to dict (keep first occurrence for each index)
        toc_map = {}
        for index, title, level in toc_entries:
            if index not in toc_map:
                toc_map[index] = {'title': title, 'level': level}
        
        logger.info(f"Parsed {len(toc_map)} entries from TOC")
        return toc_map
    
    def _parse_toc_recursive(
        self,
        toc_items: list,
        href_to_index: Dict[str, int],
        entries: List[Tuple[int, str, int]],
        level: int,
        max_depth: int = 50
    ):
        """Recursively parse ebooklib's TOC structure.
        
        The TOC structure can be:
        - List of epub.Link objects
        - List of tuples: (epub.Section, [children])
        - Mixed list of Link and tuples
        
        Args:
            toc_items: List of epub.Link or tuples
            href_to_index: Mapping from href to spine index
            entries: Output list of (index, title, level) tuples
            level: Current depth level
            max_depth: Maximum recursion depth
        """
        if level > max_depth:
            logger.warning(f"Reached max recursion depth {max_depth} in TOC")
            return
        
        for item in toc_items:
            if isinstance(item, epub.Link):
                # Direct link to a chapter
                href = item.href
                # Remove fragment identifier
                href = href.split('#')[0]
                # Normalize path
                href = href.lstrip('/')
                
                title = item.title
                
                index = href_to_index.get(href)
                if index is not None and title:
                    entries.append((index, title, level))
            
            elif isinstance(item, tuple) and len(item) >= 2:
                # Tuple format: (Section/Link, [children])
                section_or_link = item[0]
                children = item[1] if len(item) > 1 else []
                
                # If the section itself is a link, add it
                if isinstance(section_or_link, epub.Link):
                    href = section_or_link.href.split('#')[0].lstrip('/')
                    title = section_or_link.title
                    index = href_to_index.get(href)
                    if index is not None and title:
                        entries.append((index, title, level))
                
                # Parse children with increased level
                if children and isinstance(children, list):
                    self._parse_toc_recursive(children, href_to_index, entries, level + 1, max_depth)
