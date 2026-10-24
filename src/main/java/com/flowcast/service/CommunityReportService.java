package com.flowcast.service;

import com.flowcast.dto.CommunityReportDto;
import com.flowcast.dto.CommunityReportRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CommunityReportService {
    private final Optional<JdbcTemplate> jdbcTemplate;
    private final AtomicLong idSequence = new AtomicLong(100);
    private final Map<Long, CommunityReportDto> inMemoryStore = new ConcurrentHashMap<>();
    private final Map<String, Instant> rateLimitMap = new ConcurrentHashMap<>();

    public CommunityReportService(Optional<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public synchronized CommunityReportDto submitReport(CommunityReportRequest request, String clientIp) {
        // Simple rate limiting: max 1 report every 5 seconds per client
        String key = clientIp != null ? clientIp : "anonymous";
        Instant now = Instant.now();
        Instant lastSubmission = rateLimitMap.get(key);
        if (lastSubmission != null && now.isBefore(lastSubmission.plusSeconds(5))) {
            throw new IllegalArgumentException("Submissions are rate-limited. Please wait a few seconds before submitting another report.");
        }
        rateLimitMap.put(key, now);

        long id = idSequence.incrementAndGet();
        CommunityReportDto report = new CommunityReportDto(
                id,
                request.reportType().trim(),
                request.description().trim(),
                request.latitude(),
                request.longitude(),
                "REPORTED",
                now,
                null,
                "Community Submission (Unverified)"
        );

        inMemoryStore.put(id, report);

        // Best-effort database insertion if table exists
        jdbcTemplate.ifPresent(jdbc -> {
            try {
                jdbc.update("INSERT INTO community_reports (report_type, description, latitude, longitude, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                        report.reportType(), report.description(), report.latitude(), report.longitude(), report.status(), java.sql.Timestamp.from(report.createdAt()));
            } catch (Exception ignored) {
                // Table might not exist or database is offline; in-memory fallback preserves data safely
            }
        });

        return report;
    }

    public List<CommunityReportDto> listReports() {
        List<CommunityReportDto> list = new ArrayList<>(inMemoryStore.values());
        list.sort(Comparator.comparing(CommunityReportDto::createdAt).reversed());
        return list;
    }

    public Optional<CommunityReportDto> getReport(Long id) {
        return Optional.ofNullable(inMemoryStore.get(id));
    }

    public boolean deleteReport(Long id) {
        boolean removed = inMemoryStore.remove(id) != null;
        jdbcTemplate.ifPresent(jdbc -> {
            try {
                jdbc.update("DELETE FROM community_reports WHERE id = ?", id);
            } catch (Exception ignored) {}
        });
        return removed;
    }

    public void clearAll() {
        inMemoryStore.clear();
        rateLimitMap.clear();
        jdbcTemplate.ifPresent(jdbc -> {
            try {
                jdbc.update("DELETE FROM community_reports");
            } catch (Exception ignored) {}
        });
    }
}
