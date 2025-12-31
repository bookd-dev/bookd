# 📖 电子书内容获取 API 设计

> 适用于 Compose Multiplatform + Kotlin Multiplatform 跨平台方案

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                    KMP 跨平台架构                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Compose UI Layer                         │   │
│  │  ┌─────────┐   ┌─────────┐   ┌─────────┐                   │   │
│  │  │ Desktop │   │   iOS   │   │ Android │                   │   │
│  │  │ Compose │   │ Compose │   │ Compose │                   │   │
│  │  └────┬────┘   └────┬────┘   └────┬────┘                   │   │
│  │       └─────────────┼─────────────┘                         │   │
│  │                     ▼                                       │   │
│  │       ┌─────────────────────────────┐                      │   │
│  │       │   Shared Compose Reader     │                      │   │
│  │       │   - RichText 渲染            │                      │   │
│  │       │   - 图片异步加载             │                      │   │
│  │       │   - 翻页/滚动               │                      │   │
│  │       └─────────────────────────────┘                      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                          │                                         │
│                          ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                 KMP Shared Module                           │   │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐   │   │
│  │  │ BookRepository│  │ ContentParser │  │ ProgressSync  │   │   │
│  │  └───────────────┘  └───────────────┘  └───────────────┘   │   │
│  │           │                                                 │   │
│  │           ▼                                                 │   │
│  │  ┌─────────────────────────────────────────────────────┐   │   │
│  │  │              Ktor Client (共享)                      │   │   │
│  │  └─────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                          │                                         │
│                          ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Bookd Server API                         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## API 端点

| Method | Path | 说明 | 响应类型 |
|--------|------|------|----------|
| GET | `/api/books/{id}/manifest` | 书籍结构信息 | `BookManifest` |
| GET | `/api/books/{id}/chapters/{index}` | 章节内容（结构化富文本） | `ChapterContent` |
| GET | `/api/books/{id}/resources/{path}` | 资源流（图片/字体等） | Binary Stream |
| GET | `/api/books/{id}/search?q=xxx` | 全文搜索 | `SearchResults` |
| GET | `/api/books/{id}/text?page=1&pageSize=3000` | TXT 分页内容 | `TextPage` |

---

## 数据模型

### BookManifest - 书籍清单

```kotlin
@Serializable
data class BookManifest(
    val id: Int,
    val title: String,
    val author: String?,
    val format: String,           // epub, pdf, txt, mobi
    val totalChapters: Int,
    val toc: List<TocItem>,       // 目录
    val spine: List<Int>,         // 阅读顺序
    val metadata: BookMetadata?
)

@Serializable
data class TocItem(
    val index: Int,
    val title: String,
    val level: Int = 0,
    val children: List<TocItem> = emptyList()
)

@Serializable
data class BookMetadata(
    val publisher: String? = null,
    val language: String? = null,
    val publishDate: String? = null,
    val description: String? = null
)
```

### ChapterContent - 章节内容

```kotlin
@Serializable
data class ChapterContent(
    val index: Int,
    val title: String?,
    val elements: List<ContentElement>,  // 结构化内容元素
    val prevIndex: Int?,
    val nextIndex: Int?
)
```

### ContentElement - 内容元素（sealed class）

```kotlin
@Serializable
sealed class ContentElement {
    
    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        val spans: List<TextSpan>
    ) : ContentElement()
    
    @Serializable
    @SerialName("heading")
    data class Heading(
        val level: Int,              // 1-6
        val text: String
    ) : ContentElement()
    
    @Serializable
    @SerialName("image")
    data class Image(
        val src: String,             // 资源路径，客户端拼接完整 URL
        val alt: String? = null,
        val width: Int? = null,
        val height: Int? = null
    ) : ContentElement()
    
    @Serializable
    @SerialName("quote")
    data class Quote(
        val spans: List<TextSpan>
    ) : ContentElement()
    
    @Serializable
    @SerialName("code")
    data class Code(
        val text: String,
        val language: String? = null
    ) : ContentElement()
    
    @Serializable
    @SerialName("list")
    data class ListBlock(
        val ordered: Boolean,
        val items: List<ListItem>
    ) : ContentElement()
    
    @Serializable
    @SerialName("divider")
    object Divider : ContentElement()
}
```

### TextSpan - 文本片段

```kotlin
@Serializable
data class TextSpan(
    val text: String,
    val styles: List<TextStyle> = emptyList(),
    val link: String? = null       // 如果是链接，存储 href
)

@Serializable
enum class TextStyle {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    CODE
}

@Serializable
data class ListItem(
    val spans: List<TextSpan>
)
```

### SearchResults - 搜索结果

```kotlin
@Serializable
data class SearchResults(
    val query: String,
    val total: Int,
    val results: List<SearchHit>
)

@Serializable
data class SearchHit(
    val chapterIndex: Int,
    val chapterTitle: String?,
    val snippet: String,           // 带上下文的片段
    val elementIndex: Int          // 定位到具体元素
)
```

### TextPage - TXT 分页

```kotlin
@Serializable
data class TextPage(
    val content: String,
    val page: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrev: Boolean
)
```

---

## 响应示例

### GET /api/books/1/manifest

```json
{
  "id": 1,
  "title": "三体",
  "author": "刘慈欣",
  "format": "epub",
  "totalChapters": 25,
  "toc": [
    { "index": 0, "title": "序章", "level": 0, "children": [] },
    { "index": 1, "title": "第一部 科学边界", "level": 0, "children": [
      { "index": 2, "title": "第一章 疯狂年代", "level": 1, "children": [] },
      { "index": 3, "title": "第二章 寂静的春天", "level": 1, "children": [] }
    ]}
  ],
  "spine": [0, 1, 2, 3, 4, 5],
  "metadata": {
    "publisher": "重庆出版社",
    "language": "zh",
    "publishDate": "2008-01-01",
    "description": "地球往事三部曲之一"
  }
}
```

