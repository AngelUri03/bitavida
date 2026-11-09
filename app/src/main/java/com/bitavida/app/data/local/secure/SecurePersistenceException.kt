package com.bitavida.app.data.local.secure

sealed class SecurePersistenceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class InconsistentSecureStorageException(
    message: String,
) : SecurePersistenceException(message)

class WrappingKeyUnavailableException(
    cause: Throwable? = null,
) : SecurePersistenceException("The database wrapping key is unavailable.", cause)

class WrappedDatabaseKeyException(
    cause: Throwable? = null,
) : SecurePersistenceException("The wrapped database key could not be recovered.", cause)

class SecureDatabaseUnavailableException(
    cause: Throwable,
) : SecurePersistenceException("The encrypted database could not be opened.", cause)
