package com.socialcoisa.rede.media.adapters.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Pure local signing with fake credentials. No upload, AWS API call or bucket access is made. */
class S3UploadConstraintAuditTests {
    @Test
    void theUploadSizeLimitMustBeBoundToThePresignedPutInsteadOfTrustingTheDeclaredSize() {
        try (var presigner = S3Presigner.builder().region(Region.US_EAST_1)
                .endpointOverride(URI.create("https://storage.audit.invalid"))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("audit-fake-access", "audit-fake-secret"))).build()) {
            var upload = new S3UploadSigner(presigner, "audit-bucket", "uploads/")
                .sign("synthetic.png", "image/png", 128L);
            String signedHeaders = Arrays.stream(URI.create(upload.url()).getRawQuery().split("&"))
                .filter(value -> value.startsWith("X-Amz-SignedHeaders="))
                .map(value -> URLDecoder.decode(value.substring(value.indexOf('=') + 1), StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
            assertThat(upload.method()).isEqualTo("PUT");
            assertThat(signedHeaders).as("Sem tamanho assinado, o cliente pode declarar 1 KB e usar o PUT para outro tamanho; politica externa do bucket precisa de verificacao separada")
                .contains("content-length");
        }
    }
}
