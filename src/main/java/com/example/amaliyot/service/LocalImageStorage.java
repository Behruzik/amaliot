package com.example.amaliyot.service;

import com.example.amaliyot.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Locale;

@Service
public class LocalImageStorage {
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private final Path root;

    public LocalImageStorage(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("INVALID_IMAGE_SIZE", "Image must be between 1 byte and 3 MB");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw ApiException.badRequest("INVALID_IMAGE_SIZE", "Image exceeds the 3 MB limit");
            String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
            String extension;
            if (type.equals("image/png") && bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) extension = ".png";
            else if (type.equals("image/jpeg") && bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) extension = ".jpg";
            else if (type.equals("image/webp") && bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) extension = ".webp";
            else throw ApiException.badRequest("INVALID_IMAGE_TYPE", "Only PNG, JPEG, and WebP images are accepted");
            Files.createDirectories(root);
            String name = UUID.randomUUID() + extension;
            Path target = root.resolve(name).normalize();
            if (!target.getParent().equals(root)) throw ApiException.badRequest("INVALID_FILE_NAME", "Invalid file name");
            Files.write(target, bytes);
            return "/uploads/" + name;
        } catch (IOException exception) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_SAVE_FAILED", "Image could not be saved");
        }
    }

    public Path getRoot() { return root; }
}
