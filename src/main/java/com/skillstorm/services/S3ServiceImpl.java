package com.skillstorm.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
public class S3ServiceImpl implements S3Service {
    private final S3Presigner presigner;
    private final String bucket;

    @Autowired
    public S3ServiceImpl(S3Presigner presigner, @Value("${BUCKET}") String bucket) {
        this.presigner = presigner;
        this.bucket = bucket;
    }

    // AWS SDK calls are all synchronous but Pre-signed Urls are lightweight and non-blocking, thus we don't
    // need to use subsribeOn(Schedulers.boundedElastic()) to reserve a synchronous thread pool. Instead we
    // can just defer the call until something subscribes to it:

    // Generate pre-signed URL to allow user upload:
    @Override
    public Mono<String> generateUploadUrl(String key, String contentType) {
        return Mono.defer(() -> {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .putObjectRequest(putRequest)
                    .signatureDuration(Duration.ofMinutes(10))
                    .build();

            String presignedUrl = presigner.presignPutObject(presignRequest).url().toString();
            return Mono.just(presignedUrl);
        });
    }

    // Generate pre-signed URL to allow user to retrieve/download file:
    @Override
    public Mono<String> generateDownloadUrl(String key) {
        return Mono.defer(() -> {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .getObjectRequest(getObjectRequest)
                    .signatureDuration(Duration.ofMinutes(10))
                    .build();

            String presignedUrl = presigner.presignGetObject(presignRequest).url().toString();
            return Mono.just(presignedUrl);
        });
    }
}