### GET /api/books/1/chapters/3

```json
{
  "index": 3,
  "title": "第一章 初遇",
  "elements": [
    {
      "type": "heading",
      "level": 1,
      "text": "第一章 初遇"
    },
    {
      "type": "paragraph",
      "spans": [
        { "text": "那是一个" },
        { "text": "寒冷", "styles": ["BOLD"] },
        { "text": "的冬日清晨，雪花纷纷扬扬地落下..." }
      ]
    },
    {
      "type": "image",
      "src": "images/chapter1/scene.jpg",
      "alt": "雪景",
      "width": 800,
      "height": 600
    },
    {
      "type": "quote",
      "spans": [
        { "text": "人生若只如初见，何事秋风悲画扇。", "styles": ["ITALIC"] }
      ]
    },
    {
      "type": "list",
      "ordered": true,
      "items": [
        { "spans": [{ "text": "第一点内容" }] },
        { "spans": [{ "text": "第二点内容" }] }
      ]
    },
    {
      "type": "divider"
    },
    {
      "type": "paragraph",
      "spans": [
        { "text": "她站在窗前，望着远方..." }
      ]
    }
  ],
  "prevIndex": 2,
  "nextIndex": 4
}
```

### GET /api/books/1/search?q=科学

```json
{
  "query": "科学",
  "total": 15,
  "results": [
    {
      "chapterIndex": 2,
      "chapterTitle": "科学边界",
      "snippet": "...他开始意识到科学的边界并不是那么清晰...",
      "elementIndex": 5
    },
    {
      "chapterIndex": 8,
      "chapterTitle": "红岸基地",
      "snippet": "...这是一项绝密的科学实验...",
      "elementIndex": 12
    }
  ]
}
```

---

## 格式解析策略

| 格式 | 解析方式 | 章节划分 | 输出 |
|------|---------|---------|------|
| **EPUB** | 解析 XHTML → ContentElement[] | 按 spine 顺序 | 结构化富文本 |
| **PDF** | PDFBox 提取文本/图片 | 按页或书签 | 结构化富文本 |
| **TXT** | 按段落/章节正则分割 | 智能分章 | Paragraph[] |
| **MOBI/AZW3** | 转 EPUB 后解析 | 同 EPUB | 结构化富文本 |

---

## Compose 渲染示例

```kotlin
// KMP Shared Compose UI

@Composable
fun ChapterReader(
    content: ChapterContent,
    onImageLoad: (String) -> String  // 图片 URL 构建
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(content.elements) { element ->
            when (element) {
                is ContentElement.Heading -> HeadingText(element)
                is ContentElement.Paragraph -> ParagraphText(element)
                is ContentElement.Image -> BookImage(element, onImageLoad)
                is ContentElement.Quote -> QuoteBlock(element)
                is ContentElement.Code -> CodeBlock(element)
                is ContentElement.ListBlock -> ListContent(element)
                is ContentElement.Divider -> HorizontalDivider()
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ParagraphText(paragraph: ContentElement.Paragraph) {
    Text(
        text = buildAnnotatedString {
            paragraph.spans.forEach { span ->
                withStyle(span.toSpanStyle()) {
                    append(span.text)
                }
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 1.8.em
    )
}

@Composable
fun HeadingText(heading: ContentElement.Heading) {
    val style = when (heading.level) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
    }
    Text(
        text = heading.text,
        style = style,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun QuoteBlock(quote: ContentElement.Quote) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = buildAnnotatedString {
                    quote.spans.forEach { span ->
                        withStyle(span.toSpanStyle()) {
                            append(span.text)
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

// TextSpan 扩展函数
fun TextSpan.toSpanStyle(): SpanStyle {
    var style = SpanStyle()
    styles.forEach { textStyle ->
        style = when (textStyle) {
            TextStyle.BOLD -> style.copy(fontWeight = FontWeight.Bold)
            TextStyle.ITALIC -> style.copy(fontStyle = FontStyle.Italic)
            TextStyle.UNDERLINE -> style.copy(textDecoration = TextDecoration.Underline)
            TextStyle.STRIKETHROUGH -> style.copy(textDecoration = TextDecoration.LineThrough)
            TextStyle.CODE -> style.copy(
                fontFamily = FontFamily.Monospace,
                background = Color.LightGray.copy(alpha = 0.3f)
            )
        }
    }
    if (link != null) {
        style = style.copy(
            color = Color.Blue,
            textDecoration = TextDecoration.Underline
        )
    }
    return style
}
```

---

## 实现优先级

### Phase 1 - 核心功能
- [ ] `GET /api/books/{id}/manifest` - EPUB 解析
- [ ] `GET /api/books/{id}/chapters/{index}` - EPUB 章节内容
- [ ] `GET /api/books/{id}/resources/{path}` - 资源流

### Phase 2 - 扩展格式
- [ ] TXT 分页支持
- [ ] PDF 解析（使用 PDFBox）
- [ ] MOBI/AZW3 转换

### Phase 3 - 增强功能
- [ ] 全文搜索
- [ ] 内容缓存
- [ ] 预加载策略

---

## 设计优势

1. **KMP 友好** - 数据模型可直接在 KMP 共享模块中使用
2. **Compose 原生渲染** - 无 WebView 依赖，性能更好
3. **流式加载** - 按章节请求，节省带宽
4. **类型安全** - sealed class 保证穷尽匹配
5. **跨平台一致** - Web/iOS/Android 使用相同 API 和数据结构

---

*文档版本: 1.0 | 更新时间: 2024-12-30*
