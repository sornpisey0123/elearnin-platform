package com.pisey.elearning.service.impl;

import com.pisey.elearning.config.StorageProperties;
import com.pisey.elearning.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    private static final Set<String> IMAGES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long TWO_MB = 2 * 1024 * 1024;

    @TempDir
    private Path root;

    private LocalFileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorageService(new StorageProperties(root));
    }

    @Test
    void storesFileUnderFolderWithRandomNameAndTypeExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "../../evil.exe", "image/png", new byte[]{1, 2, 3});

        String url = storage.store(file, "avatars", IMAGES, TWO_MB);

        assertThat(url).matches("/files/avatars/[0-9a-f-]{36}\\.png");
        Path stored = root.resolve(url.substring("/files/".length()));
        assertThat(Files.readAllBytes(stored)).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsDisallowedType() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> storage.store(file, "avatars", IMAGES, TWO_MB))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("application/pdf");
    }

    @Test
    void rejectsTooLargeFile() {
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", new byte[11]);

        assertThatThrownBy(() -> storage.store(file, "avatars", IMAGES, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storage.store(file, "avatars", IMAGES, TWO_MB))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnsafeFolder() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> storage.store(file, "../outside", IMAGES, TWO_MB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRemovesStoredFile() {
        String url = storage.store(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}),
                "avatars", IMAGES, TWO_MB);

        storage.delete(url);

        assertThat(root.resolve(url.substring("/files/".length()))).doesNotExist();
    }

    @Test
    void deleteIgnoresPathsOutsideRoot() throws Exception {
        Path outside = Files.writeString(root.getParent().resolve("keep-" + root.getFileName() + ".txt"), "x");
        try {
            storage.delete("/files/../" + outside.getFileName());

            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void deleteIgnoresNullAndForeignUrls() {
        storage.delete(null);
        storage.delete("https://cdn.example.com/a.png");
    }
}
