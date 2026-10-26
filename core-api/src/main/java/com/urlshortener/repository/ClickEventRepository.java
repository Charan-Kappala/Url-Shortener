package com.urlshortener.repository;

import com.urlshortener.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    @Query("SELECT COUNT(DISTINCT CAST(c.clickedAt AS LocalDate)) FROM ClickEvent c WHERE c.shortCode = :shortCode")
    Long countUniqueDaysByShortCode(String shortCode);

    @Query("""
            SELECT c.referer, COUNT(c) AS cnt FROM ClickEvent c
            WHERE c.shortCode = :shortCode AND c.referer IS NOT NULL
            GROUP BY c.referer ORDER BY cnt DESC LIMIT 10""")
    List<Object[]> findTopReferersByShortCode(String shortCode);

    @Query("""
            SELECT c.deviceType, COUNT(c) AS cnt FROM ClickEvent c
            WHERE c.shortCode = :shortCode
            GROUP BY c.deviceType ORDER BY cnt DESC""")
    List<Object[]> findTopDevicesByShortCode(String shortCode);

    @Query("""
            SELECT CAST(c.clickedAt AS LocalDate) AS day, COUNT(c) AS cnt FROM ClickEvent c
            WHERE c.shortCode = :shortCode
              AND c.clickedAt BETWEEN :from AND :to
            GROUP BY day ORDER BY day""")
    List<Object[]> findClicksByDay(String shortCode, ZonedDateTime from, ZonedDateTime to);

    Long countByShortCode(String shortCode);
}
