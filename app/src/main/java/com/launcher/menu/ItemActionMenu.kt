package com.launcher.menu

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.launcher.R
import com.launcher.data.AppInfo
import com.launcher.data.AppRepository
import com.launcher.data.SettingsRepository
import com.launcher.folder.FolderManager
import com.launcher.notification.AppMuteListenerService
import com.launcher.theme.ThemeManager
import com.launcher.theme.applyLauncherFont
import com.launcher.util.openAppInfo
import com.launcher.util.requestUninstall
import com.launcher.util.showToast
import com.launcher.util.userFromToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Long-press action menu for app rows (drawer, folder members) and folders.
 * All state changes persist via the shared repositories; [onChanged] lets the
 * caller re-render.
 */
class ItemActionMenu(
    private val context: Context,
    private val appRepo: AppRepository,
    private val settings: SettingsRepository,
    private val themeManager: ThemeManager,
    private val folders: FolderManager? = null,
) {

    private val scope: CoroutineScope = MainScope()

    fun showAppMenu(
        app: AppInfo,
        isDrawerRow: Boolean = false,
        folderId: Int = -1,
        onChanged: () -> Unit = {},
    ) {
        val items = mutableListOf<Pair<String, () -> Unit>>()

        items.add(context.getString(R.string.action_app_info) to {
            context.openAppInfo(app.packageName, context.userFromToken(app.userToken))
        })

        items.add(context.getString(R.string.action_change_label) to {
            showRenameDialog(app.label) { newName ->
                // Blank resets to the real label.
                settings.setRenameLabel(app.key, newName)
                appRepo.refresh()
                onChanged()
            }
        })

        val hidden = appRepo.isHidden(app)
        items.add(
            (if (hidden) context.getString(R.string.action_show)
            else context.getString(R.string.action_hide)) to {
                appRepo.toggleHidden(app)
                context.showToast(
                    context.getString(if (hidden) R.string.toast_shown else R.string.toast_hidden)
                )
                onChanged()
            }
        )

        items.add(context.getString(R.string.action_disable_for) to {
            showDisableForDialog(app)
        })

        if (isDrawerRow) {
            val pinned = appRepo.isPinned(app)
            items.add(
                (if (pinned) context.getString(R.string.action_unpin)
                else context.getString(R.string.action_pin)) to {
                    appRepo.togglePinned(app)
                    onChanged()
                }
            )
            if (pinned) {
                items.add(context.getString(R.string.action_move_up) to {
                    settings.movePinned(app.key, up = true); onChanged()
                })
                items.add(context.getString(R.string.action_move_down) to {
                    settings.movePinned(app.key, up = false); onChanged()
                })
            }
            if (folders != null) {
                items.add(context.getString(R.string.action_add_to_folder) to {
                    showAddToFolderDialog(app, onChanged)
                })
            }
        }

        if (folderId >= 0 && folders != null) {
            items.add(context.getString(R.string.action_remove_from_folder) to {
                scope.launch {
                    folders.removeMember(folderId, app)
                    onChanged()
                }
            })
        }

        if (app.isShortcut) {
            items.add(context.getString(R.string.action_delete_shortcut) to {
                appRepo.deletePinnedShortcut(app)
                onChanged()
            })
        } else {
            items.add(context.getString(R.string.action_uninstall) to {
                if (app.isSystem) {
                    context.showToast(context.getString(R.string.toast_uninstall_failed))
                    context.openAppInfo(app.packageName, context.userFromToken(app.userToken))
                } else {
                    context.requestUninstall(app.packageName)
                }
            })
        }

        showThemedList(app.label, items)
    }

    fun showFolderMenu(
        folderId: Int,
        folderName: String,
        onChanged: () -> Unit = {},
    ) {
        val folders = folders ?: return
        val items = listOf<Pair<String, () -> Unit>>(
            context.getString(R.string.action_rename) to {
                showRenameDialog(folderName) { newName ->
                    scope.launch {
                        folders.renameFolder(folderId, newName)
                            .onFailure { context.showToast(context.getString(R.string.toast_invalid_folder_name)) }
                        onChanged()
                    }
                }
            },
            context.getString(R.string.action_move_up) to {
                scope.launch { folders.moveFolder(folderId, up = true); onChanged() }
            },
            context.getString(R.string.action_move_down) to {
                scope.launch { folders.moveFolder(folderId, up = false); onChanged() }
            },
            context.getString(R.string.action_delete) to {
                AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.confirm_delete_folder, folderName))
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        scope.launch { folders.deleteFolder(folderId); onChanged() }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
        )
        showThemedList(folderName, items)
    }

    fun showCreateFolderDialog(app: AppInfo, onChanged: () -> Unit) {
        val folders = folders ?: return
        showRenameDialog("") { name ->
            scope.launch {
                folders.createFolder(name).fold(
                    onSuccess = { folder ->
                        folders.addMember(folder.id, app)
                        onChanged()
                    },
                    onFailure = {
                        context.showToast(context.getString(R.string.toast_invalid_folder_name))
                    },
                )
            }
        }
    }

    private fun showAddToFolderDialog(app: AppInfo, onChanged: () -> Unit) {
        val folders = folders ?: return
        scope.launch {
            val existing = folders.getFolders()
            val names = existing.map { it.name } + context.getString(R.string.action_create_folder)
            AlertDialog.Builder(context)
                .setTitle(R.string.action_add_to_folder)
                .setItems(names.toTypedArray()) { _, which ->
                    if (which < existing.size) {
                        scope.launch {
                            folders.addMember(existing[which].id, app)
                            onChanged()
                        }
                    } else {
                        showCreateFolderDialog(app, onChanged)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showThemedList(title: String, items: List<Pair<String, () -> Unit>>) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(items.map { it.first }.toTypedArray()) { _, which ->
                items[which].second()
            }
            .show()
            .applyLauncherFont(settings.fontFamily)
    }

    private fun showRenameDialog(currentLabel: String, onSave: (String) -> Unit) {
        val input = EditText(context).apply {
            setText(currentLabel)
            setSelection(0, text.length)
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.action_change_label)
            .setView(input)
            .setPositiveButton(R.string.action_rename) { _, _ ->
                onSave(input.text.toString().trim())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        // Commit via keyboard Done too, not just the button.
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onSave(input.text.toString().trim())
                dialog.dismiss()
                true
            } else {
                false
            }
        }

        dialog.show()
        dialog.applyLauncherFont(settings.fontFamily)
        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showDisableForDialog(app: AppInfo) {
        // Muting works through the notification listener; route to the system
        // access screen on first use.
        if (!AppMuteListenerService.isConnected) {
            context.showToast(context.getString(R.string.notification_access_needed))
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            return
        }

        val hours = listOf(1, 2, 4, 8)
        val labels = hours.map { context.getString(R.string.disable_for_hours, it) }
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.action_disable_for))
            .setItems(labels.toTypedArray()) { _, which ->
                val until = System.currentTimeMillis() + hours[which] * 60 * 60 * 1000L
                settings.setMuteUntil(app.packageName, until)
                AppMuteListenerService.instance?.cancelAllFrom(app.packageName)
                val time = DateFormat.getTimeFormat(context).format(Date(until))
                context.showToast(context.getString(R.string.toast_disabled_until, time))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
