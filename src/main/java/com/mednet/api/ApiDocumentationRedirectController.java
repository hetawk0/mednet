package com.mednet.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiDocumentationRedirectController {

    @GetMapping("/api-docs/")
    public ResponseEntity<Void> redirectApiDocsTrailingSlash() {
        return redirect("/api-docs");
    }

    @GetMapping("/swagger-ui/")
    public ResponseEntity<Void> redirectSwaggerUiTrailingSlash() {
        return redirect("/swagger-ui");
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .location(URI.create(location))
                .build();
    }
}
