import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.properties.OssProperties;

public class OssProbe {
    public static void main(String[] args) throws Exception {
        String endpoint = args.length > 0 ? args[0] : "127.0.0.1:9000";
        String bucket = args.length > 1 ? args[1] : "ruoyi";
        String ak = args.length > 2 ? args[2] : "ruoyi";
        String sk = args.length > 3 ? args[3] : "ruoyi123";
        OssProperties p = new OssProperties();
        p.setEndpoint(endpoint);
        p.setBucketName(bucket);
        p.setAccessKey(ak);
        p.setSecretKey(sk);
        p.setPrefix("");
        p.setIsHttps("N");
        p.setRegion("");
        long t0 = System.currentTimeMillis();
        System.out.println("[probe] endpoint=" + endpoint + " bucket=" + bucket);
        try {
            OssClient c = new OssClient("probe", p);
            System.out.println("[probe] client built in " + (System.currentTimeMillis() - t0) + "ms");
            UploadResult r = c.uploadSuffix("hello-lqg-doc-render".getBytes(), ".txt", "text/plain");
            System.out.println("[probe] OK url=" + r.getUrl() + " in " + (System.currentTimeMillis() - t0) + "ms");
        } catch (Throwable e) {
            System.out.println("[probe] FAILED in " + (System.currentTimeMillis() - t0) + "ms: " + e);
            Throwable cause = e.getCause();
            while (cause != null) { System.out.println("[probe]   caused by: " + cause); cause = cause.getCause(); }
        }
        System.exit(0);
    }
}
