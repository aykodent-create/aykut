package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "products")
@JsonClass(generateAdapter = true)
data class Product(
    @PrimaryKey val barcode: String,
    val name: String,
    val price: Double,
    val description: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)
