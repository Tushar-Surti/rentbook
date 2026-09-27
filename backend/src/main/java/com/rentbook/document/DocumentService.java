package com.rentbook.document;

import com.rentbook.common.ApiException;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseRepository;
import com.rentbook.lease.LeaseService;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Files on a lease: maintenance photos and the document vault. An upload is two steps. The server
 * issues a presigned PUT and the browser sends the file straight to storage. The upload is then
 * confirmed against what actually landed in the bucket before anyone can use it.
 */
@Service
public class DocumentService {

    /** Photos and PDFs, up to 10 MB each. */
    static final long MAX_BYTES = 10L * 1024 * 1024;

    static final int MAX_CONDITION_PHOTOS = 6;

    /** Photos belong to a thread message or a report line, not to the lease's shelf. */
    private static final List<Document.Type> PHOTOS = List.of(Document.Type.TICKET_PHOTO, Document.Type.CONDITION_PHOTO);

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "application/pdf", ".pdf");

    private final DocumentRepository documents;
    private final StorageService storage;
    private final LeaseService leases;
    private final LeaseRepository leaseRepository;
    private final UserRepository users;
    private final ApplicationEventPublisher publisher;

    DocumentService(DocumentRepository documents, StorageService storage, LeaseService leases,
                    LeaseRepository leaseRepository, UserRepository users, ApplicationEventPublisher publisher) {
        this.documents = documents;
        this.storage = storage;
        this.leases = leases;
        this.leaseRepository = leaseRepository;
        this.users = users;
        this.publisher = publisher;
    }

    /** A shared document reached the shelf; {@link DocumentNotifications} tells the lease's other party. */
    public record DocumentFiled(UUID documentId, UUID leaseId, UUID landlordId, UUID tenantId, UUID uploaderId,
                                Document.Type type, String filename) {
    }

    public record UploadRequest(UUID leaseId, Document.Type type, String filename, String contentType, long sizeBytes,
                                Document.Visibility visibility) {
    }

    /** Where and how the browser sends the file. */
    public record UploadTicket(UUID documentId, String url, String method, Map<String, String> headers,
                               Instant expiresAt) {
    }

    public record DocumentView(UUID id, UUID leaseId, Document.Type type, Document.Visibility visibility,
                               String filename, String contentType, Long sizeBytes, Document.Status status,
                               UUID uploadedBy, Instant uploadedAt) {
    }

    /** A photo on a thread message, with a short-lived link to the image itself. */
    public record PhotoRef(UUID id, String filename, String url) {
    }

    public record Link(String url, Instant expiresAt) {
    }

    /** A file on a lease's shelf in the vault: what it is, who filed it, and whether it is this reader's own. */
    public record VaultEntry(UUID id, Document.Type type, Document.Visibility visibility, String filename,
                             String contentType, Long sizeBytes, Instant uploadedAt, String uploadedBy, boolean mine) {
    }

    @Transactional
    public UploadTicket startUpload(UUID userId, Role role, UploadRequest request) {
        requireConfigured();
        Lease lease = leases.require(request.leaseId(), userId, role);
        String contentType = request.contentType() == null ? "" : request.contentType().strip().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw ApiException.badRequest("file_type", "Upload a photo (JPEG, PNG or WebP) or a PDF.");
        }
        if (request.sizeBytes() <= 0 || request.sizeBytes() > MAX_BYTES) {
            throw ApiException.badRequest("file_size", "Files can be up to 10 MB.");
        }
        Document.Type type = request.type();
        switch (type) {
            case TICKET_PHOTO -> {
                if (!contentType.startsWith("image/")) {
                    throw ApiException.badRequest("file_type", "Attach photos as JPEG, PNG or WebP.");
                }
            }
            case RECEIPT -> throw ApiException.badRequest("document_type", "Receipts are issued by Rentbook.");
            case CONDITION_PHOTO -> throw ApiException.badRequest("document_type",
                    "Add condition photos from the report itself.");
            case KYC -> {
                if (role != Role.TENANT) {
                    throw ApiException.badRequest("document_type", "Tenants upload their own ID documents.");
                }
            }
            case LEASE -> {
                if (role != Role.LANDLORD) {
                    throw ApiException.badRequest("document_type", "The landlord uploads the lease agreement.");
                }
            }
            case OTHER -> {
            }
        }
        // Only a landlord may keep a file to themselves; everything else is shared by the lease's two parties.
        Document.Visibility visibility = type == Document.Type.OTHER && role == Role.LANDLORD
                && request.visibility() == Document.Visibility.LANDLORD_ONLY
                ? Document.Visibility.LANDLORD_ONLY : Document.Visibility.LEASE_PARTIES;
        Document document = documents.save(new Document(lease.getLandlordId(), lease.getId(), userId, type, visibility,
                cleanName(request.filename(), extension), contentType, extension));
        StorageService.SignedRequest signed = storage.presignUpload(document.getStorageKey(), contentType);
        return new UploadTicket(document.getId(), signed.url(), "PUT", signed.headers(), signed.expiresAt());
    }

    /** Confirms the upload against the bucket itself; a browser's word that it finished is not enough. */
    @Transactional
    public DocumentView complete(UUID documentId, UUID userId) {
        requireConfigured();
        Document document = documents.findByIdAndUploadedBy(documentId, userId)
                .orElseThrow(() -> ApiException.notFound("Document"));
        if (!document.isAvailable()) {
            StorageService.StoredObject stored = storage.head(document.getStorageKey())
                    .orElseThrow(() -> ApiException.conflict("not_uploaded",
                            "The file hasn't reached storage. Try uploading it again."));
            if (stored.size() > MAX_BYTES) {
                storage.delete(document.getStorageKey());
                throw ApiException.badRequest("file_size", "Files can be up to 10 MB.");
            }
            document.markAvailable(stored.size());
            // A shared file is news to the other party; a photo arrives with its message or its report, a
            // private file never.
            if (!PHOTOS.contains(document.getType())
                    && document.getVisibility() == Document.Visibility.LEASE_PARTIES) {
                leaseRepository.findById(document.getLeaseId()).ifPresent(lease -> publisher.publishEvent(
                        new DocumentFiled(document.getId(), lease.getId(), lease.getLandlordId(), lease.getTenantId(),
                                userId, document.getType(), document.getFilename())));
            }
        }
        return view(document);
    }

    /** A short-lived link. Photos open in the browser unless {@code asAttachment}; everything else downloads. */
    @Transactional(readOnly = true)
    public Link download(UUID documentId, UUID userId, Role role, boolean asAttachment) {
        requireConfigured();
        Document document = documents.findById(documentId)
                .filter(Document::isAvailable)
                .filter(found -> canSee(found, userId, role))
                .orElseThrow(() -> ApiException.notFound("Document"));
        boolean inline = !asAttachment && document.getContentType().startsWith("image/");
        StorageService.SignedRequest signed = storage.presignDownload(document.getStorageKey(), document.getFilename(),
                document.getContentType(), inline);
        return new Link(signed.url(), signed.expiresAt());
    }

    /** The lease's shelf: agreements, ID documents and other files this reader may see, newest first. */
    @Transactional(readOnly = true)
    public List<VaultEntry> vault(UUID leaseId, UUID userId, Role role) {
        leases.require(leaseId, userId, role);
        List<Document> shelf = documents.findByLeaseIdAndStatusAndTypeNotInOrderByCreatedAtDesc(leaseId,
                        Document.Status.AVAILABLE, PHOTOS).stream()
                .filter(document -> canSee(document, userId, role))
                .toList();
        Map<UUID, String> names = users.findAllById(shelf.stream().map(Document::getUploadedBy).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return shelf.stream().map(document -> new VaultEntry(document.getId(), document.getType(),
                document.getVisibility(), document.getFilename(), document.getContentType(), document.getSizeBytes(),
                document.getCreatedAt(), names.getOrDefault(document.getUploadedBy(), "Someone"),
                document.getUploadedBy().equals(userId))).toList();
    }

    /**
     * Only whoever filed a document may take it off the shelf. A photo in a request's thread stays, as
     * the thread does. The file leaves storage once its row is gone for good.
     */
    @Transactional
    public void remove(UUID documentId, UUID userId) {
        requireConfigured();
        Document document = documents.findByIdAndUploadedBy(documentId, userId)
                .orElseThrow(() -> ApiException.notFound("Document"));
        if (document.getTicketEventId() != null) {
            throw ApiException.conflict("in_thread",
                    "This photo is part of a request's thread, which is kept as it was written.");
        }
        if (document.getConditionItemId() != null) {
            throw ApiException.conflict("in_report", "Remove this photo from the condition report instead.");
        }
        discard(List.of(document));
    }

    /** Photos for thread messages, oldest first. The caller has already checked the reader may see the thread. */
    @Transactional(readOnly = true)
    public Map<UUID, List<PhotoRef>> photosFor(Collection<UUID> ticketEventIds) {
        if (ticketEventIds.isEmpty() || !storage.configured()) {
            return Map.of();
        }
        return documents.findByTicketEventIdInAndStatusOrderByCreatedAtAsc(ticketEventIds, Document.Status.AVAILABLE)
                .stream()
                .collect(Collectors.groupingBy(Document::getTicketEventId, LinkedHashMap::new,
                        Collectors.mapping(photo -> new PhotoRef(photo.getId(), photo.getFilename(),
                                storage.presignDownload(photo.getStorageKey(), photo.getFilename(),
                                        photo.getContentType(), true).url()), Collectors.toList())));
    }

    /**
     * Attaches photos to a new thread message. Each must be the author's own, on this lease, confirmed in
     * storage, and not already attached somewhere else.
     */
    @Transactional
    public void attachPhotos(List<UUID> photoIds, UUID ticketEventId, UUID authorId, UUID leaseId) {
        if (photoIds == null || photoIds.isEmpty()) {
            return;
        }
        List<Document> photos = documents.findAllById(photoIds);
        boolean usable = photos.size() == new HashSet<>(photoIds).size() && photos.stream().allMatch(photo ->
                photo.getUploadedBy().equals(authorId)
                        && leaseId.equals(photo.getLeaseId())
                        && photo.getType() == Document.Type.TICKET_PHOTO
                        && photo.isAvailable()
                        && photo.getTicketEventId() == null);
        if (!usable) {
            throw ApiException.badRequest("photos", "One of those photos isn't ready to attach. Upload it again.");
        }
        photos.forEach(photo -> photo.attachTo(ticketEventId));
    }

    /** A photo on a condition report line, with who took it. */
    public record ConditionPhoto(UUID id, String filename, String url, UUID uploadedBy) {
    }

    /**
     * Signs the upload of a photo for one line of a condition report. The caller has already checked that
     * this person may add photos to that line right now.
     */
    @Transactional
    public UploadTicket startConditionPhoto(Lease lease, UUID userId, UUID conditionItemId, String filename,
                                            String contentType, long sizeBytes) {
        requireConfigured();
        String type = contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(type);
        if (extension == null || !type.startsWith("image/")) {
            throw ApiException.badRequest("file_type", "Add photos as JPEG, PNG or WebP.");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_BYTES) {
            throw ApiException.badRequest("file_size", "Photos can be up to 10 MB.");
        }
        if (documents.countByConditionItemId(conditionItemId) >= MAX_CONDITION_PHOTOS) {
            throw ApiException.conflict("too_many_photos", "A line can have up to six photos.");
        }
        Document photo = documents.save(Document.conditionPhoto(lease.getLandlordId(), lease.getId(), userId,
                conditionItemId, cleanName(filename, extension), type, extension));
        StorageService.SignedRequest signed = storage.presignUpload(photo.getStorageKey(), type);
        return new UploadTicket(photo.getId(), signed.url(), "PUT", signed.headers(), signed.expiresAt());
    }

    /** Confirmed photos for each report line, oldest first, with short-lived links to the images. */
    @Transactional(readOnly = true)
    public Map<UUID, List<ConditionPhoto>> conditionPhotos(Collection<UUID> conditionItemIds) {
        if (conditionItemIds.isEmpty() || !storage.configured()) {
            return Map.of();
        }
        return documents.findByConditionItemIdInAndStatusOrderByCreatedAtAsc(conditionItemIds,
                        Document.Status.AVAILABLE).stream()
                .collect(Collectors.groupingBy(Document::getConditionItemId, LinkedHashMap::new,
                        Collectors.mapping(photo -> new ConditionPhoto(photo.getId(), photo.getFilename(),
                                storage.presignDownload(photo.getStorageKey(), photo.getFilename(),
                                        photo.getContentType(), true).url(), photo.getUploadedBy()),
                                Collectors.toList())));
    }

    /** The report line one of this person's own photos belongs to; anything else is "not found". */
    @Transactional(readOnly = true)
    public UUID conditionItemOf(UUID documentId, UUID userId) {
        return documents.findByIdAndUploadedBy(documentId, userId)
                .map(Document::getConditionItemId)
                .orElseThrow(() -> ApiException.notFound("Photo"));
    }

    /** Removes one photo from its report line. The caller has checked the line can still change. */
    @Transactional
    public void discardConditionPhoto(UUID documentId) {
        documents.findById(documentId).ifPresent(photo -> discard(List.of(photo)));
    }

    /** Removes every photo on these report lines, before the lines themselves go. */
    @Transactional
    public void discardConditionPhotos(Collection<UUID> conditionItemIds) {
        if (!conditionItemIds.isEmpty()) {
            discard(documents.findByConditionItemIdIn(conditionItemIds));
        }
    }

    /** Deletes the rows now and the files once that has committed. */
    private void discard(List<Document> gone) {
        if (gone.isEmpty()) {
            return;
        }
        documents.deleteAll(gone);
        documents.flush();
        List<String> keys = gone.stream().map(Document::getStorageKey).toList();
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
                        log.warn("Couldn't delete {} from storage; nothing refers to it any more", key, e);
                    }
                }
            }
        });
    }

    private boolean canSee(Document document, UUID userId, Role role) {
        if (role == Role.LANDLORD) {
            return document.getLandlordId().equals(userId);
        }
        return document.getLeaseId() != null && leases.isParty(document.getLeaseId(), userId)
                && (document.getVisibility() == Document.Visibility.LEASE_PARTIES
                || document.getUploadedBy().equals(userId));
    }

    private void requireConfigured() {
        if (!storage.configured()) {
            throw ApiException.serviceUnavailable("storage_off", "File uploads aren't set up on this server yet.");
        }
    }

    private static String cleanName(String filename, String extension) {
        String name = filename == null ? "" : filename.replaceAll("[\\p{Cntrl}/\\\\]", "").strip();
        if (name.isEmpty()) {
            name = "file" + extension;
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static DocumentView view(Document document) {
        return new DocumentView(document.getId(), document.getLeaseId(), document.getType(), document.getVisibility(),
                document.getFilename(), document.getContentType(), document.getSizeBytes(), document.getStatus(),
                document.getUploadedBy(), document.getCreatedAt());
    }
}
