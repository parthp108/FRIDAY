package com.health.friday.shared.sync.cloud

/**
 * Durable persistence contract for cloud synchronization.
 *
 * Android and desktop will provide separate implementations.
 *
 * Important:
 * - Outbox entries must survive application restarts.
 * - Uploaded entries must not be deleted immediately.
 * - Inbox processing must be idempotent.
 * - Cursor advancement must be atomic with remote operation processing.
 */
interface CloudSyncStore {

    /**
     * Persist a locally created operation.
     *
     * Repeated calls with the same operationId must not create duplicates.
     */
    suspend fun enqueue(operation: SyncOperation)

    /**
     * Return pending operations in chronological order.
     *
     * This method must not mark them as uploaded.
     */
    suspend fun pendingOutbox(
        limit: Int
    ): List<SyncOperation>

    /**
     * Mark operations as uploaded only after Drive confirms
     * successful batch creation.
     *
     * Keep uploaded records temporarily for recovery and diagnostics.
     */
    suspend fun markUploaded(
        operationIds: List<String>,
        batchId: String,
        uploadedAtMs: Long
    )

    /**
     * Retrieve the last committed Drive Changes API cursor.
     */
    suspend fun readCursor(): SyncCursor

    /**
     * Process a downloaded batch.
     *
     * Implementations must:
     * 1. Validate the batch.
     * 2. Ignore operation IDs already present in the inbox.
     * 3. Apply new operations.
     * 4. Record processed operation IDs.
     * 5. Save nextCursor.
     *
     * Steps 2–5 must execute in one database transaction.
     *
     * Replaying a previously processed batch must be harmless.
     */
    suspend fun ingestRemoteBatch(
        batch: SyncBatch,
        nextCursor: SyncCursor
    ): RemoteBatchResult
}

data class RemoteBatchResult(
    val appliedOperations: Int,
    val duplicateOperations: Int
)
