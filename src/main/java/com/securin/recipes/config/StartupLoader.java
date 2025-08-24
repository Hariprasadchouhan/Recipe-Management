package com.securin.recipes.config;

import com.securin.recipes.service.RecipeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StartupLoader {
    private static final Logger log = LoggerFactory.getLogger(StartupLoader.class);

    @Bean
    CommandLineRunner dataLoader(RecipeService recipeService) {
        return args -> {
            log.info("=== Starting Recipe Application Data Loader ===");
            log.info("Command line arguments: {}", String.join(" ", args));
            
            try {
                log.info("Initiating data load process...");
                recipeService.loadDataIfNeeded();
                log.info("=== Data Loader Completed Successfully ===");
                
            } catch (Exception ex) {
                log.error("=== Data Loader Failed ===", ex);
                log.warn("Data load skipped due to error: {}", ex.getMessage());
                
                // Log additional context about the error
                if (ex.getCause() != null) {
                    log.error("Root cause: {}", ex.getCause().getMessage());
                }
                
                // Don't rethrow - let the application continue
                log.info("Application will continue without initial data load");
            }
        };
    }
}
