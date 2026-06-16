package com.solo.request;

import lombok.Data;

@Data
public class FileSaveRequest {

    private String originalName;
    private String storageName;
    private String url;
    private String ext;
    private String mimeType;
    private Long size;
}
