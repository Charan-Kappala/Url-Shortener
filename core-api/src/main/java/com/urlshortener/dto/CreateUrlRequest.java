package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;
import lombok.Data;

@Data
public class CreateUrlRequest {
    @NotBlank(message = "Original URL cannot be blank")
    @URL(message = "Original URL must be valid")
    private String originalUrl;

    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,10}$", message = "Custom code must be between 3 and 10 characters, containing only alphanumeric, underscore, and dash")
    private String customCode;

    private Integer expiresInDays;
}
