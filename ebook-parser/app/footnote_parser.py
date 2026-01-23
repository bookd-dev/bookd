"""Footnote parser - handles footnote references and definitions."""

import logging
from typing import Optional
from bs4 import BeautifulSoup, Tag

logger = logging.getLogger(__name__)


class FootnoteParser:
    """Parser for EPUB footnotes.
    
    Handles:
    - Footnote references (links and special images)
    - Footnote definitions (aside, div, p with specific attributes)
    """
    
    # Footnote reference image patterns
    FOOTNOTE_IMAGE_PATTERNS = [
        'noteref',
        'footnote',
        'sup',
        'reference'
    ]
    
    @staticmethod
    def is_footnote_image(element: Tag) -> bool:
        """Check if an image element is a footnote reference image.
        
        Args:
            element: BeautifulSoup Tag element
            
        Returns:
            True if it's a footnote reference image
        """
        if element.name != 'img':
            return False
        
        src = element.get('src', '').lower()
        alt = element.get('alt', '').lower()
        
        # Check if src or alt contains footnote patterns
        for pattern in FootnoteParser.FOOTNOTE_IMAGE_PATTERNS:
            if pattern in src or pattern in alt:
                return True
        
        return False
    
    @staticmethod
    def extract_footnote_reference_id(element: Tag) -> Optional[str]:
        """Extract footnote ID from a reference element (link or image).
        
        Args:
            element: BeautifulSoup Tag element (usually <a> or <img>)
            
        Returns:
            Footnote ID or None
        """
        # Case 1: <a href="#note1"> link
        if element.name == 'a':
            href = element.get('href', '')
            if href.startswith('#'):
                return href[1:]  # Remove '#' prefix
            return None
        
        # Case 2: Footnote image with parent link
        if element.name == 'img' and FootnoteParser.is_footnote_image(element):
            parent = element.parent
            if parent and parent.name == 'a':
                href = parent.get('href', '')
                if href.startswith('#'):
                    return href[1:]
        
        return None
    
    @staticmethod
    def is_footnote_container(element: Tag) -> bool:
        """Check if an element is a footnote definition container.
        
        Args:
            element: BeautifulSoup Tag element
            
        Returns:
            True if it's a footnote container
        """
        # Check epub:type="footnote" or "endnote"
        epub_type = element.get('epub:type', '')
        if epub_type in ['footnote', 'endnote']:
            return True
        
        # Check role attribute
        role = element.get('role', '')
        if role in ['doc-footnote', 'doc-endnote']:
            return True
        
        # Check class attribute
        class_attr = element.get('class', [])
        if isinstance(class_attr, list):
            class_str = ' '.join(class_attr).lower()
        else:
            class_str = str(class_attr).lower()
        
        footnote_classes = ['footnote', 'endnote', 'note']
        for fn_class in footnote_classes:
            if fn_class in class_str:
                return True
        
        return False
    
    @staticmethod
    def extract_footnote_definition_id(element: Tag) -> Optional[str]:
        """Extract footnote ID from a definition element.
        
        Args:
            element: BeautifulSoup Tag element (aside, div, p, etc.)
            
        Returns:
            Footnote ID or None
        """
        # Check if it's a footnote container
        if not FootnoteParser.is_footnote_container(element):
            # Also check if parent is a footnote container
            parent = element.parent
            if not parent or not FootnoteParser.is_footnote_container(parent):
                return None
        
        # Get id attribute
        elem_id = element.get('id', '')
        if elem_id:
            return elem_id
        
        # Check parent's id if element doesn't have one
        parent = element.parent
        if parent:
            parent_id = parent.get('id', '')
            if parent_id and FootnoteParser.is_footnote_container(parent):
                return parent_id
        
        return None
    
    @staticmethod
    def extract_footnote_number_from_image(element: Tag) -> Optional[str]:
        """Extract footnote number from image alt text or src.
        
        Args:
            element: Image Tag element
            
        Returns:
            Footnote number or None
        """
        if not FootnoteParser.is_footnote_image(element):
            return None
        
        # Try to extract number from alt text
        alt = element.get('alt', '')
        # Look for patterns like "1", "[1]", "(1)", "注1"
        import re
        match = re.search(r'[\[\(]?(\d+)[\]\)]?', alt)
        if match:
            return f"fn{match.group(1)}"
        
        # Try to extract from src
        src = element.get('src', '')
        match = re.search(r'(\d+)', src)
        if match:
            return f"fn{match.group(1)}"
        
        return None
