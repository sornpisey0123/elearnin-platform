package com.pisey.elearning.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Stores uploaded files and returns their public URL ({@code /files/...}).
 * The local-disk implementation can be swapped for S3 without changing callers.
 */
public interface FileStorageService {

    /**
     * Validates type and size, then stores the file under {@code folder} with a random name.
     *
     * @param folder       e.g. "avatars", "thumbnails", "lessons"
     * @param allowedTypes accepted content types, e.g. {@code Set.of("image/png", "image/jpeg")}
     * @param maxBytes     maximum file size
     * @return public URL, e.g. {@code /files/avatars/4f1c….png}
     */
    String store(MultipartFile file, String folder, Set<String> allowedTypes, long maxBytes);

    /** Deletes a file previously returned by {@link #store}; ignores null, foreign or missing URLs. */
    void delete(String url);
}
