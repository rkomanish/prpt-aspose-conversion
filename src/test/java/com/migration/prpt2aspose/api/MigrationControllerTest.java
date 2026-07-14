package com.migration.prpt2aspose.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives the HTTP surface end-to-end on a random port: upload a real sample .prpt, then download the generated template. */
@SpringBootTest(
        classes = com.migration.prpt2aspose.cli.Prpt2AsposeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MigrationControllerTest {

    private static Path tempOutputDir;

    @DynamicPropertySource
    static void isolateOutputDir(DynamicPropertyRegistry registry) throws Exception {
        tempOutputDir = Files.createTempDirectory("prpt2aspose-api-test");
        registry.add("prpt2aspose.output-dir", () -> tempOutputDir.toString());
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void uploadConvertsAndArtifactsAreDownloadable() {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(Path.of("src/test/resources/samples/customer-orders.prpt")));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<MigrationResponse> response =
                rest.postForEntity("/api/migrations", new HttpEntity<>(form, headers), MigrationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        MigrationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.reportName()).isEqualTo("Customer Orders Report");
        assertThat(body.downloadUrls()).containsKeys("template", "queries", "mapping", "migrationReport");
        assertThat(tempOutputDir.resolve("customer-orders/template.xlsx")).exists();

        ResponseEntity<byte[]> template =
                rest.getForEntity(body.downloadUrls().get("template"), byte[].class);
        assertThat(template.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(template.getBody()).isNotEmpty();

        ResponseEntity<String> queries =
                rest.getForEntity(body.downloadUrls().get("queries"), String.class);
        assertThat(queries.getBody()).contains("SELECT", "${fromDate}");
    }

    @Test
    void rejectsNonPrptUpload() {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(Path.of("pom.xml")));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> response =
                rest.postForEntity("/api/migrations", new HttpEntity<>(form, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownArtifactIs404() {
        ResponseEntity<String> response =
                rest.getForEntity("/api/migrations/customer-orders/secrets.txt", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
