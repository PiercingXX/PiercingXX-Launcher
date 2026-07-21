package com.launcher.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "home_slots")
data class HomeSlot(
    @PrimaryKey val slotIndex: Int,
    val appId: String,
    val label: String,
    val isFolder: Boolean,
    val folderId: Int?
)

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sortOrder: Int
)

// Member table for folder contents
@Entity(tableName = "folder_members", primaryKeys = ["folderId", "appId"])
data class FolderMember(
    val folderId: Int,
    val appId: String
)

@Dao
interface HomeSlotDao {
    @Query("SELECT * FROM home_slots ORDER BY slotIndex ASC")
    fun getAllSlots(): Flow<List<HomeSlot>>

    @Query("SELECT * FROM home_slots WHERE slotIndex = :index LIMIT 1")
    fun getSlot(index: Int): Flow<HomeSlot?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(slot: HomeSlot)

    @Update
    suspend fun update(slot: HomeSlot)

    @Delete
    suspend fun delete(slot: HomeSlot)

    @Query("DELETE FROM home_slots WHERE slotIndex = :index")
    suspend fun clearSlot(index: Int)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    fun getFolder(id: Int): Flow<Folder?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun clearFolder(id: Int)

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun count(): Int
    
    @Query("SELECT COUNT(*) FROM folders WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int

    // Folder member operations
    @Query("SELECT * FROM folder_members WHERE folderId = :folderId")
    suspend fun getFolderMembers(folderId: Int): List<FolderMember>

    @Query("SELECT * FROM folder_members")
    suspend fun getAllMembers(): List<FolderMember>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: FolderMember)
    
    @Delete
    suspend fun deleteMember(member: FolderMember)
    
    @Query("DELETE FROM folder_members WHERE folderId = :folderId AND appId = :appId")
    suspend fun removeMember(folderId: Int, appId: String)
}

@Database(
    entities = [HomeSlot::class, Folder::class, FolderMember::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun homeSlotDao(): HomeSlotDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "launcher.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
