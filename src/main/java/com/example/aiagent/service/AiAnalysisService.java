package com.example.aiagent.service;

import com.example.aiagent.entity.SearchResult;
import com.example.aiagent.repository.SearchResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Arrays;
import java.util.List;

@Service
public class AiAnalysisService {

    @Autowired
    private SearchResultRepository repository;

    public List<SearchResult> analyzeKeyword(String keyword, String platforms) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "/Users/harshitasrivastava/Downloads/demo/venv/bin/python",
                    "python/ai_analyzer.py",
                    keyword,
                    platforms,
                    "10"
            );
            pb.directory(new File("."));

            // Separate stdout and stderr
            Process process = pb.start();

            // Read stdout (JSON output)
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Read stderr (warnings/errors)
            BufferedReader errorReader =
                    new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder output = new StringBuilder();
            StringBuilder errors = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                // Only capture lines that look like JSON (start with [ or {)
                if (line.trim().startsWith("[") || line.trim().startsWith("{")) {
                    output.append(line);
                }
            }

            // Read errors separately
            while ((line = errorReader.readLine()) != null) {
                errors.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.err.println("Python stderr: " + errors.toString());
                throw new RuntimeException("Python script failed with code: " + exitCode);
            }

            // Log warnings but don't fail
            if (errors.length() > 0) {
                System.out.println("Python warnings: " + errors.toString());
            }

            String jsonOutput = output.toString().trim();

            if (jsonOutput.isEmpty()) {
                throw new RuntimeException("No JSON output from Python script");
            }

            System.out.println("JSON output: " + jsonOutput);

            ObjectMapper mapper = new ObjectMapper();
            SearchResult[] results =
                    mapper.readValue(jsonOutput, SearchResult[].class);

            for (SearchResult result : results) {
                result.setKeyword(keyword);
                repository.save(result);
            }

            return Arrays.asList(results);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Analysis failed: " + e.getMessage(), e);
        }
    }

    public List<SearchResult> getAllResults() {
        return repository.findAll();
    }
}