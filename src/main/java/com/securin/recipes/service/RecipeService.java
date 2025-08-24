package com.securin.recipes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.securin.recipes.model.Recipe;

import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import com.securin.recipes.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.InputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class RecipeService {
    private static final Logger logger = LoggerFactory.getLogger(RecipeService.class);
    
    private final RecipeRepository recipeRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.data.load:true}")
    private boolean loadData;

    @Value("${app.data.file:../data/US_recipes.json}")
    private String dataFilePath;

    public RecipeService(RecipeRepository recipeRepository) {
        logger.info("Initializing RecipeService with repository: {}", recipeRepository.getClass().getSimpleName());
        this.recipeRepository = recipeRepository;
        // Create ObjectMapper with NaN handling enabled
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());
        logger.info("ObjectMapper configured with NaN handling enabled");
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Recipe> getAllSortedByRating(org.springframework.data.domain.Pageable pageable) {
        logger.debug("Fetching recipes sorted by rating with pageable: {}", pageable);
        org.springframework.data.domain.Page<Recipe> result = recipeRepository.findAllByOrderByRatingDesc(pageable);
        logger.debug("Retrieved {} recipes out of {} total", result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Recipe> search(String cuisine, String title, Double minRating, Double maxRating, Integer maxTotalTime) {
        logger.debug("Searching recipes with criteria - cuisine: {}, title: {}, minRating: {}, maxRating: {}, maxTotalTime: {}", 
                    cuisine, title, minRating, maxRating, maxTotalTime);
        List<Recipe> result = recipeRepository.search(cuisine, title, minRating, maxRating, maxTotalTime);
        logger.debug("Search returned {} recipes", result.size());
        return result;
    }

    @Transactional
    public void loadDataIfNeeded() throws IOException {
        logger.info("Checking if data loading is needed. loadData: {}, current count: {}", loadData, recipeRepository.count());
    
        if (!loadData) {
            logger.info("Data loading is disabled via configuration");
            return;
        }
    
        if (recipeRepository.count() > 0) {
            logger.info("Data already loaded, skipping. Current count: {}", recipeRepository.count());
            return;
        }
    
        // Load from classpath
        ClassPathResource resource = new ClassPathResource("data/US_recipes.json");
    
        if (!resource.exists()) {
            logger.warn("Data file not found in classpath: data/recipes.json");
            return;
        }
    
        logger.info("Starting data loading from classpath file: {}", resource.getFilename());
    
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            logger.info("Successfully parsed JSON with {} root fields", root.size());
    
            List<Recipe> toSave = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            int processedCount = 0;
            int skippedCount = 0;
    
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode node = entry.getValue();
    
                try {
                    Recipe r = new Recipe();
                    r.setCuisine(getText(node, "cuisine"));
                    r.setTitle(getText(node, "title"));
                    r.setRating(getDoubleOrNull(node, "rating"));
                    r.setPrepTime(getIntOrNull(node, "prep_time"));
                    r.setCookTime(getIntOrNull(node, "cook_time"));
                    r.setTotalTime(getIntOrNull(node, "total_time"));
                    r.setDescription(getText(node, "description"));
    
                    JsonNode nutrients = node.get("nutrients");
                    r.setNutrients(nutrients == null || nutrients.isNull() ? null : nutrients.toString());
                    r.setServes(getText(node, "serves"));
    
                    toSave.add(r);
                    processedCount++;
    
                    if (processedCount % 1000 == 0) {
                        logger.debug("Processed {} recipes so far", processedCount);
                    }
    
                } catch (Exception e) {
                    logger.warn("Failed to process recipe at index {}, skipping. Error: {}", processedCount + skippedCount, e.getMessage());
                    skippedCount++;
                }
            }
    
            logger.info("Processing complete. Valid recipes: {}, Skipped: {}", processedCount, skippedCount);
    
            if (!toSave.isEmpty()) {
                logger.info("Saving {} recipes to database", toSave.size());
                List<Recipe> savedRecipes = recipeRepository.saveAll(toSave);
                logger.info("Successfully saved {} recipes to database", savedRecipes.size());
            } else {
                logger.warn("No valid recipes to save");
            }
    
        } catch (Exception e) {
            logger.error("Failed to load data from classpath file: data/recipes.json", e);
            throw e;
        }
    }
    

    private String getText(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) {
            logger.trace("Field '{}' is null or missing", field);
            return null;
        }
        String result = val.asText();
        logger.trace("Field '{}' = '{}'", field, result);
        return result;
    }

    private Double getDoubleOrNull(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) {
            logger.trace("Field '{}' is null or missing", field);
            return null;
        }
        
        try {
            if (val.isNumber()) {
                double d = val.asDouble();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    logger.debug("Field '{}' contains NaN or infinite value: {}", field, d);
                    return null;
                }
                logger.trace("Field '{}' = {} (number)", field, d);
                return d;
            }
            
            String s = val.asText();
            if (s == null) {
                logger.trace("Field '{}' text is null", field);
                return null;
            }
            
            double d = Double.parseDouble(s);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                logger.debug("Field '{}' parsed to NaN or infinite value: {}", field, d);
                return null;
            }
            logger.trace("Field '{}' = {} (parsed from text)", field, d);
            return d;
            
        } catch (Exception e) {
            logger.debug("Failed to parse field '{}' as double: {}", field, e.getMessage());
            return null;
        }
    }

    private Integer getIntOrNull(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) {
            logger.trace("Field '{}' is null or missing", field);
            return null;
        }
        
        try {
            if (val.isNumber()) {
                int result = val.asInt();
                logger.trace("Field '{}' = {} (number)", field, result);
                return result;
            }
            
            String s = val.asText();
            int result = Integer.parseInt(s);
            logger.trace("Field '{}' = {} (parsed from text)", field, result);
            return result;
            
        } catch (Exception e) {
            logger.debug("Failed to parse field '{}' as integer: {}", field, e.getMessage());
            return null;
        }
    }
}
