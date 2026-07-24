package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.ApplicationConfig.Question;
import com.corebuilders.bot.model.Models.ApplicationFile;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Preserves application uploads without loading entire attachments into the Paper JVM heap. */
public final class ApplicationUploadPreserver {
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private final HttpClient httpClient;
    private final long maxFileSizeBytes;
    private final long maxTotalSizeBytes;
    private final ApplicationTextFormatter formatter;

    public ApplicationUploadPreserver(
            long maxFileSizeBytes,
            long maxTotalSizeBytes,
            ApplicationTextFormatter formatter
    ) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxTotalSizeBytes = maxTotalSizeBytes;
        this.formatter = formatter;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public List<ApplicationFile> preserve(
            TextChannel pending,
            UUID applicationId,
            Question question,
            List<Message.Attachment> attachments
    ) throws Exception {
        if (attachments == null || attachments.isEmpty()) return List.of();

        long declaredTotal = attachments.stream().mapToLong(Message.Attachment::getSize).sum();
        if (declaredTotal > maxTotalSizeBytes) {
            throw new IllegalArgumentException(
                    "Upload exceeds the configured total application upload limit of "
                            + formatter.humanBytes(maxTotalSizeBytes) + "."
            );
        }

        List<Path> tempFiles = new ArrayList<>();
        List<FileUpload> uploads = new ArrayList<>();
        long actualTotal = 0L;
        try {
            for (Message.Attachment attachment : attachments) {
                if (attachment.getSize() > maxFileSizeBytes) {
                    throw new IllegalArgumentException(
                            "Attachment '" + attachment.getFileName() + "' exceeds the configured application upload limit of "
                                    + formatter.humanBytes(maxFileSizeBytes) + "."
                    );
                }

                URI uri = URI.create(attachment.getUrl());
                validateDiscordAttachmentUri(uri);
                Path temp = Files.createTempFile("corebuilders-application-", ".upload");
                tempFiles.add(temp);

                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(45))
                        .GET()
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream input = response.body()) {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Could not preserve uploaded file '" + attachment.getFileName() + "'.");
                    }
                    long copied = copyBounded(input, temp, maxFileSizeBytes);
                    actualTotal = Math.addExact(actualTotal, copied);
                    if (actualTotal > maxTotalSizeBytes) {
                        throw new IllegalArgumentException(
                                "Upload exceeds the configured total application upload limit of "
                                        + formatter.humanBytes(maxTotalSizeBytes) + "."
                        );
                    }
                }
                uploads.add(FileUpload.fromData(temp, formatter.safeFileName(attachment.getFileName())));
            }

            Message evidence = pending.sendFiles(uploads)
                    .setContent("**Application `" + applicationId + "` — Upload: "
                            + formatter.escapeMarkdown(question.label()) + "**")
                    .complete();

            return evidence.getAttachments().stream()
                    .map(attachment -> new ApplicationFile(
                            question.id(),
                            question.label(),
                            attachment.getFileName(),
                            attachment.getContentType(),
                            attachment.getSize(),
                            attachment.getUrl(),
                            evidence.getId()
                    ))
                    .toList();
        } finally {
            for (FileUpload upload : uploads) {
                try { upload.close(); } catch (Exception ignored) { }
            }
            for (Path path : tempFiles) {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            }
        }
    }

    static void validateDiscordAttachmentUri(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean trustedHost = host.equals("cdn.discordapp.com")
                || host.equals("media.discordapp.net");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !trustedHost || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Refusing to download an attachment from an untrusted URL.");
        }
    }

    private static long copyBounded(InputStream input, Path target, long maxBytes) throws Exception {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long total = 0L;
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total = Math.addExact(total, read);
                if (total > maxBytes) {
                    throw new IllegalArgumentException("Downloaded attachment exceeded the configured per-file size limit.");
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }
}
