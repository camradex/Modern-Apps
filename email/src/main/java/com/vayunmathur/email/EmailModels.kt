package com.vayunmathur.email

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(primaryKeys = ["accountEmail", "fullName"])
data class EmailFolder(
    val accountEmail: String,
    val fullName: String,
    val name: String,
    val parentFullName: String? = null,
    val holdsMessages: Boolean = true,
    val delimiter: String = "/"
)

@Serializable
@Entity(primaryKeys = ["accountEmail", "folderName", "id"])
data class EmailMessage(
    val accountEmail: String,
    val folderName: String,
    val id: Long,
    val subject: String,
    val from: String,
    val date: String,
    val body: String? = null,
    val isHtml: Boolean = false
)

@Serializable
@Entity
data class EmailAccount(
    @PrimaryKey val email: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long = 0
)
