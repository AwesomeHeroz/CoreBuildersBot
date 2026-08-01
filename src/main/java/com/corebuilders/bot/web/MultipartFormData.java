package com.corebuilders.bot.web;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal strict multipart/form-data reader for a single in-memory upload request. */
final class MultipartFormData {
    private static final byte[] HEADER_SEPARATOR = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final Pattern PARAMETER = Pattern.compile("(?:^|;)\\s*([A-Za-z0-9_-]+)=\\\"([^\\\"]*)\\\"");

    private MultipartFormData() {}

    static FilePart requireFile(byte[] body, String contentType, String expectedFieldName) {
        String boundary = boundary(contentType);
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] nextDelimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
        int cursor = indexOf(body, delimiter, 0);
        if (cursor < 0) throw new IllegalArgumentException("Malformed multipart request.");

        while (cursor >= 0) {
            cursor += delimiter.length;
            if (startsWith(body, cursor, "--".getBytes(StandardCharsets.US_ASCII))) break;
            if (!startsWith(body, cursor, "\r\n".getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Malformed multipart boundary.");
            }
            cursor += 2;
            int headersEnd = indexOf(body, HEADER_SEPARATOR, cursor);
            if (headersEnd < 0 || headersEnd - cursor > MAX_HEADER_BYTES) {
                throw new IllegalArgumentException("Malformed multipart headers.");
            }
            String headers = new String(body, cursor, headersEnd - cursor, StandardCharsets.ISO_8859_1);
            int contentStart = headersEnd + HEADER_SEPARATOR.length;
            int contentEnd = indexOf(body, nextDelimiter, contentStart);
            if (contentEnd < 0) throw new IllegalArgumentException("Malformed multipart content.");

            String disposition = header(headers, "content-disposition");
            String fieldName = parameter(disposition, "name");
            if (expectedFieldName.equals(fieldName)) {
                String filename = parameter(disposition, "filename");
                if (filename == null || filename.isBlank()) filename = "upload";
                byte[] bytes = Arrays.copyOfRange(body, contentStart, contentEnd);
                if (bytes.length == 0) throw new IllegalArgumentException("The uploaded image is empty.");
                return new FilePart(filename, header(headers, "content-type"), bytes);
            }
            cursor = contentEnd + 2;
        }
        throw new IllegalArgumentException("Multipart field '" + expectedFieldName + "' is required.");
    }

    private static String boundary(String contentType) {
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            throw new IllegalArgumentException("Use multipart/form-data for image uploads.");
        }
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.toLowerCase(Locale.ROOT).startsWith("boundary=")) continue;
            String value = trimmed.substring("boundary=".length()).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            if (value.length() < 1 || value.length() > 70 || !value.matches("[A-Za-z0-9'()+_,./:=?-]+")) {
                throw new IllegalArgumentException("Invalid multipart boundary.");
            }
            return value;
        }
        throw new IllegalArgumentException("Multipart boundary is missing.");
    }

    private static String header(String headers, String expectedName) {
        for (String line : headers.split("\\r\\n")) {
            int colon = line.indexOf(':');
            if (colon < 1) continue;
            if (line.substring(0, colon).trim().equalsIgnoreCase(expectedName)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static String parameter(String header, String expectedName) {
        if (header == null) return null;
        Matcher matcher = PARAMETER.matcher(header);
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(expectedName)) return matcher.group(2);
        }
        return null;
    }

    private static int indexOf(byte[] source, byte[] target, int from) {
        if (target.length == 0) return from;
        outer:
        for (int i = Math.max(0, from); i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static boolean startsWith(byte[] source, int offset, byte[] prefix) {
        if (offset < 0 || offset + prefix.length > source.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (source[offset + i] != prefix[i]) return false;
        }
        return true;
    }

    record FilePart(String originalFilename, String declaredContentType, byte[] bytes) {}
}
