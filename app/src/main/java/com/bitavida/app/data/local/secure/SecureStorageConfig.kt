package com.bitavida.app.data.local.secure

internal data class SecureStorageConfig(
    val databaseName: String,
    val wrappedKeyFileName: String,
    val wrappingKeyAlias: String,
)

internal val ProductionSecureStorageConfig = SecureStorageConfig(
    databaseName = "bitavida-secure-spike.db",
    wrappedKeyFileName = "bitavida-secure-spike-key-v1.bin",
    wrappingKeyAlias = "com.bitavida.app.secure-spike.database-key.v1",
)
