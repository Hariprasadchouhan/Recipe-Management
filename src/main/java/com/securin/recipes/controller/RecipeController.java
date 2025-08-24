package com.securin.recipes.controller;

import com.securin.recipes.model.Recipe;
import com.securin.recipes.service.RecipeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {
    private static final Logger logger = LoggerFactory.getLogger(RecipeController.class);
    
    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        logger.info("Initializing RecipeController with service: {}", recipeService.getClass().getSimpleName());
        this.recipeService = recipeService;
    }

    @GetMapping
    public Map<String, Object> getAll(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int limit) {
        logger.info("GET /api/recipes - page: {}, limit: {}", page, limit);
        
        if (page < 1) {
            logger.debug("Page parameter adjusted from {} to 1", page);
            page = 1;
        }
        if (limit < 1) {
            logger.debug("Limit parameter adjusted from {} to 10", limit);
            limit = 10;
        }
        
        Pageable pageable = PageRequest.of(page - 1, limit);
        logger.debug("Created pageable: {}", pageable);
        
        try {
            Page<Recipe> result = recipeService.getAllSortedByRating(pageable);
            logger.info("Retrieved {} recipes out of {} total for page {} with limit {}", 
                       result.getContent().size(), result.getTotalElements(), page, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("page", page);
            response.put("limit", limit);
            response.put("total", result.getTotalElements());
            response.put("data", result.getContent());
            
            logger.debug("Response prepared with {} recipes", result.getContent().size());
            return response;
            
        } catch (Exception e) {
            logger.error("Error retrieving recipes for page {} with limit {}", page, limit, e);
            throw e;
        }
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(required = false) String calories,
                                      @RequestParam(required = false) String title,
                                      @RequestParam(required = false) String cuisine,
                                      @RequestParam(name = "total_time", required = false) String totalTime,
                                      @RequestParam(required = false) String rating) {
        logger.info("GET /api/recipes/search - calories: {}, title: {}, cuisine: {}, totalTime: {}, rating: {}", 
                   calories, title, cuisine, totalTime, rating);
        
        Double minRating = null;
        Double maxRating = null;
        if (rating != null && !rating.isBlank()) {
            if (rating.startsWith(">=")) {
                minRating = parseDoubleSafe(rating.substring(2).trim());
                logger.debug("Parsed rating >= {}", minRating);
            } else if (rating.startsWith("<=")) {
                maxRating = parseDoubleSafe(rating.substring(2).trim());
                logger.debug("Parsed rating <= {}", maxRating);
            } else if (rating.startsWith("=")) {
                minRating = maxRating = parseDoubleSafe(rating.substring(1).trim());
                logger.debug("Parsed rating = {}", minRating);
            }
        }
        
        Integer maxTotalTime = null;
        if (totalTime != null && !totalTime.isBlank()) {
            if (totalTime.startsWith("<=")) {
                maxTotalTime = parseIntSafe(totalTime.substring(2).trim());
                logger.debug("Parsed totalTime <= {}", maxTotalTime);
            } else if (totalTime.startsWith("=")) {
                maxTotalTime = parseIntSafe(totalTime.substring(1).trim());
                logger.debug("Parsed totalTime = {}", maxTotalTime);
            }
        }
        
        logger.debug("Search criteria - minRating: {}, maxRating: {}, maxTotalTime: {}", minRating, maxRating, maxTotalTime);
        
        try {
            List<Recipe> recipes = recipeService.search(cuisine, title, minRating, maxRating, maxTotalTime);
            logger.info("Search returned {} recipes", recipes.size());
            
            Map<String, Object> response = new HashMap<>();
            response.put("data", recipes);
            return response;
            
        } catch (Exception e) {
            logger.error("Error during search with criteria - cuisine: {}, title: {}, minRating: {}, maxRating: {}, maxTotalTime: {}", 
                        cuisine, title, minRating, maxRating, maxTotalTime, e);
            throw e;
        }
    }

    @GetMapping("/load")
    public ResponseEntity<?> triggerLoad() throws IOException {
        logger.info("GET /api/recipes/load - Triggering data load");
        
        try {
            recipeService.loadDataIfNeeded();
            logger.info("Data load completed successfully");
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Data loaded successfully"));
            
        } catch (Exception e) {
            logger.error("Error during data load", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", "Failed to load data: " + e.getMessage()));
        }
    }

    private Double parseDoubleSafe(String s) {
        try { 
            Double result = Double.parseDouble(s);
            logger.trace("Successfully parsed double: {}", result);
            return result;
        } catch (Exception e) { 
            logger.debug("Failed to parse double from '{}': {}", s, e.getMessage());
            return null; 
        }
    }

    private Integer parseIntSafe(String s) {
        try { 
            Integer result = Integer.parseInt(s);
            logger.trace("Successfully parsed integer: {}", result);
            return result; 
        } catch (Exception e) { 
            logger.debug("Failed to parse integer from '{}': {}", s, e.getMessage());
            return null; 
        }
    }
}
