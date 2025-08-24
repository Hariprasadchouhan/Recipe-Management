package com.securin.recipes.repository;

import com.securin.recipes.model.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    Page<Recipe> findAllByOrderByRatingDesc(Pageable pageable);

    @Query("SELECT r FROM Recipe r WHERE " +
            "(:cuisine IS NULL OR r.cuisine = :cuisine) AND " +
            "(:title IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
            "(:minRating IS NULL OR r.rating >= :minRating) AND " +
            "(:maxRating IS NULL OR r.rating <= :maxRating) AND " +
            "(:maxTotalTime IS NULL OR r.totalTime <= :maxTotalTime)")
    List<Recipe> search(
            @Param("cuisine") String cuisine,
            @Param("title") String title,
            @Param("minRating") Double minRating,
            @Param("maxRating") Double maxRating,
            @Param("maxTotalTime") Integer maxTotalTime
    );
}
