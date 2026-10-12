package extension.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** SOLO PARA PRUEBAS: en este sandbox Google bloquea a HttpURLConnection (429) pero no a curl. build.sh --live lo engancha en Traductor.get. */
final class CurlShim {
    private CurlShim() {}

    static String get(String url, int timeoutMs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("curl", "-sS", "-m", String.valueOf(Math.max(2, timeoutMs / 1000)),
            "-A", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
            "-H", "Accept: */*", "-w", "\n%{http_code}", url);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        }
        if (!p.waitFor(timeoutMs / 1000 + 3, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("curl timeout");
        }
        String s = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        int i = s.lastIndexOf('\n');
        String code = i < 0 ? "000" : s.substring(i + 1).trim();
        if (!"200".equals(code)) throw new IOException("HTTP " + code);
        return s.substring(0, i);
    }
}
