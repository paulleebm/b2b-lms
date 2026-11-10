package com.lms.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class CertificateRendererTest {

    private static CertificateRenderer renderer() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine(); // SpEL, as in the app (plain Thymeleaf needs OGNL)
        engine.setTemplateResolver(resolver);
        return new CertificateRenderer(engine);
    }

    @Test
    @DisplayName("수료증 PDF는 A4 가로 한 장이고, 회사·수료자·과정·수료일·수료번호·진위 확인 주소가 한글로 들어간다")
    void rendersOneLandscapePageWithEveryField() throws IOException {
        var data = new CertificateRenderer.CertificateData("데모전자", "박서연", "정보보안 기초", "2026년 11월 10일",
                "LMS-2026-XFR9FVPG", "https://app.bymile.site/verify/LMS-2026-XFR9FVPG");

        byte[] pdf = renderer().render(data);

        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            var box = document.getPage(0).getMediaBox();
            assertThat(box.getWidth()).isGreaterThan(box.getHeight());

            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", "");
            assertThat(text).contains("수료증", "데모전자", "박서연", "정보보안기초", "2026년11월10일",
                    "LMS-2026-XFR9FVPG", "https://app.bymile.site/verify/LMS-2026-XFR9FVPG");

            // To look at while changing the template: build/certificate-preview.png and .pdf
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 72);
            Path out = Path.of("build", "certificate-preview.png");
            Files.createDirectories(out.getParent());
            ImageIO.write(image, "png", out.toFile());
            Files.write(Path.of("build", "certificate-preview.pdf"), pdf);
        }
    }
}
