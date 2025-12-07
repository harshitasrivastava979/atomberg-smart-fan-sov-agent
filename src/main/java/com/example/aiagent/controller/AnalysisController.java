package com.example.aiagent.controller;

import com.example.aiagent.entity.SearchResult;
import com.example.aiagent.service.AiAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    @Autowired
    private AiAnalysisService service;

    @PostMapping("/analyze")
    public List<SearchResult> analyze(
            @RequestParam String keyword,
            @RequestParam String platforms) {
        return service.analyzeKeyword(keyword, platforms);
    }

    @GetMapping("/results")
    public List<SearchResult> getAllResults() {
        return service.getAllResults();
    }
    @GetMapping("/sov/compare")
    public Map<String, Object> compareKeywords(@RequestParam List<String> keywords) {
        Map<String, Map<String, Object>> keywordComparison = new HashMap<>();

        for (String keyword : keywords) {
            List<SearchResult> results = service.getAllResults().stream()
                    .filter(r -> r.getKeyword() != null && r.getKeyword().equalsIgnoreCase(keyword))
                    .collect(Collectors.toList());

            if (!results.isEmpty()) {
                Map<String, Object> sovData = calculateShareOfVoice(results);
                keywordComparison.put(keyword, sovData);
            }
        }

        // Generate insights comparing Atomberg across keywords
        Map<String, Object> atombergComparison = new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : keywordComparison.entrySet()) {
            String keyword = entry.getKey();
            Map<String, Object> sovData = entry.getValue();

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> brandMetrics =
                    (Map<String, Map<String, Object>>) sovData.get("brandMetrics");

            if (brandMetrics != null && brandMetrics.containsKey("Atomberg")) {
                atombergComparison.put(keyword, brandMetrics.get("Atomberg"));
            }
        }

        // Calculate overall insights
        Map<String, Object> overallInsights = generateOverallInsights(atombergComparison);

        Map<String, Object> response = new HashMap<>();
        response.put("keywordComparison", keywordComparison);
        response.put("atombergAcrossKeywords", atombergComparison);
        response.put("recommendations", overallInsights);

        return response;
    }

    private Map<String, Object> generateOverallInsights(Map<String, Object> atombergComparison) {
        if (atombergComparison.isEmpty()) {
            return Map.of("message", "No Atomberg data found across keywords");
        }

        List<Map<String, Object>> recommendations = new ArrayList<>();

        // Find best performing keyword
        String bestKeyword = "";
        double highestMentionPercentage = 0.0;

        for (Map.Entry<String, Object> entry : atombergComparison.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) entry.getValue();
            double mentionPercentage = (Double) metrics.get("mentionPercentage");

            if (mentionPercentage > highestMentionPercentage) {
                highestMentionPercentage = mentionPercentage;
                bestKeyword = entry.getKey();
            }
        }

        recommendations.add(Map.of(
                "type", "Best Performing Keyword",
                "keyword", bestKeyword,
                "reason", "Atomberg has the highest share of voice (" + highestMentionPercentage + "%) for this keyword",
                "recommendation", "Focus SEO and content marketing efforts on '" + bestKeyword + "' related content"
        ));

        double avgSentimentAcrossKeywords = atombergComparison.values().stream()
                .map(v -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metrics = (Map<String, Object>) v;
                    return (Double) metrics.get("avgSentiment");
                })
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        recommendations.add(Map.of(
                "type", "Overall Sentiment",
                "score", Math.round(avgSentimentAcrossKeywords * 100) / 100.0,
                "interpretation", avgSentimentAcrossKeywords > 0.7 ? "Strong positive sentiment" : "Moderate sentiment",
                "recommendation", avgSentimentAcrossKeywords > 0.7
                        ? "Leverage positive reviews in marketing campaigns"
                        : "Focus on improving customer satisfaction and reviews"
        ));


        double avgEngagementAcrossKeywords = atombergComparison.values().stream()
                .map(v -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metrics = (Map<String, Object>) v;
                    return (Double) metrics.get("avgEngagement");
                })
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        recommendations.add(Map.of(
                "type", "Engagement Performance",
                "score", Math.round(avgEngagementAcrossKeywords * 100) / 100.0,
                "recommendation", "Create more engaging content formats (videos, infographics) to boost engagement scores"
        ));

        // Market dominance summary
        long dominantKeywords = atombergComparison.values().stream()
                .map(v -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metrics = (Map<String, Object>) v;
                    return (Double) metrics.get("mentionPercentage");
                })
                .filter(percentage -> percentage > 30.0)
                .count();

        recommendations.add(Map.of(
                "type", "Market Dominance",
                "dominantKeywords", dominantKeywords + " out of " + atombergComparison.size() + " keywords",
                "recommendation", dominantKeywords >= atombergComparison.size() / 2
                        ? "Strong market presence - focus on maintaining leadership position"
                        : "Increase content creation and SEO for underperforming keywords"
        ));

        return Map.of(
                "totalKeywordsAnalyzed", atombergComparison.size(),
                "avgSentiment", Math.round(avgSentimentAcrossKeywords * 100) / 100.0,
                "avgEngagement", Math.round(avgEngagementAcrossKeywords * 100) / 100.0,
                "recommendations", recommendations
        );
    }

    @GetMapping("/sov")
    public Map<String, Object> getShareOfVoice(@RequestParam String keyword) {
        List<SearchResult> results = service.getAllResults().stream()
                .filter(r -> r.getKeyword() != null && r.getKeyword().equalsIgnoreCase(keyword))
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            return Map.of("error", "No results found for keyword: " + keyword);
        }

        return calculateShareOfVoice(results);
    }

    private Map<String, Object> calculateShareOfVoice(List<SearchResult> results) {
        int totalResults = results.size();

        // Group by brand
        Map<String, List<SearchResult>> byBrand = results.stream()
                .filter(r -> r.getBrand() != null)
                .collect(Collectors.groupingBy(SearchResult::getBrand));

        Map<String, Map<String, Object>> brandMetrics = new HashMap<>();

        for (Map.Entry<String, List<SearchResult>> entry : byBrand.entrySet()) {
            String brand = entry.getKey();
            List<SearchResult> brandResults = entry.getValue();

            int mentions = brandResults.size();
            double mentionPercentage = (mentions * 100.0) / totalResults;

            double avgEngagement = brandResults.stream()
                    .filter(r -> r.getEngagementScore() != null)
                    .mapToDouble(SearchResult::getEngagementScore)
                    .average()
                    .orElse(0.0);

            double avgSentiment = brandResults.stream()
                    .filter(r -> r.getSentimentScore() != null)
                    .mapToDouble(SearchResult::getSentimentScore)
                    .average()
                    .orElse(0.0);

            double totalEngagement = brandResults.stream()
                    .filter(r -> r.getEngagementScore() != null)
                    .mapToDouble(SearchResult::getEngagementScore)
                    .sum();

            double avgPosition = brandResults.stream()
                    .filter(r -> r.getPosition() != null)
                    .mapToInt(SearchResult::getPosition)
                    .average()
                    .orElse(0.0);

            // Share of Positive Voice: mentions with sentiment > 0.6
            long positiveVoice = brandResults.stream()
                    .filter(r -> r.getSentimentScore() != null && r.getSentimentScore() > 0.6)
                    .count();

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("mentions", mentions);
            metrics.put("mentionPercentage", Math.round(mentionPercentage * 100) / 100.0);
            metrics.put("avgEngagement", Math.round(avgEngagement * 100) / 100.0);
            metrics.put("totalEngagement", Math.round(totalEngagement * 100) / 100.0);
            metrics.put("avgSentiment", Math.round(avgSentiment * 100) / 100.0);
            metrics.put("avgPosition", Math.round(avgPosition * 100) / 100.0);
            metrics.put("positiveVoice", positiveVoice);
            metrics.put("shareOfPositiveVoice", Math.round((positiveVoice * 100.0 / mentions) * 100) / 100.0);

            brandMetrics.put(brand, metrics);
        }

        // Overall summary
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalResults", totalResults);
        summary.put("uniqueBrands", byBrand.size());
        summary.put("brandMetrics", brandMetrics);

        // Atomberg-specific insights
        if (brandMetrics.containsKey("Atomberg")) {
            Map<String, Object> atombergMetrics = brandMetrics.get("Atomberg");
            summary.put("atombergInsights", Map.of(
                    "dominance", "Atomberg appears in " + atombergMetrics.get("mentions") + " out of " + totalResults + " results",
                    "sentiment", atombergMetrics.get("avgSentiment"),
                    "ranking", "Average position: " + atombergMetrics.get("avgPosition")
            ));
        }

        return summary;
    }
}
