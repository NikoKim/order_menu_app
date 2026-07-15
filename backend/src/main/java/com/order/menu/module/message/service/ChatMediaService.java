package com.order.menu.module.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.message.entity.ChatMessage;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ChatMediaService {
    private static final Semaphore IMAGE_PROCESSING_SLOTS = new Semaphore(2);
    private final ChatMessageMapper chatMessageMapper;

    @Value("${app.chat-media.base-path:/www/server/SpringBoot/order_menu_image/message_chat}")
    private String basePath;
    @Value("${app.chat-media.public-base-url:}")
    private String publicBaseUrl;
    @Value("${app.chat-media.max-original-bytes:20971520}")
    private long maxOriginalBytes;
    @Value("${app.chat-media.compress-threshold-bytes:5242880}")
    private long compressionThreshold;
    @Value("${app.chat-media.max-pixels:12000000}")
    private long maxPixels;

    public StoredImage store(Long conversationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择图片");
        }
        if (file.getSize() > maxOriginalBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片原文件不能超过20MB");
        }
        if (!IMAGE_PROCESSING_SLOTS.tryAcquire()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片处理繁忙，请稍后重试");
        }
        try {
            byte[] bytes = file.getBytes();
            DecodedImage decoded = decodeValidatedImage(bytes);
            String extension = decoded.extension();
            BufferedImage image = decoded.image();
            if (bytes.length > compressionThreshold) {
                bytes = compressJpeg(image);
                extension = "jpg";
            }
            Path directory = Path.of(basePath).resolve(String.valueOf(conversationId)).normalize();
            Path root = Path.of(basePath).normalize();
            if (!directory.startsWith(root)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "会话目录无效");
            }
            Files.createDirectories(directory);
            String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            Path target = directory.resolve(name);
            Files.write(target, bytes);
            String relativeUrl = "/api/messages/conversations/" + conversationId + "/images/" + name;
            String url = publicBaseUrl == null || publicBaseUrl.isBlank()
                    ? relativeUrl : publicBaseUrl.replaceAll("/$", "") + relativeUrl;
            return new StoredImage(url, target.toString(), nextCleanupAt(nowShanghai()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "图片保存失败");
        } finally {
            IMAGE_PROCESSING_SLOTS.release();
        }
    }

    public MediaResource load(Long conversationId, String filename) {
        if (filename == null || !filename.matches("[a-fA-F0-9]{32}\\.(jpg|jpeg|png|webp)")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        Path root = Path.of(basePath).normalize();
        Path file = root.resolve(String.valueOf(conversationId)).resolve(filename).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在或已过期");
        }
        try {
            String contentType = Files.probeContentType(file);
            return new MediaResource(new FileSystemResource(file),
                    contentType == null ? "application/octet-stream" : contentType);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在或已过期");
        }
    }

    public void discard(StoredImage image) {
        if (image == null || image.storagePath() == null) return;
        Path root = Path.of(basePath).normalize();
        Path file = Path.of(image.storagePath()).normalize();
        if (!file.startsWith(root)) return;
        try {
            Files.deleteIfExists(file);
            deleteParentIfEmpty(file, root);
        } catch (IOException ignored) {
            // The daily cleanup also removes any empty conversation directory.
        }
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void deleteExpiredImages() {
        LocalDateTime now = nowShanghai();
        List<ChatMessage> expired = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .isNotNull(ChatMessage::getImageStoragePath)
                .le(ChatMessage::getImageExpiresAt, now));
        for (ChatMessage message : expired.stream().filter(message -> isExpiredAt(message, now)).toList()) {
            Path file = Path.of(message.getImageStoragePath()).normalize();
            Path root = Path.of(basePath).normalize();
            try {
                if (file.startsWith(root)) Files.deleteIfExists(file);
                deleteParentIfEmpty(file, root);
            } catch (IOException ignored) {
                continue;
            }
            message.setImageUrl(null);
            message.setImageStoragePath(null);
            chatMessageMapper.updateById(message);
        }
    }

    private DecodedImage decodeValidatedImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不是有效图片");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持该图片格式");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!List.of("jpg", "jpeg", "png", "webp").contains(format)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持该图片格式");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "图片尺寸过大");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不是有效图片");
                return new DecodedImage(image, "jpeg".equals(format) ? "jpg" : format);
            } finally {
                reader.dispose();
            }
        }
    }

    private LocalDateTime nextCleanupAt(LocalDateTime uploadedAt) {
        LocalDateTime threshold = uploadedAt.plusDays(30);
        LocalDateTime cleanup = threshold.toLocalDate().atTime(LocalTime.of(3, 0));
        return cleanup.isBefore(threshold) ? cleanup.plusDays(1) : cleanup;
    }

    private void deleteParentIfEmpty(Path file, Path root) throws IOException {
        Path parent = file.getParent();
        if (parent != null && parent.startsWith(root) && Files.isDirectory(parent)) {
            try (var children = Files.list(parent)) {
                if (children.findAny().isEmpty()) Files.deleteIfExists(parent);
            }
        }
    }

    private byte[] compressJpeg(BufferedImage image) throws IOException {
        BufferedImage current = image;
        byte[] encoded = encodeJpeg(current, 0.82f);
        while (encoded.length > compressionThreshold && (current.getWidth() > 640 || current.getHeight() > 640)) {
            int width = Math.max(1, (int) (current.getWidth() * 0.82));
            int height = Math.max(1, (int) (current.getHeight() * 0.82));
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var graphics = scaled.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(current, 0, 0, width, height, java.awt.Color.WHITE, null);
            graphics.dispose();
            current = scaled;
            encoded = encodeJpeg(current, 0.78f);
        }
        return encoded;
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = rgb.createGraphics();
        graphics.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        graphics.dispose();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(rgb, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private LocalDateTime nowShanghai() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    static boolean isExpiredAt(ChatMessage message, LocalDateTime now) {
        return message.getImageExpiresAt() != null && !message.getImageExpiresAt().isAfter(now);
    }

    public record StoredImage(String url, String storagePath, LocalDateTime expiresAt) {
    }

    public record MediaResource(Resource resource, String contentType) {
    }

    private record DecodedImage(BufferedImage image, String extension) {
    }
}
