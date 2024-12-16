package com.skillstorm.services;

import reactor.core.publisher.Mono;

public interface S3Service {

    // Generate pre-signed URL to allow user upload:
    Mono<String> generateUploadUrl(String key, String contentType);

    // Generate pre-signed URL to allow user to retrieve/download file:
    Mono<String> generateDownloadUrl(String key);
}
