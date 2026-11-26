package com.solo.openapi.auth;

import com.solo.openapi.entity.OpenApp;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.Set;

@Data
@AllArgsConstructor
public class OpenAppContext implements Serializable {

    private Long appId;

    private String appKey;

    private String appName;

    private Set<String> scopes;

    private OpenApp app;

    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
}
