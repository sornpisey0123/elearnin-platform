package com.pisey.elearning.service.impl;

import com.pisey.elearning.config.StorageProperties;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores files on local disk under {@code app.storage.root}; {@code WebConfig} serves them at {@code /files/**}.
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    public static final String URL_PREFIX = "/files/";

    private static final Pattern FOLDER = Pattern.compile("[a-z0-9-]+");

    /** Extension is derived from the validated content type, never from the client's file name. */
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/gif", "gif"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/zip", "zip"),
            Map.entry("text/plain", "txt"),
            Map.entry("video/mp4", "mp4"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx")
    );

    private final Path root;

    public LocalFileStorageService(StorageProperties storageProperties) {
        this.root = storageProperties.root().toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file, String folder, Set<String> allowedTypes, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File is too large; maximum is %d bytes".formatted(maxBytes));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (!allowedTypes.contains(contentType) || extension == null) {
            throw new BadRequestException("Unsupported file type: %s; allowed: %s"
                    .formatted(StringUtils.hasText(contentType) ? contentType : "unknown", allowedTypes));
        }
        if (!FOLDER.matcher(folder).matches()) {
            throw new IllegalArgumentException("Invalid storage folder: " + folder);
        }

        String fileName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(folder).resolve(fileName);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store file " + fileName, ex);
        }
        return URL_PREFIX + folder + "/" + fileName;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        Path target = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        // Refuse anything that escapes the storage root, e.g. "/files/../secret"
        if (!target.startsWith(root)) {
            log.warn("Refusing to delete file outside storage root: {}", url);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            // A leftover file is not worth failing the request for
            log.warn("Could not delete file {}", target, ex);
        }
    }
}
