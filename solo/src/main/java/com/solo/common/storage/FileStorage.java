package com.solo.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorage {

    String upload(MultipartFile file, String storageName);

    String upload(InputStream in, long size, String contentType, String storageName);

    InputStream openStream(String storageName);

    String presignedUrl(String storageName, int expirySeconds);

    void delete(String storageName);
}
