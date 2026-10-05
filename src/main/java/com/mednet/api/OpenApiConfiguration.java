package com.mednet.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "MedNet API",
        version = "v1",
        description = "REST API for MedNet authentication and administration workflows."))
public class OpenApiConfiguration {
}
