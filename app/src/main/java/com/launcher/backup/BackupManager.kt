package com.launcher.backup

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.launcher.data.SettingsRepository
import com.launcher.data.SlotEntry
import com.launcher.folder.FolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BACKUP_VERSION = 1

data class BackupData(
    val version: Int = BACKUP_VERSION,
    val timestamp: Long = 0L,
    val slotCount: Int = 4,
    val textAlignment: String = "center",
    val dateTimeMode: String = "date_time",
    val statusBarVisible: Boolean = false,
    val widgetsEnabled: List<String> = emptyList(),
    val widgetTapActions: Map<String, String> = emptyMap(),
    val weatherTempUnit: String = "fahrenheit",
    val autoShowKeyboard: Boolean = false,
    val sortMode: String = "default",
    val hideHomeAppsFromDrawer: Boolean = false,
    val hideFolderMembers: Boolean = false,
    val swipeLeftApp: String? = null,
    val swipeRightApp: String? = null,
    val swipeLeftEnabled: Boolean = true,
    val swipeRightEnabled: Boolean = true,
    val swipeDownAction: String = "notifications",
    val doubleTapLock: Boolean = false,
    val homeToRecents: Boolean = false,
    val themePreset: String = "amoled",
    val customBgColor: String = "",
    val appearanceMode: String = "dark",
    val fontFamily: String = "jetbrains_mono_nerd",
    val textSizeScale: Float = 1f,
    val hiddenApps: List<String> = emptyList(),
    val pinnedApps: List<String> = emptyList(),
    val renameLabels: Map<String, String> = emptyMap(),
    val slots: List<BackupSlot> = emptyList(),
    val folders: List<BackupFolder> = emptyList(),
)

data class BackupSlot(
    val index: Int,
    val label: String,
    val packageName: String,
    val activityClassName: String,
    val userToken: String,
    /** Folder *name* — folder ids are not stable across restores. */
    val folderName: String?,
)

data class BackupFolder(
    val name: String,
    val members: List<String>,
)

/**
 * Versioned JSON export/import of every user-configurable thing. Import
 * validates the full payload before writing anything.
 */
class BackupManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val folders: FolderManager,
) {

    private val gson = Gson()

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val folderEntities = folders.getFolders()
        val backupFolders = folderEntities.map { folder ->
            BackupFolder(
                name = folder.name,
                members = folders.getMembers(folder.id).map { it.key },
            )
        }
        val folderNamesById = folderEntities.associate { it.id to it.name }

        val slots = (1..SettingsRepository.MAX_SLOTS).mapNotNull { index ->
            val entry = settings.getSlot(index)
            if (entry.isEmpty) return@mapNotNull null
            BackupSlot(
                index = index,
                label = entry.label,
                packageName = entry.packageName,
                activityClassName = entry.activityClassName,
                userToken = entry.userToken,
                folderName = folderNamesById[entry.folderId],
            )
        }

        gson.toJson(
            BackupData(
                version = BACKUP_VERSION,
                timestamp = System.currentTimeMillis(),
                slotCount = settings.slotCount,
                textAlignment = settings.textAlignment,
                dateTimeMode = settings.dateTimeMode,
                statusBarVisible = settings.statusBarVisible,
                widgetsEnabled = settings.widgetsOrder,
                widgetTapActions = SettingsRepository.ALL_WIDGETS.associateWith {
                    settings.getWidgetTapAction(it)
                },
                weatherTempUnit = settings.weatherTempUnit,
                autoShowKeyboard = settings.autoShowKeyboard,
                sortMode = settings.sortMode,
                hideHomeAppsFromDrawer = settings.hideHomeAppsFromDrawer,
                hideFolderMembers = settings.hideFolderMembers,
                swipeLeftApp = settings.swipeLeftApp,
                swipeRightApp = settings.swipeRightApp,
                swipeLeftEnabled = settings.swipeLeftEnabled,
                swipeRightEnabled = settings.swipeRightEnabled,
                swipeDownAction = settings.swipeDownAction,
                doubleTapLock = settings.doubleTapLock,
                homeToRecents = settings.homeToRecents,
                themePreset = settings.themePreset,
                customBgColor = settings.customBgColor.toUInt().toString(16),
                appearanceMode = settings.appearanceMode,
                fontFamily = settings.fontFamily,
                textSizeScale = settings.textSizeScale,
                hiddenApps = settings.hiddenApps.toList(),
                pinnedApps = settings.pinnedApps,
                renameLabels = settings.getRenameLabels(),
                slots = slots,
                folders = backupFolders,
            )
        )
    }

    suspend fun importFromJson(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        val data = try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: JsonSyntaxException) {
            return@withContext Result.failure(IllegalArgumentException("Not a valid backup file"))
        } ?: return@withContext Result.failure(IllegalArgumentException("Empty backup file"))

        // Validate everything before writing anything.
        if (data.version != BACKUP_VERSION) {
            return@withContext Result.failure(
                IllegalArgumentException("Unsupported backup version ${data.version}")
            )
        }
        if (data.slotCount !in 0..SettingsRepository.MAX_SLOTS ||
            data.slots.any { it.index !in 1..SettingsRepository.MAX_SLOTS } ||
            data.textSizeScale !in 0.25f..4f
        ) {
            return@withContext Result.failure(IllegalArgumentException("Backup failed validation"))
        }

        // Settings
        settings.slotCount = data.slotCount
        settings.textAlignment = data.textAlignment
        settings.dateTimeMode = data.dateTimeMode
        settings.statusBarVisible = data.statusBarVisible
        settings.widgetsOrder = data.widgetsEnabled
        data.widgetTapActions.forEach { (widget, action) ->
            settings.setWidgetTapAction(widget, action)
        }
        settings.weatherTempUnit = data.weatherTempUnit
        settings.autoShowKeyboard = data.autoShowKeyboard
        settings.sortMode = data.sortMode
        settings.hideHomeAppsFromDrawer = data.hideHomeAppsFromDrawer
        settings.hideFolderMembers = data.hideFolderMembers
        settings.swipeLeftApp = data.swipeLeftApp
        settings.swipeRightApp = data.swipeRightApp
        settings.swipeLeftEnabled = data.swipeLeftEnabled
        settings.swipeRightEnabled = data.swipeRightEnabled
        settings.swipeDownAction = data.swipeDownAction
        settings.doubleTapLock = data.doubleTapLock
        settings.homeToRecents = data.homeToRecents
        settings.themePreset = data.themePreset
        data.customBgColor.toLongOrNull(16)?.let { settings.customBgColor = it.toInt() }
        settings.appearanceMode = data.appearanceMode
        settings.fontFamily = data.fontFamily
        settings.textSizeScale = data.textSizeScale
        settings.hiddenApps = data.hiddenApps.toSet()
        settings.pinnedApps = data.pinnedApps
        data.renameLabels.forEach { (key, label) -> settings.setRenameLabel(key, label) }
        settings.firstRunSeeded = true

        // Folders: recreate by name, then map slots onto the new ids.
        val nameToId = mutableMapOf<String, Int>()
        for (folder in data.folders) {
            val created = folders.createFolder(folder.name).getOrNull()
                ?: folders.getFolders().firstOrNull { it.name.equals(folder.name, true) }
                ?: continue
            nameToId[folder.name] = created.id
            folder.members.forEach { memberKey ->
                val packageName = memberKey.substringBefore("|")
                val userToken = memberKey.substringAfter("|", "personal")
                folders.addMember(
                    created.id,
                    com.launcher.data.AppInfo(
                        packageName = packageName,
                        activityClassName = null,
                        label = packageName,
                        originalLabel = packageName,
                        userToken = userToken,
                        isSystem = false,
                        installedAt = 0L,
                        sizeBytes = 0L,
                    ),
                )
            }
        }

        for (slot in 1..SettingsRepository.MAX_SLOTS) settings.clearSlot(slot)
        data.slots.forEach { slot ->
            settings.setSlot(
                slot.index,
                SlotEntry(
                    label = slot.label,
                    packageName = slot.packageName,
                    activityClassName = slot.activityClassName,
                    userToken = slot.userToken,
                    folderId = slot.folderName?.let { nameToId[it] } ?: -1,
                ),
            )
        }

        Result.success(Unit)
    }
}
