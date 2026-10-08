package com.sebu.backend.auth.adapter;

import com.sebu.backend.auth.config.SejongClientProperties;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.TlsVersion;
import org.conscrypt.Conscrypt;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.sebu.backend.auth.port.SejongAuthenticationException.systemUnavailable;

@Component
public class SejongHttpClientFactory {
    static final TlsVersion SEJONG_TLS_VERSION = TlsVersion.TLS_1_2;
    static final CipherSuite SEJONG_LEGACY_CIPHER_SUITE =
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA;

    private final OkHttpClient baseHttpClient;
    private final Set<Origin> allowedOrigins;

    public SejongHttpClientFactory(SejongClientProperties properties) {
        TlsMaterial tlsMaterial = createTlsMaterial();
        this.allowedOrigins = Set.copyOf(List.of(
            Origin.from(properties.portalLoginUrl()),
            Origin.from(properties.portalLoginPageUrl()),
            Origin.from(properties.portalSsoLoginUrl()),
            Origin.from(properties.ssoLoginUrl()),
            Origin.from(properties.userInfoUrl())
        ));

        ConnectionSpec modernTls12 = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(SEJONG_TLS_VERSION)
            .build();
        ConnectionSpec legacyTls12 = new ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .tlsVersions(SEJONG_TLS_VERSION)
            .cipherSuites(SEJONG_LEGACY_CIPHER_SUITE)
            .build();
        List<ConnectionSpec> connectionSpecs = allowedOrigins.stream()
            .allMatch(origin -> "https".equals(origin.scheme()))
            ? List.of(modernTls12, legacyTls12)
            : List.of(modernTls12, legacyTls12, ConnectionSpec.CLEARTEXT);

        this.baseHttpClient = new OkHttpClient.Builder()
            .sslSocketFactory(tlsMaterial.sslContext().getSocketFactory(), tlsMaterial.trustManager())
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(properties.connectTimeout())
            .readTimeout(properties.requestTimeout())
            .callTimeout(properties.requestTimeout())
            .connectionSpecs(connectionSpecs)
            .protocols(List.of(Protocol.HTTP_1_1))
            .addNetworkInterceptor(chain -> {
                requireAllowedOrigin(chain.request().url());
                return chain.proceed(chain.request());
            })
            .build();
    }

    public Session create() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        OkHttpClient httpClient = baseHttpClient.newBuilder()
            .cookieJar(new JavaNetCookieJar(cookieManager))
            .build();
        return new Session(httpClient, cookieManager);
    }

    private void requireAllowedOrigin(HttpUrl url) throws IOException {
        if (allowedOrigins.stream().noneMatch(origin -> origin.matches(url))) {
            throw new IOException("The school server redirected to an unapproved origin");
        }
    }

    private TlsMaterial createTlsMaterial() {
        try {
            Provider provider = Conscrypt.newProviderBuilder()
                .provideTrustManager(true)
                .build();
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm(),
                provider
            );
            trustManagerFactory.init((KeyStore) null);
            X509TrustManager trustManager = Arrays.stream(trustManagerFactory.getTrustManagers())
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .findFirst()
                .orElseThrow(() -> new GeneralSecurityException("X509 trust manager is unavailable"));
            SSLContext sslContext = SSLContext.getInstance("TLS", provider);
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            return new TlsMaterial(sslContext, trustManager);
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw systemUnavailable(exception);
        }
    }

    public record Session(OkHttpClient httpClient, CookieManager cookieManager) {
    }

    private record TlsMaterial(SSLContext sslContext, X509TrustManager trustManager) {
    }

    private record Origin(String scheme, String host, int port) {
        private static Origin from(URI uri) {
            int effectivePort = uri.getPort() >= 0
                ? uri.getPort()
                : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            return new Origin(
                uri.getScheme().toLowerCase(Locale.ROOT),
                uri.getHost().toLowerCase(Locale.ROOT),
                effectivePort
            );
        }

        private boolean matches(HttpUrl url) {
            return scheme.equals(url.scheme()) && host.equals(url.host()) && port == url.port();
        }
    }
}
