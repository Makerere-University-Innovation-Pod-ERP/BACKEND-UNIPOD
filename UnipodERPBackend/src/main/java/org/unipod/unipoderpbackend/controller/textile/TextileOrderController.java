package org.unipod.unipoderpbackend.controller.textile;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.unipod.unipoderpbackend.dto.textile.TextileCollectionRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileMessageRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileOrderRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileProductionRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileQualityRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileQuoteRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileReasonRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileTransitionRequest;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.textile.TextileOrderService;

@RestController
@RequestMapping("/textile")
@Validated
public class TextileOrderController {
    private final TextileOrderService orders;
    private final CurrentActor currentActor;

    public TextileOrderController(TextileOrderService orders, CurrentActor currentActor) {
        this.orders = orders;
        this.currentActor = currentActor;
    }

    @GetMapping("/orders")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> list(Authentication authentication) {
        return orders.list(currentActor.from(authentication));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> get(@PathVariable UUID id, Authentication authentication) {
        return orders.get(id, currentActor.from(authentication));
    }

    @GetMapping("/orders/{id}/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StreamingResponseBody> photo(@PathVariable UUID id, Authentication authentication) {
        TextileOrderService.DownloadedPhoto photo = orders.photo(id, currentActor.from(authentication));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(photo.contentType()));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(photo.filename(), StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(output -> {
            try (var input = photo.content()) {
                input.transferTo(output);
            }
        });
    }

    @PostMapping(value = "/orders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','DEPARTMENT_COORDINATOR','RECEPTIONIST')")
    public Map<String, Object> create(@Valid @RequestPart("order") TextileOrderRequest request,
                                      @RequestPart(value = "photo", required = false) MultipartFile photo,
                                      Authentication authentication) {
        return orders.create(request, photo, currentActor.from(authentication));
    }

    @PutMapping(value = "/orders/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','DEPARTMENT_COORDINATOR','RECEPTIONIST')")
    public Map<String, Object> update(@PathVariable UUID id,
                                      @Valid @RequestPart("order") TextileOrderRequest request,
                                      @RequestPart(value = "photo", required = false) MultipartFile photo,
                                      Authentication authentication) {
        return orders.update(id, request, photo, currentActor.from(authentication));
    }

    @GetMapping("/price-list")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> priceList(Authentication authentication) {
        return Map.of("items", orders.priceList(currentActor.from(authentication)));
    }

    @PostMapping("/orders/{id}/quote")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','DEPARTMENT_COORDINATOR')")
    public Map<String, Object> quote(@PathVariable UUID id, @Valid @RequestBody TextileQuoteRequest request,
                                     Authentication authentication) {
        return orders.quote(id, request, currentActor.from(authentication));
    }

    @PostMapping("/orders/{id}/request-details")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','DEPARTMENT_COORDINATOR')")
    public Map<String, Object> requestDetails(@PathVariable UUID id, @Valid @RequestBody TextileMessageRequest request,
                                              Authentication authentication) {
        return orders.requestDetails(id, request.message(), currentActor.from(authentication));
    }

    @PostMapping("/orders/{id}/send-back")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','DEPARTMENT_COORDINATOR')")
    public Map<String, Object> sendBack(@PathVariable UUID id, @Valid @RequestBody TextileReasonRequest request,
                                        Authentication authentication) {
        return orders.sendBack(id, request.reason(), currentActor.from(authentication));
    }

    @PostMapping("/orders/{id}/transitions")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','DEPARTMENT_COORDINATOR')")
    public Map<String, Object> transition(@PathVariable UUID id, @Valid @RequestBody TextileTransitionRequest request,
                                          Authentication authentication) {
        return orders.transition(id, request, currentActor.from(authentication));
    }

    @PutMapping("/orders/{id}/production")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','DEPARTMENT_COORDINATOR')")
    public Map<String, Object> production(@PathVariable UUID id, @Valid @RequestBody TextileProductionRequest request,
                                           Authentication authentication) {
        return orders.saveProduction(id, request, currentActor.from(authentication));
    }

    @PutMapping("/orders/{id}/quality")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','DEPARTMENT_COORDINATOR')")
    public Map<String, Object> quality(@PathVariable UUID id, @Valid @RequestBody TextileQualityRequest request,
                                       Authentication authentication) {
        return orders.saveQuality(id, request, currentActor.from(authentication));
    }

    @PostMapping("/orders/{id}/collection")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','DEPARTMENT_COORDINATOR','RECEPTIONIST')")
    public Map<String, Object> collection(@PathVariable UUID id, @Valid @RequestBody TextileCollectionRequest request,
                                          Authentication authentication) {
        return orders.recordCollection(id, request.collectedBy(), currentActor.from(authentication));
    }
}
