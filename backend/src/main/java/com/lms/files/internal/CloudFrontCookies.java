package com.lms.files.internal;

import com.lms.files.MediaLibrary.SignedCookie;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.cookie.CookiesForCustomPolicy;
import software.amazon.awssdk.services.cloudfront.model.CustomSignerRequest;

/**
 * CloudFront signed cookies for HLS (docs/05 §8). An HLS video is a playlist plus hundreds of
 * segment files referenced by relative paths, so per-file signed URLs cannot work. One custom
 * policy — "this video's folder, for two hours" — covers every segment.
 *
 * <p>Path-scoped ({@code /tenants/{t}/videos/{id}/}): a cookie for one video opens nothing else,
 * least of all another company's files. Set by the API's response through the BFF with
 * {@code Domain=.<parent domain>} so the browser sends it to media.&lt;domain&gt;.
 */
final class CloudFrontCookies {

    static final Duration TTL = Duration.ofHours(2);

    private final CloudFrontUtilities utilities = CloudFrontUtilities.create();
    private final String mediaBaseUrl;
    private final String keyPairId;
    private final PrivateKey privateKey;
    private final String cookieDomain;

    CloudFrontCookies(String mediaBaseUrl, String keyPairId, String privateKeyPem, String cookieDomain) {
        this.mediaBaseUrl = mediaBaseUrl.replaceAll("/+$", "");
        this.keyPairId = keyPairId;
        this.privateKey = parsePkcs8(privateKeyPem);
        this.cookieDomain = cookieDomain;
    }

    /** @param folder e.g. tenants/{t}/videos/{id}/hls/ */
    List<SignedCookie> forFolder(String folder, Instant now) {
        Instant expires = now.plus(TTL);
        CookiesForCustomPolicy cookies = utilities.getCookiesForCustomPolicy(CustomSignerRequest.builder()
                .resourceUrl(mediaBaseUrl + "/" + folder + "*")
                .keyPairId(keyPairId)
                .privateKey(privateKey)
                .expirationDate(expires)
                .build());
        String path = "/" + folder;
        return List.of(
                cookie(cookies.policyHeaderValue(), path, expires),
                cookie(cookies.signatureHeaderValue(), path, expires),
                cookie(cookies.keyPairIdHeaderValue(), path, expires));
    }

    String url(String key) {
        return mediaBaseUrl + "/" + key;
    }

    /** The SDK returns "Name=value"; split into a cookie the controller turns into Set-Cookie. */
    private SignedCookie cookie(String headerValue, String path, Instant expires) {
        int eq = headerValue.indexOf('=');
        return new SignedCookie(headerValue.substring(0, eq), headerValue.substring(eq + 1), cookieDomain, path, expires);
    }

    static PrivateKey parsePkcs8(String pem) {
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new IllegalStateException("CloudFront private key must be an RSA key in PKCS#8 PEM", e);
        }
    }
}
