# EPUB 解析器模块

本模块负责解析 EPUB 电子书格式，将其转换为结构化的章节和内容数据。

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                        EpubParser                           │
│  主协调器，对外暴露解析接口                                    │
│  - parseStructure(file): 解析书籍结构                        │
│  - parseChapterContent(file, href): 解析章节内容             │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌────────────────────┐
│EpubPackageReader│  │  EpubTocParser  │  │EpubResourceExtractor│
│  包结构读取      │  │   目录解析       │  │    资源提取         │
└─────────────────┘  └─────────────────┘  └────────────────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │  EpubContentParser  │
                    │   HTML 内容解析      │
                    └─────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
     ┌──────────────┐ ┌──────────────┐ ┌─────────────┐
     │FootnoteParser│ │ InlineParser │ │EpubPathUtils│
     │  脚注解析     │ │  行内元素解析  │ │  路径工具    │
     └──────────────┘ └──────────────┘ └─────────────┘
```

## 模块说明

### EpubParser（主入口）
- **位置**: `../EpubParser.kt`
- **职责**: 协调各组件完成 EPUB 解析
- **主要方法**:
  - `parseStructure(file)`: 解析 EPUB 结构，返回章节列表和资源
  - `parseChapterContent(file, href)`: 解析单个章节内容

---

### EpubPackageReader
- **职责**: 读取 EPUB 包的基本结构
- **解析文件**:
  - `META-INF/container.xml` → 找到 OPF 路径
  - `content.opf` → 解析 manifest 和 spine
- **输出**: `PackageInfo`（OPF 路径、基础目录、spine 列表、TOC 路径）

---

### EpubTocParser
- **职责**: 解析目录文件获取章节信息
- **支持格式**:
  - `nav.xhtml`（EPUB3，优先使用）
  - `toc.ncx`（EPUB2，回退方案）
- **特性**:
  - 优先解析 EPUB3 nav.xhtml
  - 递归解析嵌套结构
  - 防止循环引用
  - 自动检测章节标题

---

### EpubResourceExtractor
- **职责**: 从 EPUB 包中提取资源文件
- **支持格式**: jpg, jpeg, png, gif, svg, webp, bmp
- **输出**: `Map<String, ByteArray>`（相对路径 → 文件内容）

---

### EpubContentParser
- **职责**: 将 HTML 内容解析为结构化的 `ContentElement` 列表
- **支持元素**:
  - 标题: `h1` ~ `h6` → `ContentElement.Heading`
  - 段落: `p` → `ContentElement.Paragraph`
  - 图片: `img` → `ContentElement.Image`
  - 引用: `blockquote` → `ContentElement.Quote`
  - 代码: `pre`, `code` → `ContentElement.Code`
  - 列表: `ul`, `ol` → `ContentElement.ListBlock`
  - 分隔线: `hr` → `ContentElement.Divider`
  - 脚注: `aside[epub:type="footnote"]` → `ContentElement.Footnote`
- **依赖**: `InlineParser`, `FootnoteParser`

---

### InlineParser
- **职责**: 解析行内元素为 `TextSpan` 列表
- **支持样式**:
  - `strong`, `b` → BOLD
  - `em`, `i` → ITALIC
  - `u` → UNDERLINE
  - `s`, `strike`, `del` → STRIKETHROUGH
  - `code` → CODE
  - `a` → 链接
- **特殊处理**: 脚注链接识别

---

### FootnoteParser
- **职责**: 脚注识别与解析
- **识别脚注链接**:
  - `epub:type="noteref"`
  - `class="footnote"` 或 `class="note"`
  - `href="#n*"`, `href="#fn*"`, `href="#note*"`
  - 只包含脚注图片的链接
- **解析脚注定义**:
  - `<aside epub:type="footnote">`
  - `<div class="footnote">`
  - 嵌套的 `<li id="n*">` 结构（如多看格式）

---

### EpubPathUtils
- **职责**: 路径处理工具
- **方法**:
  - `normalizeImagePath(src, chapterHref)`: 正规化图片相对路径
  - `resolveFullPath(baseDir, href)`: 解析完整路径
  - `getRelativePath(fullPath, baseDir)`: 获取相对路径

---

## 数据流

```
EPUB 文件
    │
    ▼
EpubPackageReader ──→ PackageInfo (opfPath, baseDir, spineItems, tocHref)
    │
    ├──→ EpubTocParser ──→ List<ChapterInfo> (章节列表)
    │
    └──→ EpubResourceExtractor ──→ Map<String, ByteArray> (资源文件)

章节 HTML
    │
    ▼
EpubContentParser ──→ List<ContentElement>
    │
    ├──→ InlineParser ──→ List<TextSpan>
    │
    └──→ FootnoteParser ──→ 脚注识别/解析
```

## 扩展指南

### 添加新的内容元素类型
1. 在 `BookContent.kt` 中添加新的 `ContentElement` 子类
2. 在 `EpubContentParser.parseElement()` 中添加对应的解析逻辑

### 添加新的行内样式
1. 在 `BookContent.kt` 的 `TextStyle` 枚举中添加新样式
2. 在 `InlineParser.parseInlineElement()` 中添加对应的标签处理
