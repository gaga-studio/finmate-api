package com.gagastudio.finmate.documentation;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OpenApiContractController {
	private static final MediaType YAML_UTF_8 = MediaType.parseMediaType("application/yaml;charset=UTF-8");
	private static final Resource CONTRACT = new ClassPathResource("static/openapi/openapi.yaml");

	@GetMapping(value = "/openapi/openapi.yaml", produces = "application/yaml")
	ResponseEntity<Resource> getOpenApiContract() {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noCache())
			.contentType(YAML_UTF_8)
			.body(CONTRACT);
	}
}
