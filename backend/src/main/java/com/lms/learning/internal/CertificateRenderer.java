package com.lms.learning.internal;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Certificate PDF: a Thymeleaf template (HTML/CSS, previewable in a browser) rendered by
 * OpenHTMLtoPDF with Pretendard embedded and subset (only the glyphs used, so the file stays small).
 *
 * <p>Why not iText: it is AGPL — using it in a SaaS obliges publishing the source or buying a
 * licence. OpenHTMLtoPDF is LGPL on top of Apache PDFBox.
 */
@Component
class CertificateRenderer {

    record CertificateData(String companyName, String holderName, String courseTitle, String issuedDate,
                           String serial, String verifyUrl) {
    }

    private final ITemplateEngine templates;

    CertificateRenderer(ITemplateEngine templates) {
        this.templates = templates;
    }

    byte[] render(CertificateData data) {
        Context context = new Context(Locale.KOREAN);
        context.setVariable("c", data);
        String html = templates.process("certificate", context);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.useFont(() -> getClass().getResourceAsStream("/fonts/Pretendard-Regular.ttf"), "Pretendard", 400,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> getClass().getResourceAsStream("/fonts/Pretendard-Bold.ttf"), "Pretendard", 700,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
