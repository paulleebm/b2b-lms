package com.lms.learning.internal;

import com.lms.shared.web.ApiException;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CertificateController {

    private final CertificateService certificates;

    CertificateController(CertificateService certificates) {
        this.certificates = certificates;
    }

    /** A 5-minute download link (409 CERTIFICATE_NOT_READY while the worker is still rendering). */
    @GetMapping("/v1/my/enrollments/{id}/certificate")
    CertificateService.CertificateLink mine(@PathVariable UUID id) {
        return certificates.myCertificate(id);
    }

    /**
     * Public: anyone with the serial printed on a certificate can check it is genuine (no sign-in).
     * Returns only what a verifier needs, with the holder's name masked.
     */
    @GetMapping("/v1/public/certificates/{serial}")
    CertificateService.Verification verify(@PathVariable String serial) {
        if (!serial.matches("^LMS-\\d{4}-[0-9A-HJKMNP-TV-Z]{8}$")) {
            throw ApiException.notFound("CERTIFICATE_NOT_FOUND", "확인할 수 없는 수료번호입니다.");
        }
        return certificates.verify(serial)
                .orElseThrow(() -> ApiException.notFound("CERTIFICATE_NOT_FOUND", "확인할 수 없는 수료번호입니다."));
    }
}
