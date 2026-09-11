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
            // A shared file is news to the other party; a photo arrives with its message, a private file never.
            if (document.getType() != Document.Type.TICKET_PHOTO
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
        List<Document> shelf = documents.findByLeaseIdAndStatusAndTypeNotOrderByCreatedAtDesc(leaseId,
                        Document.Status.AVAILABLE, Document.Type.TICKET_PHOTO).stream()
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
        documents.delete(document);
        String key = document.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storage.delete(key);
                } catch (RuntimeException e) {
                    log.warn("Couldn't delete {} from storage; nothing refers to it any more", key, e);
                }
            }
        });
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
