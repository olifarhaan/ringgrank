package com.ringgrank.controller;

import com.ringgrank.dto.ScoreSubmissionRequest;
import com.ringgrank.service.ScoreIngestionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handling score submissions.
 * All IDs (userId, gameId) are expected to be numeric.
 */
@RestController
@RequestMapping("/api/v1/scores")
public class ScoreController {

    private final ScoreIngestionService scoreIngestionService;

    /**
     * Constructor for ScoreController.
     * @param scoreIngestionService Service to handle score processing.
     */
    @Autowired
    public ScoreController(ScoreIngestionService scoreIngestionService) {
        this.scoreIngestionService = scoreIngestionService;
    }

    /**
     * Endpoint to submit a player's score.
     * Validates the submission request before processing.
     * @param scoreSubmissionRequest The score details with numeric IDs.
     * @return ResponseEntity indicating acceptance (HTTP 202).
     */
    @PostMapping
    public ResponseEntity<Void> submitScore(@Valid @RequestBody ScoreSubmissionRequest scoreSubmissionRequest) {
        // The @Valid annotation triggers Bean Validation on the ScoreSubmissionRequest DTO.
        // Validation rules (e.g., @NotNull, @Min) are defined within the DTO itself.
        scoreIngestionService.processScore(scoreSubmissionRequest);
        // HTTP 202 Accepted is returned to indicate the request has been accepted for processing,
        // which might involve asynchronous operations like WAL writes.
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
} 