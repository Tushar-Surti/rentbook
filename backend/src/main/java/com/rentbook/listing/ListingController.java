package com.rentbook.listing;

import com.rentbook.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The landlord's listings, and the public page's two doors: read it, and send an enquiry. */
@RestController
@RequestMapping("/api/v1")
class ListingController {

    private final Listings listings;

    ListingController(Listings listings) {
        this.listings = listings;
    }

    record TermsRequest(@NotNull @Positive Long rentPaise, @NotNull @PositiveOrZero Long depositPaise,
                        @NotNull LocalDate availableFrom, @Size(max = 2000) String description) {
        Listings.Terms terms() {
            return new Listings.Terms(rentPaise, depositPaise, availableFrom, description);
        }
    }

    record PhotoRequest(@NotBlank String contentType, @Positive long sizeBytes) {
    }

    record EnquiryStatusRequest(@NotNull ListingEnquiry.Status status) {
    }

    /** {@code website} is a field people never see; anything in it came from a bot. */
    record EnquiryRequest(@NotBlank @Size(max = 120) String name,
                          @NotBlank @Pattern(regexp = "^\\+?[0-9 ]{10,15}$", message = "Enter a phone number") String phone,
                          @Email @Size(max = 254) String email, @Size(max = 1000) String message, LocalDate visitOn,
                          String website) {
    }

    @GetMapping("/listings")
    @PreAuthorize("hasRole('LANDLORD')")
    List<Listings.ListingView> list(@AuthenticationPrincipal Jwt jwt) {
        return listings.list(CurrentUser.id(jwt));
    }

    @PostMapping("/units/{unitId}/listing")
    @PreAuthorize("hasRole('LANDLORD')")
    @ResponseStatus(HttpStatus.CREATED)
    Listings.ListingView create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID unitId,
                                @Valid @RequestBody TermsRequest body) {
        return listings.create(CurrentUser.id(jwt), unitId, body.terms());
    }

    @GetMapping("/listings/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    Listings.ListingView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return listings.get(CurrentUser.id(jwt), id);
    }

    @PutMapping("/listings/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    Listings.ListingView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody TermsRequest body) {
        return listings.update(CurrentUser.id(jwt), id, body.terms());
    }

    @PostMapping("/listings/{id}/close")
    @PreAuthorize("hasRole('LANDLORD')")
    Listings.ListingView close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return listings.close(CurrentUser.id(jwt), id);
    }

    @PostMapping("/listings/{id}/photos")
    @PreAuthorize("hasRole('LANDLORD')")
    Listings.PhotoUpload startPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody PhotoRequest body) {
        return listings.startPhoto(CurrentUser.id(jwt), id, body.contentType(), body.sizeBytes());
    }

    @PostMapping("/listings/{id}/photos/{photoId}/complete")
    @PreAuthorize("hasRole('LANDLORD')")
    Listings.ListingView completePhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @PathVariable UUID photoId) {
        return listings.completePhoto(CurrentUser.id(jwt), id, photoId);
    }

    @DeleteMapping("/listings/{id}/photos/{photoId}")
    @PreAuthorize("hasRole('LANDLORD')")
    Listings.ListingView removePhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @PathVariable UUID photoId) {
        return listings.removePhoto(CurrentUser.id(jwt), id, photoId);
    }

    @PatchMapping("/listings/{id}/enquiries/{enquiryId}")
    @PreAuthorize("hasRole('LANDLORD')")
    Listings.ListingView markEnquiry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @PathVariable UUID enquiryId, @Valid @RequestBody EnquiryStatusRequest body) {
        return listings.markEnquiry(CurrentUser.id(jwt), id, enquiryId, body.status());
    }

    @GetMapping("/public/listings/{slug}")
    Listings.PublicListing publicView(@PathVariable String slug) {
        return listings.publicView(slug);
    }

    @PostMapping("/public/listings/{slug}/enquiries")
    ResponseEntity<Void> enquire(@PathVariable String slug, @Valid @RequestBody EnquiryRequest body) {
        if (body.website() == null || body.website().isBlank()) {
            listings.enquire(slug, body.name(), body.phone(), body.email(), body.message(), body.visitOn());
        }
        return ResponseEntity.accepted().build();
    }
}
