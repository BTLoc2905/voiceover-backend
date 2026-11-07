package com.voiceover.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class PoiThumbnailService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 24_000_000L;
    private final Path storage;

    public PoiThumbnailService(@Value("${app.image.storage:storage/images}") String storage) {
        this.storage = Path.of(storage).toAbsolutePath().normalize();
    }

    public String store(Long poiId, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) throw new IllegalArgumentException("Hãy chọn một ảnh thumbnail.");
        if (upload.getSize() > MAX_BYTES) throw new IllegalArgumentException("Ảnh thumbnail không được vượt quá 5 MB.");
        try {
            byte[] content = upload.getBytes();
            String extension = validateImage(content);
            Files.createDirectories(storage);
            String filename = "poi-" + poiId + "-" + UUID.randomUUID() + "." + extension;
            Path destination = storage.resolve(filename).normalize();
            if (!destination.startsWith(storage)) throw new IllegalStateException("Đường dẫn lưu ảnh không hợp lệ.");
            Files.write(destination, content, StandardOpenOption.CREATE_NEW);
            return "/images/" + filename;
        } catch (IOException error) {
            throw new IllegalStateException("Không thể lưu ảnh thumbnail vào kho lưu trữ.", error);
        }
    }

    private String validateImage(byte[] content) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) throw new IllegalArgumentException("Tệp tải lên không phải ảnh PNG hoặc JPEG hợp lệ.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Chỉ chấp nhận ảnh PNG hoặc JPEG hợp lệ.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg"))
                    throw new IllegalArgumentException("Chỉ chấp nhận ảnh PNG hoặc JPEG.");
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS)
                    throw new IllegalArgumentException("Kích thước ảnh vượt giới hạn 24 megapixel.");
                if (reader.read(0) == null) throw new IllegalArgumentException("Không thể giải mã ảnh tải lên.");
                return format.equals("png") ? "png" : "jpg";
            } finally {
                reader.dispose();
            }
        }
    }
}
