package com.sgmart.media;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Serves product photos. Keys never change (a new photo gets a new key), so browsers can cache them for a year. */
@RestController
public class ImageController {

    private final ImageStorage storage;

    public ImageController(ImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/v1/images/{key}")
    public ResponseEntity<byte[]> get(@PathVariable String key) {
        return storage.load(key)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(key.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(bytes))
                .orElse(ResponseEntity.notFound().build());
    }
}
