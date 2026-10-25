package com.voiceover.backend.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PoiThumbnailServiceTest {
    @TempDir Path storage;

    @Test
    void savesVerifiedPngWithGeneratedNameAndServableUrl() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB), "png", bytes);
        PoiThumbnailService service = new PoiThumbnailService(storage.toString());

        String url = service.store(42L, new MockMultipartFile("file", "unsafe-name.png", "image/png", bytes.toByteArray()));

        assertTrue(url.matches("/images/poi-42-[0-9a-f-]+\\.png"));
        assertTrue(Files.isRegularFile(storage.resolve(url.substring("/images/".length()))));
    }

    @Test
    void rejectsForgedImageTypeAndOversizedUpload() {
        PoiThumbnailService service = new PoiThumbnailService(storage.toString());
        var forged = new MockMultipartFile("file", "image.png", "image/png", "not an image".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.store(1L, forged));

        var oversized = new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertThrows(IllegalArgumentException.class, () -> service.store(1L, oversized));
    }
}
