package com.launcher.folder

import android.content.Context
import android.content.pm.LauncherApps
import com.launcher.data.AppDatabase
import com.launcher.data.AppInfo
import com.launcher.data.Folder
import com.launcher.data.FolderMember
import com.launcher.data.SettingsRepository
import com.launcher.util.serializeUser
import com.launcher.util.userFromToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

data class FolderWithCount(val folder: Folder, val memberCount: Int)

/**
 * Room-backed folders. Members are stored as "package|userToken" keys and
 * resolved against LauncherApps at read time, so uninstalled apps drop out
 * gracefully.
 */
class FolderManager(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    private val db = AppDatabase.getDatabase(context)
    private val folderDao = db.folderDao()

    suspend fun getFolders(): List<Folder> = withContext(Dispatchers.IO) {
        folderDao.getAllFolders().firstOrNull() ?: emptyList()
    }

    suspend fun getFoldersWithCounts(): List<FolderWithCount> = withContext(Dispatchers.IO) {
        getFolders().mapNotNull { folder ->
            val members = getMembers(folder.id)
            if (members.isEmpty()) null else FolderWithCount(folder, members.size)
        }
    }

    suspend fun getFolder(folderId: Int): Folder? = withContext(Dispatchers.IO) {
        folderDao.getFolder(folderId).firstOrNull()
    }

    /** Resolves member rows to launchable apps; prunes entries that no longer resolve. */
    suspend fun getMembers(folderId: Int): List<AppInfo> = withContext(Dispatchers.IO) {
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val rows = folderDao.getFolderMembers(folderId)
        val resolved = mutableListOf<AppInfo>()
        val stale = mutableListOf<FolderMember>()

        rows.forEach { row ->
            val packageName = row.appId.substringBefore("|")
            val userToken = row.appId.substringAfter("|", "personal")
            val user = context.userFromToken(userToken)
            val activity = runCatching {
                launcherApps.getActivityList(packageName, user).firstOrNull()
            }.getOrNull()
            if (activity == null) {
                stale.add(row)
                return@forEach
            }
            val original = activity.label.toString()
            val rename = settings.getRenameLabel(row.appId)
            resolved.add(
                AppInfo(
                    packageName = packageName,
                    activityClassName = activity.componentName.className,
                    label = rename.ifBlank { original },
                    originalLabel = original,
                    userToken = serializeUser(user),
                    isSystem = false,
                    installedAt = activity.firstInstallTime,
                    sizeBytes = 0L,
                )
            )
        }

        stale.forEach { folderDao.deleteMember(it) }
        resolved.sortedBy { it.label.lowercase() }
    }

    suspend fun createFolder(name: String): Result<Folder> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Folder name cannot be blank"))
        }
        if (folderDao.countByName(trimmed) > 0) {
            return@withContext Result.failure(IllegalArgumentException("Folder already exists"))
        }
        val folder = Folder(name = trimmed, sortOrder = folderDao.count())
        val id = folderDao.insert(folder).toInt()
        Result.success(folder.copy(id = id))
    }

    suspend fun renameFolder(folderId: Int, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trimmed = newName.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Folder name cannot be blank"))
            }
            val folder = folderDao.getFolder(folderId).firstOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Folder not found"))
            if (!folder.name.equals(trimmed, ignoreCase = true) && folderDao.countByName(trimmed) > 0) {
                return@withContext Result.failure(IllegalArgumentException("Folder already exists"))
            }
            folderDao.update(folder.copy(name = trimmed))
            Result.success(Unit)
        }

    suspend fun deleteFolder(folderId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolder(folderId).firstOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Folder not found"))
        folderDao.getFolderMembers(folderId).forEach { folderDao.deleteMember(it) }
        folderDao.delete(folder)
        settings.clearSlotsForFolder(folderId)
        Result.success(Unit)
    }

    suspend fun addMember(folderId: Int, app: AppInfo): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = folderDao.getFolderMembers(folderId)
        if (existing.any { it.appId == app.key }) {
            return@withContext Result.failure(IllegalArgumentException("App already in folder"))
        }
        folderDao.insertMember(FolderMember(folderId = folderId, appId = app.key))
        Result.success(Unit)
    }

    suspend fun removeMember(folderId: Int, app: AppInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            folderDao.removeMember(folderId, app.key)
            // A folder that loses its last member disappears; slots clear too.
            if (folderDao.getFolderMembers(folderId).isEmpty()) {
                deleteFolder(folderId)
            }
            Result.success(Unit)
        }

    /** All member keys ("package|userToken") across every folder. */
    suspend fun getAllMemberKeys(): Set<String> = withContext(Dispatchers.IO) {
        db.folderDao().getAllMembers().mapTo(mutableSetOf()) { it.appId }
    }

    suspend fun moveFolder(folderId: Int, up: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val folders = (folderDao.getAllFolders().firstOrNull() ?: emptyList()).toMutableList()
        val index = folders.indexOfFirst { it.id == folderId }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target < 0 || target >= folders.size) {
            return@withContext Result.failure(IllegalStateException("Cannot move"))
        }
        val tmp = folders[index]
        folders[index] = folders[target]
        folders[target] = tmp
        folders.forEachIndexed { i, folder -> folderDao.update(folder.copy(sortOrder = i)) }
        Result.success(Unit)
    }
}
