"""Content parser - parses HTML chapter content to structured elements."""

import logging
import re
from typing import List
from bs4 import BeautifulSoup, Tag, NavigableString

from app.models import ContentElement, TextSpan, ListItemModel
from app.inline_parser import InlineParser
from app.footnote_parser import FootnoteParser

logger = logging.getLogger(__name__)


class ContentParser:
    """Parser for EPUB chapter HTML content.
    
    Converts HTML elements to structured ContentElement objects.
    """
    
    def __init__(self):
        self.inline_parser = InlineParser()
        self.footnote_parser = FootnoteParser()
    
    def parse_html(self, html: str, chapter_href: str) -> List[ContentElement]:
        """Parse HTML content to ContentElement list.
        
        Args:
            html: HTML content string
            chapter_href: Chapter href for image path resolution
            
        Returns:
            List of ContentElement objects
        """
        elements: List[ContentElement] = []
        
        # Reset footnote counter for each chapter
        self.inline_parser.reset_footnote_counter()
        
        try:
            soup = BeautifulSoup(html, 'lxml')
            body = soup.body if soup.body else soup
            
            # Parse body children
            for child in body.children:
                if isinstance(child, Tag):
                    self._parse_element(child, elements, chapter_href)
            
            logger.debug(f"Parsed {len(elements)} content elements")
            return elements
            
        except Exception as e:
            logger.error(f"Failed to parse HTML content: {e}", exc_info=True)
            return []
    
    def _parse_element(self, element: Tag, elements: List[ContentElement], chapter_href: str):
        """Parse a single HTML element.
        
        Args:
            element: BeautifulSoup Tag element
            elements: Output list of ContentElement
            chapter_href: Chapter href
        """
        tag_name = element.name.lower()
        
        # Heading
        if tag_name in ['h1', 'h2', 'h3', 'h4', 'h5', 'h6']:
            level = int(tag_name[1])
            text = element.get_text().strip()
            if text:
                elements.append(ContentElement(
                    type='heading',
                    level=level,
                    text=text
                ))
        
        # Paragraph
        elif tag_name == 'p':
            self._parse_paragraph(element, elements, chapter_href)
        
        # Image
        elif tag_name == 'img':
            self._parse_image(element, elements, chapter_href)
        
        # Figure (may contain image or SVG)
        elif tag_name == 'figure':
            self._parse_figure(element, elements, chapter_href)
        
        # Blockquote
        elif tag_name == 'blockquote':
            spans = self.inline_parser.parse(element, chapter_href)
            if spans:
                elements.append(ContentElement(
                    type='quote',
                    spans=spans
                ))
        
        # Code block
        elif tag_name in ['pre', 'code']:
            text = element.get_text()
            if text:
                elements.append(ContentElement(
                    type='code',
                    text=text
                ))
        
        # List
        elif tag_name in ['ul', 'ol']:
            self._parse_list(element, elements, chapter_href)
        
        # Horizontal rule
        elif tag_name == 'hr':
            elements.append(ContentElement(type='divider'))
        
        # Aside (may be footnote)
        elif tag_name == 'aside':
            self._parse_aside(element, elements, chapter_href)
        
        # Container elements
        elif tag_name in ['div', 'section', 'article']:
            self._parse_container(element, elements, chapter_href)
    
    def _parse_paragraph(self, element: Tag, elements: List[ContentElement], chapter_href: str):
        """Parse paragraph element.
        
        Args:
            element: Paragraph Tag element
            elements: Output list
            chapter_href: Chapter href
        """
        # Check if it's a footnote definition
        footnote_id = self.footnote_parser.extract_footnote_definition_id(element)
        if footnote_id:
            spans = self.inline_parser.parse(element, chapter_href)
            if spans:
                elements.append(ContentElement(
                    type='footnote',
                    id=footnote_id,
                    spans=spans
                ))
            return
        
        # Check if paragraph contains images (exclude footnote images)
        images = element.find_all('img')
        for img in images:
            if not self.footnote_parser.is_footnote_image(img):
                self._parse_image(img, elements, chapter_href)
        
        # Parse text content
        spans = self.inline_parser.parse(element, chapter_href)
        if spans:
            elements.append(ContentElement(
                type='paragraph',
                spans=spans
            ))
    
    def _parse_image(self, element: Tag, elements: List[ContentElement], chapter_href: str):
        """Parse image element.
        
        Args:
            element: Image Tag element
            elements: Output list
            chapter_href: Chapter href
        """
        # Skip footnote images
        if self.footnote_parser.is_footnote_image(element):
            return
        
        src = element.get('src', '')
        alt = element.get('alt', '')
        
        if src:
            normalized_src = self._normalize_image_path(src, chapter_href)
            elements.append(ContentElement(
                type='image',
                src=normalized_src,
                alt=alt if alt else None
            ))
    
    def _parse_figure(self, element: Tag, elements: List[ContentElement], chapter_href: str):
        """Parse figure element (may contain img or SVG).
        
        Args:
            element: Figure Tag element
            elements: Output list
            chapter_href: Chapter href
        """
        # Look for regular img tags
        img_elements = element.find_all('img', recursive=True)
        if img_elements:
            for img in img_elements:
                if not self.footnote_parser.is_footnote_image(img):
                    self._parse_image(img, elements, chapter_href)
            return
        
        # Look for SVG image tags
        svg_images = element.find_all('image', recursive=True)
        if svg_images:
            for img in svg_images:
                # SVG image uses xlink:href or href attribute
                src = img.get('xlink:href') or img.get('href', '')
                if src:
                    normalized_src = self._normalize_image_path(src, chapter_href)
                    elements.append(ContentElement(
                        type='image',
                        src=normalized_src,
                        alt=''
                    ))
            return
        
        # If no images found, recursively parse children
        for child in element.children:
            if isinstance(child, Tag):
                self._parse_element(child, elements, chapter_href)
    
    def _parse_aside(self, element: Tag, elements: List[ContentElement], chapter_href: str):
        """Parse aside element (may be footnote container).
        
        Args:
            element: Aside Tag element
            elements: Output list
            chapter_href: Chapter href
        """
        # Check if it's a footnote container
        if self.footnote_parser.is_footnote_container(element):
            self._parse_footnote_container(element, elements, chapter_href)
        else:
            # Regular aside, parse children
            for child in element.children:
                if isinstance(child, Tag):
                    self._parse_element(child, elements, chapter_href)
    
    def _parse_footnote_container(self, container: Tag, elements: List[ContentElement], chapter_href: str):
        """Parse footnote container with nested ol/li structure.
        
        Args:
            container: Footnote container element
            elements: Output list
            chapter_href: Chapter href
        """
        # Look for li elements with id (footnote items)
        footnote_lis = container.find_all('li')
        
        if footnote_lis:
            for li in footnote_lis:
                li_id = li.get('id', '')
                if li_id:
                    spans = self.inline_parser.parse(li, chapter_href)
                    if spans:
                        elements.append(ContentElement(
                            type='footnote',
                            id=li_id,
                            spans=spans
                        ))
        else:
            # No nested li, use container's id
            container_id = container.get('id', '')
            if container_id:
                spans = self.inline_parser.parse(container, chapter_href)
                if spans:
                    elements.append(ContentElement(
                        type='footnote',
                        id=container_id,
                        spans=spans
                    ))
    
    def _parse_list(self, element: Tag, elements: List[ContentElement], chapter_href: str):
        """Parse list element (ul/ol).
        
        Args:
            element: List Tag element
            elements: Output list
            chapter_href: Chapter href
        """
        ordered = element.name == 'ol'
        items = []
        
        for li in element.find_all('li', recursive=False):
            spans = self.inline_parser.parse(li, chapter_href)
            if spans:
                items.append(ListItemModel(spans=spans))
        
        if items:
            elements.append(ContentElement(
                type='listBlock',
                ordered=ordered,
                items=items
            ))
    
    def _parse_container(self, element: Tag, elements: List[ContentElement], chapter_href: str):
        """Parse container element (div/section/article).
        
        Args:
            element: Container Tag element
            elements: Output list
            chapter_href: Chapter href
        """
        # Check if it's a footnote definition
        footnote_id = self.footnote_parser.extract_footnote_definition_id(element)
        if footnote_id:
            spans = self.inline_parser.parse(element, chapter_href)
            if spans:
                elements.append(ContentElement(
                    type='footnote',
                    id=footnote_id,
                    spans=spans
                ))
            return
        
        # Check if contains direct text with <br> tags
        has_direct_text = any(
            isinstance(node, NavigableString) and node.strip()
            for node in element.children
        )
        has_br_tags = element.find('br') is not None
        
        if has_direct_text and has_br_tags:
            self._parse_text_with_breaks(element, elements, chapter_href)
        else:
            # Recursively parse children
            for child in element.children:
                if isinstance(child, Tag):
                    self._parse_element(child, elements, chapter_href)
    
    def _parse_text_with_breaks(self, element: Tag, elements: List[ContentElement], chapter_href: str):
        """Parse text content with <br> tags.
        
        Args:
            element: Container element with mixed text and br tags
            elements: Output list
            chapter_href: Chapter href
        """
        current_line_text = []
        
        for node in element.children:
            if isinstance(node, NavigableString):
                text = str(node)
                if text.strip():
                    current_line_text.append(text)
            elif isinstance(node, Tag):
                if node.name == 'br':
                    # Line break - flush current line
                    line = ''.join(current_line_text).strip()
                    if line:
                        elements.append(ContentElement(
                            type='paragraph',
                            spans=[TextSpan(text=line)]
                        ))
                    current_line_text = []
                elif node.name == 'img':
                    # Image - flush current line first
                    if self.footnote_parser.is_footnote_image(node):
                        continue
                    line = ''.join(current_line_text).strip()
                    if line:
                        elements.append(ContentElement(
                            type='paragraph',
                            spans=[TextSpan(text=line)]
                        ))
                        current_line_text = []
                    self._parse_image(node, elements, chapter_href)
                else:
                    # Other tag - add its text
                    current_line_text.append(node.get_text())
        
        # Flush remaining text
        line = ''.join(current_line_text).strip()
        if line:
            elements.append(ContentElement(
                type='paragraph',
                spans=[TextSpan(text=line)]
            ))
    
    def _normalize_image_path(self, src: str, chapter_href: str) -> str:
        """Normalize image path relative to chapter.
        
        Args:
            src: Image source path
            chapter_href: Chapter href
            
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
    
    def count_words(self, elements: List[ContentElement]) -> int:
        """Count words in content elements.
        
        Handles CJK characters (count each character) and English words.
        
        Args:
            elements: List of ContentElement
            
        Returns:
            Word count
        """
        count = 0
        
        for element in elements:
            if element.type == 'paragraph' and element.spans:
                for span in element.spans:
                    count += self._count_words_in_text(span.text)
            elif element.type == 'heading' and element.text:
                count += self._count_words_in_text(element.text)
            elif element.type == 'quote' and element.spans:
                for span in element.spans:
                    count += self._count_words_in_text(span.text)
            elif element.type == 'code' and element.text:
                count += self._count_words_in_text(element.text)
            elif element.type == 'listBlock' and element.items:
                for item in element.items:
                    for span in item.spans:
                        count += self._count_words_in_text(span.text)
        
        return count
    
    def _count_words_in_text(self, text: str) -> int:
        """Count words in a single text string.
        
        CJK characters: count each character
        English/numbers: count words
        
        Args:
            text: Text string
            
        Returns:
            Word count
        """
        # CJK character ranges (Common CJK Unified Ideographs, Hiragana, Katakana, Hangul)
        # Note: Removed \u{20000}-\u{2a6df} (CJK Extension B-F) as Python re doesn't support \u{} syntax
        # and these rarely appear in ebooks. Use regex module if needed.
        cjk_pattern = re.compile(r'[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff\u3040-\u309f\u30a0-\u30ff\uac00-\ud7af]', re.UNICODE)
        
        # Count CJK characters
        cjk_count = len(cjk_pattern.findall(text))
        
        # Remove CJK characters and count English words
        non_cjk_text = cjk_pattern.sub('', text)
        if non_cjk_text.strip():
            # Split by whitespace and count words with letters/digits
            words = re.findall(r'\b\w+\b', non_cjk_text)
            word_count = len(words)
        else:
            word_count = 0
        
        return cjk_count + word_count
    
    def count_images(self, elements: List[ContentElement]) -> int:
        """Count images in content elements.
        
        Args:
            elements: List of ContentElement
            
        Returns:
            Image count
        """
        return sum(1 for elem in elements if elem.type == 'image')
