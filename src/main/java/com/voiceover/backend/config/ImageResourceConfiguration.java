package com.voiceover.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class ImageResourceConfiguration implements WebMvcConfigurer {
    private final String imageLocation;

    public ImageResourceConfiguration(@Value("${app.image.storage:storage/images}") String storage) {
        this.imageLocation = Path.of(storage).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations(imageLocation.endsWith("/") ? imageLocation : imageLocation + "/");
    }
}
