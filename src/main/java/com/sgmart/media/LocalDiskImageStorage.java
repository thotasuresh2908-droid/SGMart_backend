package com.sgmart.media;

import com.sgmart.config.SgMartProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;

/** Keeps photos in a folder (sgmart.images.dir). In production, mount that folder as a volume and back it up. */
@Component
public class LocalDiskImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskImageStorage.class);

    private final Path root;

    public LocalDiskImageStorage(SgMartProperties props) throws IOException {
        this.root = Path.of(props.imagesDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Product photos folder: {}", root);
    }

    @Override
    public String save(byte[] bytes, String extension) {
        String key = UUID.randomUUID() + "." + extension;
        try {
            Files.write(root.resolve(key), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save photo " + key, e);
        }
        return key;
    }

    @Override
    public Optional<byte[]> load(String key) {
        if (!ImageStorage.isValidKey(key)) {
            return Optional.empty();
        }
        Path file = root.resolve(key);
        try {
            return Files.exists(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read photo " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        if (!ImageStorage.isValidKey(key)) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete photo {}: {}", key, e.getMessage());
        }
    }
}
