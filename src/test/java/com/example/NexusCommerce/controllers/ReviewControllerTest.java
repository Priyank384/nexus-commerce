package com.example.nexusCommerce.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.nexusCommerce.services.ReviewService;
import com.example.nexusCommerce.utils.ApiResponse;

/**
 * Every other delete in this API answers 200 with the ApiResponse envelope; review moderation
 * used to answer 204 while still writing a body, which forced the admin UI to read a response
 * Spring could have suppressed.
 */
@ExtendWith(MockitoExtension.class)
public class ReviewControllerTest {

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private ReviewController controller;

    @Test
    void deleteReview_returns200WithEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = controller.deleteReview(12L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Review deleted successfully", response.getBody().getMessage());
        assertEquals(true, response.getBody().isSuccess());
        verify(reviewService).deleteReview(12L);
    }
}
