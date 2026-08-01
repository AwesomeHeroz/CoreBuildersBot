package com.corebuilders.bot.web;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stores validated marketplace images outside the plugin JAR and resolves immutable public files safely. */
final class MarketplaceImageStorage {
    private static final Pattern OWNER_DIRECTORY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{32}\\.(png|jpg|gif)");
    private static final long MAX_PIXELS = 20_000_000L;
    private static final int MAX_DIMENSION = 8192;

    private final Path root;
    private final URI publicBase;
    private final int maxFileBytes;

    MarketplaceImageStorage(Path root, URI publicBase, int maxFileBytes) throws IOException {
        Path configuredRoot = root.toAbsolutePath().normalize();
        Files.createDirectories(configuredRoot);
        this.root = configuredRoot.toRealPath();
        this.publicBase = publicBase;
        this.maxFileBytes = maxFileBytes;
    }

    StoredImage store(UUID ownerMemberId, MultipartFormData.FilePart upload) throws IOException {
        byte[] bytes = upload.bytes();
        if (bytes.length > maxFileBytes) {
            throw new ImageTooLargeException("Image exceeds the " + maxFileBytes + " byte upload limit.");
        }
        ImageType type = ImageType.detect(bytes)
                .orElseThrow(() -> new IllegalArgumentException("Only PNG, JPEG, and GIF images are supported."));
        validateDimensions(bytes, type);

        String owner = ownerMemberId.toString().toLowerCase(Locale.ROOT);
        Path ownerDirectory = root.resolve(owner).normalize();
        if (!ownerDirectory.startsWith(root)) throw new IllegalStateException("Invalid image storage path.");
        Files.createDirectories(ownerDirectory);
        if (Files.isSymbolicLink(ownerDirectory) || !ownerDirectory.toRealPath().equals(ownerDirectory)) {
            throw new IllegalStateException("Image owner directory cannot be a symbolic link.");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + '.' + type.extension;
        Path destination = ownerDirectory.resolve(filename);
        Path temporary = Files.createTempFile(ownerDirectory, ".upload-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        URI url = publicBase.resolve(owner + "/" + filename);
        return new StoredImage(url.toASCIIString(), type.contentType, bytes.length);
    }

    Optional<StoredFile> resolve(String relativePath) throws IOException {
        String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
        String[] parts = normalized.split("/", -1);
        if (parts.length != 2 || !OWNER_DIRECTORY.matcher(parts[0]).matches()
                || !FILE_NAME.matcher(parts[1]).matches()) {
            return Optional.empty();
        }
        Path ownerDirectory = root.resolve(parts[0]).normalize();
        Path file = ownerDirectory.resolve(parts[1]).normalize();
        if (!file.startsWith(root)
                || !Files.isDirectory(ownerDirectory, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        Path realFile = file.toRealPath();
        if (!realFile.getParent().equals(ownerDirectory) || !realFile.startsWith(root)) return Optional.empty();
        String contentType = contentType(parts[1]);
        return Optional.of(new StoredFile(realFile, contentType, Files.size(realFile)));
    }

    private static void validateDimensions(byte[] bytes, ImageType expectedType) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IllegalArgumentException("The uploaded file is not a readable image.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("The uploaded file is not a readable image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!expectedType.matchesFormat(format)) {
                    throw new IllegalArgumentException("The image contents do not match the file format.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION || pixels > MAX_PIXELS) {
                    throw new IllegalArgumentException("Image dimensions are too large.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static String contentType(String filename) {
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private enum ImageType {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        GIF("gif", "image/gif");

        private final String extension;
        private final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        static Optional<ImageType> detect(byte[] bytes) {
            if (bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                    && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
                return Optional.of(PNG);
            }
            if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff) {
                return Optional.of(JPEG);
            }
            if (bytes.length >= 6) {
                String signature = new String(bytes, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
                if (signature.equals("GIF87a") || signature.equals("GIF89a")) return Optional.of(GIF);
            }
            return Optional.empty();
        }

        boolean matchesFormat(String format) {
            return switch (this) {
                case PNG -> format.equals("png");
                case JPEG -> format.equals("jpeg") || format.equals("jpg");
                case GIF -> format.equals("gif");
            };
        }
    }

    record StoredImage(String url, String contentType, int size) {}
    record StoredFile(Path path, String contentType, long size) {}

    static final class ImageTooLargeException extends IllegalArgumentException {
        ImageTooLargeException(String message) { super(message); }
    }
}
