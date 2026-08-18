package com.thz.spectrum.storage;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * S3 兼容对象存储（Cloudflare R2）。
 * 配置：app.storage.type=s3 + app.storage.s3.* 各项
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    @Value("${app.storage.s3.endpoint:}") private String endpoint;
    @Value("${app.storage.s3.access-key:}") private String accessKey;
    @Value("${app.storage.s3.secret-key:}") private String secretKey;
    @Value("${app.storage.s3.bucket:}") private String bucket;
    @Value("${app.storage.s3.public-base-url:}") private String publicBaseUrl;

    private MinioClient client;

    @PostConstruct
    void init() {
        if (isBlank(endpoint) || isBlank(accessKey) || isBlank(secretKey) || isBlank(bucket)) {
            throw new IllegalStateException(
                    "S3 存储配置不完整：请设置 R2_ENDPOINT / R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY / R2_BUCKET");
        }
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public String store(String category, String filename, InputStream in, long size) throws IOException {
        String key = category + "/" + System.currentTimeMillis() + "_" + sanitize(filename);
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(in, size, -1)
                    .contentType(contentType(filename))
                    .build());
        } catch (Exception e) {
            throw new IOException("对象存储写入失败: " + e.getMessage(), e);
        }
        return baseUrl() + "/" + key;
    }

    @Override
    public InputStream open(String storedRef) throws IOException {
        String key = toKey(storedRef);
        try {
            GetObjectResponse resp = client.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(key).build());
            return resp;
        } catch (Exception e) {
            throw new IOException("对象存储读取失败: " + e.getMessage(), e);
        }
    }

    private String baseUrl() {
        return publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    private String toKey(String ref) {
        if (ref == null) return "";
        if (ref.startsWith("http://") || ref.startsWith("https://")) {
            String base = baseUrl();
            if (!base.isBlank() && ref.startsWith(base + "/")) {
                return ref.substring(base.length() + 1);
            }
            return ref;
        }
        return ref.replaceFirst("^/+", "");
    }

    private String contentType(String name) {
        if (name == null) return "application/octet-stream";
        String l = name.toLowerCase();
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".gif")) return "image/gif";
        if (l.endsWith(".webp")) return "image/webp";
        if (l.endsWith(".txt")) return "text/plain";
        if (l.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private String sanitize(String name) {
        if (name == null) return "file";
        String b = name.replace('\\', '/');
        int idx = b.lastIndexOf('/');
        if (idx >= 0) b = b.substring(idx + 1);
        b = b.replaceAll("[^A-Za-z0-9._-]", "_");
        if (b.isBlank()) b = "file";
        return b.length() > 100 ? b.substring(b.length() - 100) : b;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
