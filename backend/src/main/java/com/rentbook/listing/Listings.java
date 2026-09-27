package com.rentbook.listing;

import com.rentbook.common.ApiException;
import com.rentbook.common.IndiaTime;
import com.rentbook.document.StorageService;
import com.rentbook.invite.InviteService;
import com.rentbook.property.Property;
import com.rentbook.property.PropertyRepository;
import com.rentbook.property.PropertyService;
import com.rentbook.property.Unit;
import com.rentbook.property.UnitRepository;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vacancies offered publicly. A landlord lists a vacant flat, room or bed with its rent, photos and a few
 * words; the listing gets a short public link to share. People who see it send an enquiry, the landlord
 * invites the one they choose, and the listing closes itself when that tenant moves in. The public page
 * gives the property's name and area, never its street address, which the landlord shares when they reply.
 */
@Service
public class Listings {

    static final int MAX_PHOTOS = 8;
    static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> PHOTO_TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");
    private static final String SLUG_LETTERS = "abcdefghjkmnpqrstuvwxyz23456789";

    private final ListingRepository listings;
    private final ListingPhotoRepository photos;
    private final ListingEnquiryRepository enquiries;
    private final PropertyService propertyService;
    private final PropertyRepository properties;
    private final UnitRepository units;
    private final UserRepository users;
    private final StorageService storage;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    Listings(ListingRepository listings, ListingPhotoRepository photos, ListingEnquiryRepository enquiries,
             PropertyService propertyService, PropertyRepository properties, UnitRepository units,
             UserRepository users, StorageService storage, ApplicationEventPublisher events, Clock clock) {
        this.listings = listings;
        this.photos = photos;
        this.enquiries = enquiries;
        this.propertyService = propertyService;
        this.properties = properties;
        this.units = units;
        this.users = users;
        this.storage = storage;
        this.events = events;
        this.clock = clock;
    }

    public record Terms(long rentPaise, long depositPaise, LocalDate availableFrom, String description) {
    }

    public record Place(UUID propertyId, String propertyName, Property.Kind propertyKind, String city, String pincode,
                        UUID unitId, Unit.Kind unitKind, String unitLabel, String roomLabel, int bedsInRoom) {
    }

    public record PhotoView(UUID id, String url) {
    }

    public record EnquiryView(UUID id, String name, String phone, String email, String message, LocalDate visitOn,
                              ListingEnquiry.Status status, Instant receivedAt) {
    }

    /** The landlord's view of one listing, with its enquiries, newest first. */
    public record ListingView(UUID id, String slug, Listing.Status status, long rentPaise, long depositPaise,
                              LocalDate availableFrom, String description, Place place, List<PhotoView> photos,
                              List<EnquiryView> enquiries, Instant createdAt) {
    }

    /** What anyone with the link sees. */
    public record PublicListing(String slug, boolean open, String landlordFirstName, Place place, long rentPaise,
                                long depositPaise, LocalDate availableFrom, String description,
                                List<PhotoView> photos) {
    }

    public record PhotoUpload(UUID photoId, String url, Map<String, String> headers, Instant expiresAt) {
    }

    /** Published inside the transaction; {@link ListingNotifications} emails the landlord after commit. */
    public record EnquiryReceived(UUID listingId, UUID landlordId, String place, String name, String phone,
                                  String email, String message, LocalDate visitOn) {
    }

    @Transactional
    public ListingView create(UUID landlordId, UUID unitId, Terms terms) {
        Unit unit = propertyService.leasableUnit(landlordId, unitId);
        if (listings.findByUnitIdAndStatus(unit.getId(), Listing.Status.OPEN).isPresent()) {
            throw ApiException.conflict("already_listed", "This unit is already listed. Change that listing instead.");
        }
        Listing listing = new Listing(landlordId, unit.getId(), slug());
        listing.describe(terms.rentPaise(), terms.depositPaise(), terms.availableFrom(), clean(terms.description()));
        listings.save(listing);
        return view(listing);
    }

    @Transactional
    public ListingView update(UUID landlordId, UUID listingId, Terms terms) {
        Listing listing = open(landlordId, listingId);
        listing.describe(terms.rentPaise(), terms.depositPaise(), terms.availableFrom(), clean(terms.description()));
        return view(listing);
    }

    @Transactional
    public ListingView close(UUID landlordId, UUID listingId) {
        Listing listing = own(landlordId, listingId);
        listing.close(clock.instant());
        return view(listing);
    }

    @Transactional(readOnly = true)
    public List<ListingView> list(UUID landlordId) {
        return views(listings.findByLandlordIdOrderByCreatedAtDesc(landlordId), true);
    }

