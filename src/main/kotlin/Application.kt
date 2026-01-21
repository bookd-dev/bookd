package com.bookd

import com.bookd.config.DatabaseConfig
import com.bookd.domain.service.BackgroundParseService
import com.bookd.domain.service.TxtParseRuleService
import com.bookd.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.io.File

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // Initialize database
    val dbUrl = environment.config.propertyOrNull("database.url")?.getString() 
        ?: "jdbc:postgresql://localhost:5432/bookd"
    val dbDriver = environment.config.propertyOrNull("database.driver")?.getString() 
        ?: "org.postgresql.Driver"
    val dbUser = environment.config.propertyOrNull("database.user")?.getString() 
        ?: "bookd"
    val dbPassword = environment.config.propertyOrNull("database.password")?.getString() 
        ?: "bookd"
    
    DatabaseConfig.init(dbUrl, dbDriver, dbUser, dbPassword)
    
    // Create tables and add missing columns
    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            com.bookd.data.entity.Users,
            com.bookd.data.entity.Books,
            com.bookd.data.entity.BookSources,
            com.bookd.data.entity.Tags,
            com.bookd.data.entity.BookTags,
            com.bookd.data.entity.ReadingProgress,
            com.bookd.data.entity.FolderPermissions,
            com.bookd.data.entity.Sessions,
            com.bookd.data.entity.InviteTokens,
            com.bookd.data.entity.Bookmarks,
            com.bookd.data.entity.ReaderSettings,
            com.bookd.data.entity.BookDocuments,
            com.bookd.data.entity.DocumentContents,
            com.bookd.data.entity.DocumentResources,
            com.bookd.data.entity.TxtParseRules,
            com.bookd.data.entity.Bookshelves,
            com.bookd.data.entity.BookshelfItems
        )
    }
    
    // Note: First-time setup will be handled via /setup page
    
    // Configure plugins
    configureDependencyInjection()
    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    configureRouting()
    
    // Initialize TXT parse rules from JSON file if database is empty
    val txtParseRuleService by inject<TxtParseRuleService>()
    runBlocking {
        txtParseRuleService.initializeFromJson(DEFAULT_TXT_TOC_RULES)
    }
    
    // Start background parse service
    val backgroundParseService by inject<BackgroundParseService>()
    backgroundParseService.start()
}

private val DEFAULT_TXT_TOC_RULES = """
    [
      {
        "name": "常规模式",
        "rule": "^[ 　\\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\\s{0,4}[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|篇(?!张))).{0,30}$",
        "example": "第一章 我还活着"
      },
      {
        "name": "常规模式 匹配扉页标题",
        "rule": "(?<=[　\\s])(?:(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\\s{0,4}[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|回(?![合来事去])|场(?![和合比电是])|篇(?!张))).{0,30}$",
        "example": "简介 老夫诸葛村夫"
      },
      {
        "name": "古典、轻小说",
        "rule": "^[ 　\\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\\s{0,4}[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|回(?![合来事去])|场(?![和合比电是])|话|篇(?!张))).{0,30}$",
        "example": "第一回 读一本书\n第一话 读一本书"
      },
      {
        "name": "纯数字标题",
        "rule": "(?<=[　\\s])\\d+\\.?[ 　\\t]{0,4}$",
        "example": "12"
      },
      {
        "name": "中文纯数字标题",
        "rule": "(?<=[　\\s])[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,12}[ 　\\t]{0,4}$",
        "example": "一百七十"
      },
      {
        "name": "混合纯数字标题",
        "rule": "(?<=[　\\s])[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟\\d]{1,12}[ 　\\t]{0,4}$",
        "example": "12\n一百七十"
      },
      {
        "name": "数字 分隔符 标题",
        "rule": "^[ 　\\t]{0,4}\\d{1,5}[:：,.， 、_—\\-].{1,30}$",
        "example": "1、这个就是标题"
      },
      {
        "name": "大写数字 分隔符 标题名称",
        "rule": "^[ 　\\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章?)[ 、_—\\-].{1,30}$",
        "example": "一、只有前面的数字有差别\n二十四章 我瞎编的标题"
      },
      {
        "name": "数字混合 分隔符 标题名称",
        "rule": "^[ 　\\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章?[ 、_—\\-]|\\d{1,5}章?[:：,.， 、_—\\-]).{0,30}$",
        "example": "1、人参公鸡\n二百二十章 boy next door"
      },
      {
        "name": "正文 标题/序号",
        "rule": "^[ 　\\t]{0,4}正文[ 　]{1,4}.{0,20}$",
        "example": "正文 我乃常山赵子龙"
      },
      {
        "name": "Chapter/Section/Part/Episode 序号 标题",
        "rule": "^[ 　\\t]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|ＰＡＲＴ|[Nn][oO][.、]|[Ee]pisode|(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外)\\s{0,4}\\d{1,4}.{0,30}$",
        "example": "Chapter 1 MyGrandmaIsNB"
      },
      {
        "name": "特殊符号 序号 标题",
        "rule": "(?<=[\\s　])[【〔〖「『〈［\\[](?:第|[Cc]hapter)[\\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,10}[章节].{0,20}$",
        "example": "【第一章 后面的符号可以没有"
      },
      {
        "name": "特殊符号 标题(成对)",
        "rule": "(?<=[\\s　]{0,4})(?:[\\[〈「『〖〔《（【\\(].{1,30}[\\)】）》〕〗』」〉\\]]?|(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外)[ 　]{0,4}$",
        "example": "『读一本书』\n(11)读一本书"
      },
      {
        "name": "特殊符号 标题(单个)",
        "rule": "(?<=[\\s　]{0,4})(?:[☆★✦✧].{1,30}|(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外)[ 　]{0,4}$",
        "example": "☆、读一本书"
      },
      {
        "name": "章/卷 序号 标题",
        "rule": "^[ \\t　]{0,4}(?:(?:内容|文章)?简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|[卷章][\\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8})[ 　]{0,4}.{0,30}$",
        "example": "卷五 读一本书"
      },
      {
        "name": "书名 括号 序号",
        "rule": "^[一-龥]{1,20}[ 　\\t]{0,4}[(（][\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[)）][ 　\\t]{0,4}$",
        "example": "标题后面数字有括号(12)"
      },
      {
        "name": "书名 序号",
        "rule": "^[一-龥]{1,20}[ 　\\t]{0,4}[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[ 　\\t]{0,4}$",
        "example": "标题后面数字没有括号124"
      },
      {
        "name": "特定字符 标题 特定符号",
        "rule": "(?<=\\={3,6}).{1,40}?(?=\\=)",
        "example": "===读一本书==="
      }
    ]
""".trimIndent()