package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.model.design.VisionAnalysisRequest;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PdfPageRasterizer {

    private final DesignVisionProperties properties;

    public List<VisionAnalysisRequest.VisionImage> rasterize(Path pdfPath, String pageRange) {
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(pdfPath))) {
            List<Integer> pages = parsePageRange(pageRange, document.getNumberOfPages());
            if (pages.size() > properties.getMaxPdfPages()) {
                throw new IllegalArgumentException("PDF 페이지 제한을 초과했습니다: " + properties.getMaxPdfPages());
            }
            PDFRenderer renderer = new PDFRenderer(document);
            List<VisionAnalysisRequest.VisionImage> result = new ArrayList<>();
            for (int page : pages) {
                BufferedImage image = renderer.renderImageWithDPI(page - 1, properties.getRenderDpi(), ImageType.RGB);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                result.add(new VisionAnalysisRequest.VisionImage(page, "image/png", output.toByteArray()));
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("PDF 페이지 이미지 변환 실패: " + pdfPath, e);
        }
    }

    List<Integer> parsePageRange(String pageRange, int pageCount) {
        if (pageCount <= 0) return List.of();
        if (pageRange == null || pageRange.isBlank()) {
            int end = Math.min(pageCount, properties.getMaxPdfPages());
            List<Integer> pages = new ArrayList<>();
            for (int i = 1; i <= end; i++) pages.add(i);
            return pages;
        }
        Set<Integer> pages = new LinkedHashSet<>();
        for (String token : pageRange.split(",")) {
            String value = token.trim();
            if (value.matches("\\d+")) {
                addPage(pages, Integer.parseInt(value), pageCount);
            } else if (value.matches("\\d+\\s*-\\s*\\d+")) {
                String[] range = value.split("-");
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());
                if (start > end) throw new IllegalArgumentException("PDF 페이지 범위가 역순입니다: " + value);
                for (int page = start; page <= end; page++) addPage(pages, page, pageCount);
            } else {
                throw new IllegalArgumentException("PDF 페이지 범위 형식이 잘못됐습니다: " + value);
            }
        }
        return List.copyOf(pages);
    }

    private void addPage(Set<Integer> pages, int page, int pageCount) {
        if (page < 1 || page > pageCount) {
            throw new IllegalArgumentException("PDF 페이지가 범위를 벗어났습니다: " + page + "/" + pageCount);
        }
        pages.add(page);
    }
}
