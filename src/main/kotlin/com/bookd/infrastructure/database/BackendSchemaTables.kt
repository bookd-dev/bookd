package com.bookd.infrastructure.database

import com.bookd.data.entity.BookDocuments
import com.bookd.data.entity.BookSources
import com.bookd.data.entity.BookTags
import com.bookd.data.entity.Bookmarks
import com.bookd.data.entity.Books
import com.bookd.data.entity.BookshelfItems
import com.bookd.data.entity.Bookshelves
import com.bookd.data.entity.AiModels
import com.bookd.data.entity.AiProviderEndpoints
import com.bookd.data.entity.AiProviders
import com.bookd.data.entity.DocumentContents
import com.bookd.data.entity.DocumentResources
import com.bookd.data.entity.FolderPermissions
import com.bookd.data.entity.InviteTokens
import com.bookd.data.entity.ReaderSettings
import com.bookd.data.entity.ReadingProgress
import com.bookd.data.entity.Sessions
import com.bookd.data.entity.SystemSettings
import com.bookd.data.entity.Tags
import com.bookd.data.entity.TxtParseRules
import com.bookd.data.entity.Users
import org.jetbrains.exposed.v1.core.Table

object BackendSchemaTables {
    val all: Array<Table> = arrayOf(
        Users,
        BookSources,
        Books,
        Tags,
        BookTags,
        ReadingProgress,
        FolderPermissions,
        Sessions,
        InviteTokens,
        Bookmarks,
        ReaderSettings,
        BookDocuments,
        DocumentContents,
        DocumentResources,
        TxtParseRules,
        Bookshelves,
        BookshelfItems,
        SystemSettings,
        AiProviders,
        AiProviderEndpoints,
        AiModels
    )
}
