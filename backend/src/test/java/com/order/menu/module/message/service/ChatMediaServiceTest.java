package com.order.menu.module.message.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import com.order.menu.module.message.entity.ChatMessage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class ChatMediaServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesValidatedImageInsideConversationDirectory() throws Exception {
        ChatMediaService service = configuredService();
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);

        ChatMediaService.StoredImage stored = service.store(42L,
                new MockMultipartFile("image", "photo.png", "image/png", bytes.toByteArray()));

        assertTrue(Path.of(stored.storagePath()).startsWith(tempDir.resolve("42")));
        assertTrue(Files.exists(Path.of(stored.storagePath())));
        assertTrue(stored.url().startsWith("/api/messages/conversations/42/images/"));
        assertTrue(stored.expiresAt().getHour() == 3);
        assertTrue(stored.expiresAt().isAfter(java.time.LocalDateTime.now().plusDays(30)));
    }

    @Test
    void rejectsBytesThatAreNotAnImage() {
        ChatMediaService service = configuredService();
        assertThrows(BusinessException.class, () -> service.store(42L,
                new MockMultipartFile("image", "fake.jpg", "image/jpeg", "not-image".getBytes())));
    }

    @Test
    void cleanupDeletesExpiredFileAndItsNowEmptyConversationDirectory() throws Exception {
        ChatMessageMapper mapper = mock(ChatMessageMapper.class);
        ChatMediaService service = configuredService(mapper);
        Path directory = Files.createDirectories(tempDir.resolve("42"));
        Path image = directory.resolve("expired.jpg");
        Files.write(image, new byte[]{1, 2, 3});
        ChatMessage message = new ChatMessage();
        message.setId(9L);
        message.setType("IMAGE_SHARE");
        message.setImageUrl("/message-chat-images/42/expired.jpg");
        message.setImageStoragePath(image.toString());
        message.setImageExpiresAt(java.time.LocalDateTime.now().minusSeconds(1));
        when(mapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of(message));

        service.deleteExpiredImages();

        assertTrue(Files.notExists(image));
        assertTrue(Files.notExists(directory));
        assertTrue(message.getImageUrl() == null);
        verify(mapper).updateById(message);
    }

    @Test
    void expirationBoundaryStartsExactlyAtExpiresAt() {
        ChatMessage message = new ChatMessage();
        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.of(2026, 8, 1, 3, 0);
        message.setImageExpiresAt(expiresAt);

        assertTrue(!ChatMediaService.isExpiredAt(message, expiresAt.minusNanos(1)));
        assertTrue(ChatMediaService.isExpiredAt(message, expiresAt));
    }

    private ChatMediaService configuredService() {
        return configuredService(mock(ChatMessageMapper.class));
    }

    private ChatMediaService configuredService(ChatMessageMapper mapper) {
        ChatMediaService service = new ChatMediaService(mapper);
        ReflectionTestUtils.setField(service, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "");
        ReflectionTestUtils.setField(service, "maxOriginalBytes", 20L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "compressionThreshold", 5L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "maxPixels", 40_000_000L);
        return service;
    }
}
