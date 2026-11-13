package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun findById(userId: Int): UserEntity?

    @Query("SELECT * FROM users WHERE remoteAuthId = :remoteAuthId LIMIT 1")
    suspend fun findByRemoteAuthId(remoteAuthId: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE role = 'WORKSHOP'")
    suspend fun countWorkshopAccounts(): Int

    @Query("SELECT COUNT(*) FROM users WHERE username = :username COLLATE NOCASE")
    suspend fun usernameExists(username: String): Int

    @Query("SELECT * FROM users WHERE role = 'CLIENT' ORDER BY displayName COLLATE NOCASE")
    fun observeClients(): Flow<List<UserEntity>>
}
