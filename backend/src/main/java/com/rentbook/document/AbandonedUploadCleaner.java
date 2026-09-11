package com.rentbook.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * An upload that was signed but never confirmed leaves a PENDING row, and perhaps an object nobody will
 * ever use. Once a day, anything left pending for more than a day goes, from the database and from storage.
 */
@Component
class AbandonedUploadCleaner {

    static final Duration GRACE = Duration.ofDays(1);

    private static final Logger log = LoggerFactory.getLogger(AbandonedUploadCleaner.class);

    private final DocumentRepository documents;
    private final StorageService storage;
    private final Clock clock;

    AbandonedUploadCleaner(DocumentRepository documents, StorageService storage, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.clock = clock;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "Asia/Kolkata")
    @Transactional
    public int clear() {
        List<Document> abandoned = documents.findByStatusAndCreatedAtBefore(Document.Status.PENDING,
                clock.instant().minus(GRACE));
        if (abandoned.isEmpty()) {
            return 0;
        }
        documents.deleteAll(abandoned);
        List<String> keys = abandoned.stream().map(Document::getStorageKey).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (!storage.configured()) {
                    return;
                }
                for (String key : keys) {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException e) {
                        log.warn("Couldn't delete abandoned upload {} from storage", key, e);
                    }
                }
            }
        });
        log.info("Cleared {} uploads that were never confirmed", abandoned.size());
        return abandoned.size();
    }
}
