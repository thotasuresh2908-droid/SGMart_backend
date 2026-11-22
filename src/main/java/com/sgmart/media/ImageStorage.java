package com.sgmart.media;

import java.util.Optional;

/**
 * Where product photos live. v1 uses the local disk (LocalDiskImageStorage).
 * An S3/MinIO version can replace it later without changing any other code.
 */
public interface ImageStorage {

    /** Saves the bytes and returns a new random key such as "3f2a...e1.jpg". */
    String save(byte[] bytes, String extension);

    Optional<byte[]> load(String key);

    void delete(String key);

    /** Only keys this storage created are accepted, which also blocks "../" path tricks. */
    static boolean isValidKey(String key) {
        return key != null && key.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|webp)$");
    }
}