    @Transactional(readOnly = true)
    public ListingView get(UUID landlordId, UUID listingId) {
        return view(own(landlordId, listingId));
    }

    /** Open listings by unit, for the landlord's register. */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> openByUnit(UUID landlordId) {
        return listings.findByLandlordIdAndStatus(landlordId, Listing.Status.OPEN).stream()
                .collect(Collectors.toMap(Listing::getUnitId, Listing::getId, (first, second) -> first));
    }

    @Transactional
    public PhotoUpload startPhoto(UUID landlordId, UUID listingId, String contentType, long sizeBytes) {
        requireStorage();
        Listing listing = open(landlordId, listingId);
        String type = contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
        String extension = PHOTO_TYPES.get(type);
        if (extension == null) {
            throw ApiException.badRequest("file_type", "Add photos as JPEG, PNG or WebP.");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_PHOTO_BYTES) {
            throw ApiException.badRequest("file_size", "Photos can be up to 10 MB.");
        }
        if (photos.countByListingId(listing.getId()) >= MAX_PHOTOS) {
            throw ApiException.conflict("too_many_photos", "A listing can have up to " + MAX_PHOTOS + " photos.");
        }
        ListingPhoto photo = photos.save(new ListingPhoto(listing.getId(), type, extension, clock.instant()));
        StorageService.SignedRequest signed = storage.presignUpload(photo.getStorageKey(), type);
        return new PhotoUpload(photo.getId(), signed.url(), signed.headers(), signed.expiresAt());
    }

    /** Confirmed against the bucket itself before the photo appears anywhere. */
    @Transactional
    public ListingView completePhoto(UUID landlordId, UUID listingId, UUID photoId) {
        requireStorage();
        Listing listing = own(landlordId, listingId);
        ListingPhoto photo = photos.findById(photoId).filter(found -> found.getListingId().equals(listing.getId()))
                .orElseThrow(() -> ApiException.notFound("Photo"));
        if (!photo.isAvailable()) {
            StorageService.StoredObject stored = storage.head(photo.getStorageKey())
                    .orElseThrow(() -> ApiException.conflict("not_uploaded", "The photo hasn't reached storage. Try again."));
            if (stored.size() > MAX_PHOTO_BYTES) {
                storage.delete(photo.getStorageKey());
                photos.delete(photo);
                throw ApiException.badRequest("file_size", "Photos can be up to 10 MB.");
            }
            photo.markAvailable(stored.size());
        }
        return view(listing);
    }

    @Transactional
    public ListingView removePhoto(UUID landlordId, UUID listingId, UUID photoId) {
        Listing listing = own(landlordId, listingId);
        photos.findById(photoId).filter(found -> found.getListingId().equals(listing.getId())).ifPresent(photo -> {
            photos.delete(photo);
            if (storage.configured()) {
                storage.delete(photo.getStorageKey());
            }
        });
        return view(listing);
    }

    @Transactional
    public ListingView markEnquiry(UUID landlordId, UUID listingId, UUID enquiryId, ListingEnquiry.Status status) {
        Listing listing = own(landlordId, listingId);
        ListingEnquiry enquiry = enquiries.findById(enquiryId)
                .filter(found -> found.getListingId().equals(listing.getId()))
                .orElseThrow(() -> ApiException.notFound("Enquiry"));
        enquiry.mark(status);
        return view(listing);
    }

    @Transactional(readOnly = true)
    public PublicListing publicView(String slug) {
        Listing listing = listings.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Listing"));
        String landlord = users.findById(listing.getLandlordId()).map(User::getFullName)
                .map(name -> name.strip().split("\\s+")[0]).orElse("The landlord");
        List<PhotoView> shown = listing.isOpen() ? photoViews(List.of(listing.getId())).getOrDefault(listing.getId(), List.of())
                : List.of();
        return new PublicListing(listing.getSlug(), listing.isOpen(), landlord, place(listing), listing.getRentPaise(),
                listing.getDepositPaise(), listing.getAvailableFrom(), listing.getDescription(), shown);
    }

