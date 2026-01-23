"""Inline parser - parses inline styles and footnote references."""

import logging
from typing import List, Optional
from bs4 import NavigableString, Tag

from app.models import TextSpan
from app.footnote_parser import FootnoteParser

logger = logging.getLogger(__name__)


class InlineParser:
    """Parser for inline text styles and footnote references.
    
    Converts HTML inline elements to TextSpan objects with style information.
    Handles nested styles (e.g., <strong><em>text</em></strong>).
    """
    
    def __init__(self):
        self.footnote_counter = 0
    
    def reset_footnote_counter(self):
        """Reset footnote counter for each chapter."""
        self.footnote_counter = 0
    
    def parse(self, element: Tag, chapter_href: str) -> List[TextSpan]:
        """Parse element content into TextSpan list.
        
        Args:
            element: BeautifulSoup Tag element to parse
            chapter_href: Current chapter href for image path resolution
            
        Returns:
            List of TextSpan objects
        """
        spans: List[TextSpan] = []
        self._parse_node(element, [], None, spans, chapter_href)
        return spans
    
    def _parse_node(
        self,
        node,
        active_styles: List[str],
        active_link: Optional[str],
        spans: List[TextSpan],
        chapter_href: str
    ):
        """Recursively parse a node and its children.
        
        Args:
            node: Current node (Tag or NavigableString)
            active_styles: Stack of currently active styles
            active_link: Currently active link URL (if any)
            spans: Output list of TextSpan objects
            chapter_href: Current chapter href
        """
        if isinstance(node, NavigableString):
            # Text node
            text = str(node).strip()
            if text:
                spans.append(TextSpan(
                    text=text,
                    styles=active_styles.copy(),
                    link=active_link,
                    footnote_id=None,
                    footnote_image=None
                ))
            return
        
        if not isinstance(node, Tag):
            return
        
        tag_name = node.name.lower()
        
        # Handle footnote reference images
        if tag_name == 'img' and FootnoteParser.is_footnote_image(node):
            self._handle_footnote_image(node, active_styles, spans, chapter_href)
            return
        
        # Handle links
        if tag_name == 'a':
            self._handle_link(node, active_styles, active_link, spans, chapter_href)
            return
        
        # Handle style tags
        new_styles = active_styles.copy()
        
        if tag_name in ['strong', 'b']:
            new_styles.append('BOLD')
        elif tag_name in ['em', 'i']:
            new_styles.append('ITALIC')
        elif tag_name == 'u':
            new_styles.append('UNDERLINE')
        elif tag_name in ['s', 'del', 'strike']:
            new_styles.append('STRIKETHROUGH')
        elif tag_name == 'code':
            new_styles.append('CODE')
        
        # Recursively parse children
        for child in node.children:
            self._parse_node(child, new_styles, active_link, spans, chapter_href)
    
    def _handle_link(
        self,
        link: Tag,
        active_styles: List[str],
        active_link: Optional[str],
        spans: List[TextSpan],
        chapter_href: str
    ):
        """Handle <a> link element.
        
        Args:
            link: Link Tag element
            active_styles: Currently active styles
            active_link: Parent link URL (if any)
            spans: Output spans list
            chapter_href: Current chapter href
        """
        href = link.get('href', '')
        
        # Check if this is a footnote reference
        if href.startswith('#'):
            footnote_id = href[1:]  # Remove '#' prefix
            
            # Get link text
            text = link.get_text().strip()
            if text:
                self.footnote_counter += 1
                spans.append(TextSpan(
                    text=text,
                    styles=active_styles.copy(),
                    link=None,
                    footnote_id=footnote_id,
                    footnote_image=None
                ))
            return
        
        # Regular link - recursively parse children with link context
        for child in link.children:
            self._parse_node(child, active_styles, href, spans, chapter_href)
    
    def _handle_footnote_image(
        self,
        img: Tag,
        active_styles: List[str],
        spans: List[TextSpan],
        chapter_href: str
    ):
        """Handle footnote reference image.
        
        Args:
            img: Image Tag element
            active_styles: Currently active styles
            spans: Output spans list
            chapter_href: Current chapter href
        """
        src = img.get('src', '')
        alt = img.get('alt', '').strip()
        
        # Try to extract footnote ID from parent link
        footnote_id = None
        parent = img.parent
        if parent and parent.name == 'a':
            href = parent.get('href', '')
            if href.startswith('#'):
                footnote_id = href[1:]
        
        # If no ID from link, try to extract from image
        if not footnote_id:
            footnote_id = FootnoteParser.extract_footnote_number_from_image(img)
        
        if not footnote_id:
            # Fallback: use counter
            self.footnote_counter += 1
            footnote_id = f"fn{self.footnote_counter}"
        
        # Use alt text or placeholder
        text = alt if alt else f"[{self.footnote_counter}]"
        
        self.footnote_counter += 1
        
        spans.append(TextSpan(
            text=text,
            styles=active_styles.copy(),
            link=None,
            footnote_id=footnote_id,
            footnote_image=self._normalize_image_path(src, chapter_href)
        ))
    
    def _normalize_image_path(self, src: str, chapter_href: str) -> str:
        """Normalize image path relative to chapter.
        
        Args:
            src: Image source path
            chapter_href: Current chapter href
            
        Returns:
            Normalized path
        """
        if not src:
            return ""
        
        # Remove query parameters
        src = src.split('?')[0]
        
        # If absolute path, return as is
        if src.startswith('/'):
            return src.lstrip('/')
        
        # Handle relative paths
        if '../' in src or './' in src:
            # Get chapter directory
            chapter_dir = '/'.join(chapter_href.split('/')[:-1])
            
            # Resolve relative path
            parts = chapter_dir.split('/') if chapter_dir else []
            for part in src.split('/'):
                if part == '..':
                    if parts:
                        parts.pop()
                elif part and part != '.':
                    parts.append(part)
            
            return '/'.join(parts)
        
        # Same directory as chapter
        chapter_dir = '/'.join(chapter_href.split('/')[:-1])
        if chapter_dir:
            return f"{chapter_dir}/{src}"
        return src
