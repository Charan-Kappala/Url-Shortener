package com.urlshortener.repository;

import com.urlshortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);

    /** Returns the number of rows changed: 0 if the code does not exist or is already inactive. */
    @Transactional
    @Modifying
    @Query("UPDATE UrlMapping u SET u.isActive = false WHERE u.shortCode = :shortCode AND u.isActive = true")
    int deactivate(String shortCode);
}
