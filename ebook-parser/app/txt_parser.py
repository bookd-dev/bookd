"""TXT file parser for chapter detection and content extraction."""

import re
import logging
from typing import List, Tuple, Optional

from app.models import (
    TxtParseRule,
    TxtChapterInfo,
    TxtStructureResponse,
    TxtContentResponse,
    ContentElement,
    TextSpan
)

logger = logging.getLogger(__name__)


class TxtParser:
    """Parser for TXT files with customizable chapter detection rules."""
    
    # Fallback patterns (used when no custom rules provided)
    FALLBACK_PATTERNS = [
        # English: Chapter 1, Chapter One, Ch.1, Ch 1
        r'^(Chapter|CHAPTER|Ch\.|Ch)\s*([0-9]+|One|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten).*$',
        
        # Chinese numbers: 第一章, 第1章, 第一节, 第1节, 第一回, 第1回
        r'^第([零一二三四五六七八九十百千万0-9]+)[章节回].*$',
        
        # Simplified: 一、二、三、
        r'^([零一二三四五六七八九十百千万]+)[、．.].*$',
        
        # Arabic numbers: 1., 1、, 01.
        r'^([0-9]{1,3})[、．.].*$',
        
        # Volume/Part/Book
        r'^第([零一二三四五六七八九十百千万0-9]+)[卷部篇].*$'
    ]
    
    def parse_structure(self, file_path: str, rules: List[TxtParseRule] = None) -> TxtStructureResponse:
        """Parse TXT file structure to detect chapters.
        
        Args:
            file_path: Absolute path to TXT file
            rules: Custom parse rules (if empty, use fallback patterns)
            
        Returns:
            TxtStructureResponse with detected chapters
        """
        try:
            # Read file with multiple encoding attempts
            full_text = self._read_file(file_path)
            if full_text is None:
                return TxtStructureResponse(
                    chapters=[],
                    total_chapters=0,
                    full_text_length=0,
                    success=False,
                    error="Failed to read file with supported encodings"
                )
            
            # Get chapter patterns
            patterns = self._get_patterns(rules)
            
            # Detect chapters
            chapters = self._detect_chapters(full_text, patterns)
            
            logger.info(f"Detected {len(chapters)} chapters in TXT file: {file_path}")
            
            return TxtStructureResponse(
                chapters=chapters,
                total_chapters=len(chapters),
                full_text_length=len(full_text),
                success=True
            )
            
        except Exception as e:
            logger.error(f"Failed to parse TXT structure: {e}", exc_info=True)
            return TxtStructureResponse(
                chapters=[],
                total_chapters=0,
                full_text_length=0,
                success=False,
                error=str(e)
            )
    
    def extract_chapter_content(
        self,
        file_path: str,
        start_pos: int,
        end_pos: int,
        chapter_title: Optional[str] = None
    ) -> TxtContentResponse:
        """Extract chapter content from TXT file.
        
        Args:
            file_path: Absolute path to TXT file
            start_pos: Start position (character index)
            end_pos: End position (character index)
            chapter_title: Chapter title (optional)
            
        Returns:
            TxtContentResponse with content elements
        """
        try:
            # Read file
            full_text = self._read_file(file_path)
            if full_text is None:
                return TxtContentResponse(
                    elements=[],
                    word_count=0,
                    success=False,
                    error="Failed to read file"
                )
            
            # Extract chapter text
            chapter_text = full_text[start_pos:end_pos]
            
            # Normalize line breaks
            normalized_text = chapter_text.replace('\r\n', '\n').replace('\r', '\n')
            lines = normalized_text.split('\n')
            
            # Convert to content elements
            elements = []
            is_first_non_empty = True
            
            for line in lines:
                trimmed_line = line.strip()
                
                if not trimmed_line:
                    # Skip empty lines
                    continue
                
                # First non-empty line: check if it's the chapter title
                if is_first_non_empty and chapter_title and trimmed_line.startswith(chapter_title.strip()):
                    elements.append(ContentElement(
                        type="heading",
                        level=1,
                        text=trimmed_line
                    ))
                    is_first_non_empty = False
                else:
                    # Each line is a paragraph
                    elements.append(ContentElement(
                        type="paragraph",
                        spans=[TextSpan(text=trimmed_line)]
                    ))
                    is_first_non_empty = False
            
            # Count words
            word_count = self._count_words(chapter_text)
            
            return TxtContentResponse(
                elements=elements,
                word_count=word_count,
                success=True
            )
            
        except Exception as e:
            logger.error(f"Failed to extract chapter content: {e}", exc_info=True)
            return TxtContentResponse(
                elements=[],
                word_count=0,
                success=False,
                error=str(e)
            )
    
    def _read_file(self, file_path: str) -> Optional[str]:
        """Read file with multiple encoding attempts.
        
        Args:
            file_path: Absolute path to file
            
        Returns:
            File content as string, or None if failed
        """
        encodings = ['utf-8', 'gbk', 'gb2312', 'big5', 'utf-16', 'iso-8859-1']
        
        for encoding in encodings:
            try:
                with open(file_path, 'r', encoding=encoding) as f:
                    content = f.read()
                logger.debug(f"Successfully read file with encoding: {encoding}")
                return content
            except (UnicodeDecodeError, LookupError):
                continue
            except Exception as e:
                logger.error(f"Failed to read file with encoding {encoding}: {e}")
                continue
        
        logger.error(f"Failed to read file with all supported encodings: {file_path}")
        return None
    
    def _get_patterns(self, rules: Optional[List[TxtParseRule]]) -> List[re.Pattern]:
        """Get regex patterns from rules or fallback patterns.
        
        Args:
            rules: Custom parse rules
            
        Returns:
            List of compiled regex patterns
        """
        if rules and len(rules) > 0:
            # Sort by priority
            sorted_rules = sorted(rules, key=lambda r: r.priority)
            
            patterns = []
            for rule in sorted_rules:
                if not rule.enabled:
                    continue
                
                try:
                    pattern = re.compile(rule.rule)
                    patterns.append(pattern)
                except re.error as e:
                    logger.error(f"Invalid regex pattern for rule '{rule.name}': {rule.rule}, error: {e}")
                    continue
            
            if patterns:
                logger.info(f"Using {len(patterns)} custom parse rules")
                return patterns
            else:
                logger.warning("No valid custom rules, falling back to default patterns")
        
        # Use fallback patterns
        logger.info("Using fallback chapter detection patterns")
        return [re.compile(p) for p in self.FALLBACK_PATTERNS]
    
    def _detect_chapters(self, text: str, patterns: List[re.Pattern]) -> List[TxtChapterInfo]:
        """Detect chapters in text using patterns.
        
        Args:
            text: Full text content
            patterns: Compiled regex patterns
            
        Returns:
            List of detected chapters
        """
        chapters = []
        lines = text.split('\n')
        
        current_chapter_start = 0
        chapter_index = 0
        last_chapter_line = 0
        current_position = 0
        
        for line_index, line in enumerate(lines):
            trimmed_line = line.strip()
            line_length = len(line) + 1  # +1 for newline
            
            # Skip empty lines
            if trimmed_line:
                # Check if line matches any chapter pattern
                for pattern in patterns:
                    if pattern.match(trimmed_line):
                        # Save previous chapter (if not first)
                        if chapter_index > 0:
                            chapters.append(TxtChapterInfo(
                                index=chapter_index - 1,
                                title=lines[last_chapter_line].strip(),
                                start_pos=current_chapter_start,
                                end_pos=current_position,
                                level=0
                            ))
                        
                        # Start new chapter
                        current_chapter_start = current_position
                        last_chapter_line = line_index
                        chapter_index += 1
                        
                        logger.debug(f"Found chapter {chapter_index}: {trimmed_line}")
                        break  # Don't check other patterns for this line
            
            current_position += line_length
        
        # Add last chapter or entire text as one chapter
        if chapter_index > 0:
            chapters.append(TxtChapterInfo(
                index=chapter_index - 1,
                title=lines[last_chapter_line].strip(),
                start_pos=current_chapter_start,
                end_pos=len(text),
                level=0
            ))
        else:
            # No chapters detected, use entire text as one chapter
            chapters.append(TxtChapterInfo(
                index=0,
                title="全文",
                start_pos=0,
                end_pos=len(text),
                level=0
            ))
        
        return chapters
    
    def _count_words(self, text: str) -> int:
        """Count words in text.
        
        For Chinese text: counts characters
        For English text: counts words (space-separated)
        
        Args:
            text: Text content
            
        Returns:
            Word count
        """
        # Count Chinese characters (CJK Unified Ideographs)
        chinese_count = len(re.findall(r'[\u4e00-\u9fff]', text))
        
        # Count English words
        english_words = re.findall(r'[a-zA-Z]+', text)
        english_count = len(english_words)
        
        return chinese_count + english_count
