package com.gagastudio.finmate.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ApiProblems {
	private final ObjectMapper objectMapper;

	public ApiProblems(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public ProblemDetail create(HttpServletRequest request, HttpStatus status, String type, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("https://api.finmate.kr/problems/" + type));
		problem.setTitle(title);
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		problem.setProperty("traceId", UUID.randomUUID().toString());
		return problem;
	}

	public ProblemDetail validation(HttpServletRequest request, List<Map<String, String>> fieldErrors) {
		ProblemDetail problem = create(request, HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
			"Request validation failed", "VALIDATION_FAILED");
		problem.setProperty("fieldErrors", fieldErrors);
		return problem;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
		String type, String title, String detail, String code) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), create(request, status, type, title, detail, code));
	}
}
