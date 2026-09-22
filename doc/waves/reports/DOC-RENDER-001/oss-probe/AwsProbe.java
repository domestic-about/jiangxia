import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.Upload;

import java.net.URI;
import java.time.Duration;

public class AwsProbe {
    public static void main(String[] args) throws Exception {
        S3AsyncClient client = S3AsyncClient.builder()
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ruoyi", "ruoyi123")))
            .endpointOverride(URI.create("http://127.0.0.1:9000"))
            .region(Region.US_EAST_1)

            .httpClient(NettyNioAsyncHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(60))
                .connectionAcquisitionTimeout(Duration.ofSeconds(30))
                .maxConcurrency(100).maxPendingConnectionAcquires(1000).build())
            .serviceConfiguration(S3Configuration.builder().chunkedEncodingEnabled(false).pathStyleAccessEnabled(true).build())
            .build();

        long t0 = System.currentTimeMillis();
        try {
            var resp = client.putObject(
                b -> b.bucket("ruoyi").key("lqg-probe-async.txt"),
                AsyncRequestBody.fromBytes("hello-async".getBytes())).get();
            System.out.println("[A] putObject(fromBytes) OK " + resp.eTag() + " in " + (System.currentTimeMillis()-t0) + "ms");
        } catch (Throwable e) {
            System.out.println("[A] putObject FAILED in " + (System.currentTimeMillis()-t0) + "ms: " + e);
        }

        long t1 = System.currentTimeMillis();
        try {
            S3TransferManager tm = S3TransferManager.builder().s3Client(client).build();
            Upload up = tm.upload(b -> b.requestBody(AsyncRequestBody.fromBytes("hello-tm".getBytes()))
                .putObjectRequest(p -> p.bucket("ruoyi").key("lqg-probe-tm.txt").build()));
            System.out.println("[B] transferManager OK " + up.completionFuture().get().response().eTag() + " in " + (System.currentTimeMillis()-t1) + "ms");
        } catch (Throwable e) {
            System.out.println("[B] transferManager FAILED in " + (System.currentTimeMillis()-t1) + "ms: " + e);
        }
        System.exit(0);
    }
}