    /** An enquiry from the public page. The caller has already filtered out bots. */
    @Transactional
    public void enquire(String slug, String name, String phone, String email, String message, LocalDate visitOn) {
        Listing listing = listings.findBySlug(slug).filter(Listing::isOpen)
                .orElseThrow(() -> ApiException.gone("listing_closed", "This place has been let. The listing is closed."));
        LocalDate today = IndiaTime.today(clock);
        if (visitOn != null && visitOn.isBefore(today)) {
            throw ApiException.badRequest("visit_in_past", "Pick a visit day from today on.");
        }
        ListingEnquiry enquiry = enquiries.save(new ListingEnquiry(listing.getId(), name.strip(), phone.strip(),
                clean(email), clean(message), visitOn, clock.instant()));
        Place place = place(listing);
        events.publishEvent(new EnquiryReceived(listing.getId(), listing.getLandlordId(), describe(place),
                enquiry.getName(), enquiry.getPhone(), enquiry.getEmail(), enquiry.getMessage(), enquiry.getVisitOn()));
    }

    /** Someone moved in: the unit is let, so its listing closes. */
    @EventListener
    void movedIn(InviteService.InviteAccepted accepted) {
        listings.findByUnitIdAndStatus(accepted.unitId(), Listing.Status.OPEN)
                .ifPresent(listing -> listing.close(clock.instant()));
    }

    static String describe(Place place) {
        String unit = place.roomLabel() == null ? place.unitLabel() : place.unitLabel() + ", " + place.roomLabel();
        return unit + ", " + place.propertyName();
    }

    private Listing own(UUID landlordId, UUID listingId) {
        return listings.findByIdAndLandlordId(listingId, landlordId).orElseThrow(() -> ApiException.notFound("Listing"));
    }

    private Listing open(UUID landlordId, UUID listingId) {
        Listing listing = own(landlordId, listingId);
        if (!listing.isOpen()) {
            throw ApiException.conflict("listing_closed", "This listing is closed. List the unit again if it's free.");
        }
        return listing;
    }

    private ListingView view(Listing listing) {
        return views(List.of(listing), true).getFirst();
    }

    private List<ListingView> views(List<Listing> found, boolean withEnquiries) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = found.stream().map(Listing::getId).toList();
        Map<UUID, List<PhotoView>> shown = photoViews(ids);
        Map<UUID, List<EnquiryView>> asked = !withEnquiries ? Map.of()
                : enquiries.findByListingIdInOrderByCreatedAtDesc(ids).stream().collect(Collectors.groupingBy(
                ListingEnquiry::getListingId, Collectors.mapping(enquiry -> new EnquiryView(enquiry.getId(), enquiry.getName(),
                        enquiry.getPhone(), enquiry.getEmail(), enquiry.getMessage(), enquiry.getVisitOn(),
                        enquiry.getStatus(), enquiry.getCreatedAt()), Collectors.toList())));
        return found.stream().map(listing -> new ListingView(listing.getId(), listing.getSlug(), listing.getStatus(),
                listing.getRentPaise(), listing.getDepositPaise(), listing.getAvailableFrom(), listing.getDescription(),
                place(listing), shown.getOrDefault(listing.getId(), List.of()),
                asked.getOrDefault(listing.getId(), List.of()), listing.getCreatedAt())).toList();
    }

    private Map<UUID, List<PhotoView>> photoViews(Collection<UUID> listingIds) {
        if (!storage.configured()) {
            return Map.of();
        }
        return photos.findByListingIdInOrderByCreatedAtAsc(listingIds).stream().filter(ListingPhoto::isAvailable)
                .collect(Collectors.groupingBy(ListingPhoto::getListingId, Collectors.mapping(photo -> new PhotoView(
                        photo.getId(), storage.presignDownload(photo.getStorageKey(), "photo", photo.getContentType(), true)
                                .url()), Collectors.toList())));
    }

    private Place place(Listing listing) {
        Unit unit = units.findById(listing.getUnitId()).orElseThrow();
        Unit room = unit.getParentUnitId() == null ? null : units.findById(unit.getParentUnitId()).orElse(null);
        Property property = properties.findById(unit.getPropertyId()).orElseThrow();
        int beds = room == null ? 0 : (int) units.countByParentUnitId(room.getId());
        return new Place(property.getId(), property.getName(), property.getKind(), property.getCity(),
                property.getPincode(), unit.getId(), unit.getKind(), unit.getLabel(),
                room == null ? null : room.getLabel(), beds);
    }

    private String slug() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder slug = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                slug.append(SLUG_LETTERS.charAt(random.nextInt(SLUG_LETTERS.length())));
            }
            if (listings.findBySlug(slug.toString()).isEmpty()) {
                return slug.toString();
            }
        }
        throw new IllegalStateException("Could not find a free listing link");
    }

    private void requireStorage() {
        if (!storage.configured()) {
            throw ApiException.serviceUnavailable("storage_off", "Photo storage isn't set up yet.");
        }
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

}
