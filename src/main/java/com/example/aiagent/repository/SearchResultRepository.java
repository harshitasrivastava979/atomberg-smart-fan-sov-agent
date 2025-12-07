package com.example.aiagent.repository;

import com.example.aiagent.entity.SearchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchResultRepository extends JpaRepository<SearchResult, Long> {
    List<SearchResult> findByKeyword(String keyword);
}
