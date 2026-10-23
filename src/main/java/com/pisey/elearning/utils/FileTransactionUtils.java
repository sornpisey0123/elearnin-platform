package com.pisey.elearning.utils;

import com.pisey.elearning.service.FileStorageService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Keeps stored files consistent with the database row that points at them. */
public final class FileTransactionUtils {

    private FileTransactionUtils() {
    }

    /**
     * After the current transaction: on commit delete {@code oldUrl} (the row now points at {@code newUrl});
     * on rollback delete {@code newUrl} (the row still points at {@code oldUrl}). Null URLs are ignored.
     */
    public static void replaceAfterCompletion(FileStorageService storage, String oldUrl, String newUrl) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                storage.delete(status == STATUS_COMMITTED ? oldUrl : newUrl);
            }
        });
    }

    /** Deletes {@code url} only if the current transaction commits. */
    public static void deleteAfterCommit(FileStorageService storage, String url) {
        replaceAfterCompletion(storage, url, null);
    }
}
