package com.mednet.api;

import java.net.URI;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiDocumentationController {

    private final OpenApiWebMvcResource openApiResource;

    public ApiDocumentationController(OpenApiWebMvcResource openApiResource) {
        this.openApiResource = openApiResource;
    }

    @GetMapping(value = "/api-docs/", produces = MediaType.APPLICATION_JSON_VALUE)
    public byte[] openApiTrailingSlash(HttpServletRequest request, Locale locale)
            throws JsonProcessingException {
        return openApiResource.openapiJson(request, "/api-docs", locale);
    }

    @GetMapping("/swagger-ui/")
    public ResponseEntity<Void> redirectSwaggerUiTrailingSlash() {
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .location(URI.create("/swagger-ui/index.html"))
                .build();
    }
}
