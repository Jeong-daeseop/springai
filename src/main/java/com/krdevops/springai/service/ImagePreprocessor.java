package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.model.design.VisionAnalysisRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImagePreprocessor {

    private final DesignVisionProperties properties;

    public List<VisionAnalysisRequest.VisionImage> preprocess(
            List<VisionAnalysisRequest.VisionImage> images) {
        List<VisionAnalysisRequest.VisionImage> result = new ArrayList<>();
        int max = properties.getMaxImagesPerRequest();
        if (images.size() > max) {
            result.add(contactSheet(images));
            for (int i = 0; i < Math.max(0, max - 1); i++) result.add(resize(images.get(i)));
        } else {
            for (VisionAnalysisRequest.VisionImage image : images) result.add(resize(image));
        }
        return result;
    }

    private VisionAnalysisRequest.VisionImage contactSheet(List<VisionAnalysisRequest.VisionImage> images) {
        try {
            int columns = Math.max(1, (int) Math.ceil(Math.sqrt(images.size())));
            int rows = (int) Math.ceil((double) images.size() / columns);
            int cellWidth = 420;
            int cellHeight = 440;
            BufferedImage sheet = new BufferedImage(
                    columns * cellWidth, rows * cellHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = sheet.createGraphics();
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            for (int i = 0; i < images.size(); i++) {
                BufferedImage source = ImageIO.read(new ByteArrayInputStream(images.get(i).content()));
                if (source == null) continue;
                double scale = Math.min(400.0 / source.getWidth(), 400.0 / source.getHeight());
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                int x = (i % columns) * cellWidth + (cellWidth - width) / 2;
                int y = (i / columns) * cellHeight + 20;
                graphics.drawImage(source, x, y, width, height, null);
                graphics.setColor(java.awt.Color.DARK_GRAY);
                graphics.drawString("page " + images.get(i).pageNumber(),
                        (i % columns) * cellWidth + 10, (i / columns) * cellHeight + 18);
            }
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(sheet, "jpg", output);
            return resize(new VisionAnalysisRequest.VisionImage(0, "image/jpeg", output.toByteArray()));
        } catch (Exception e) {
            throw new IllegalStateException("PDF contact sheet 생성 실패", e);
        }
    }

    private VisionAnalysisRequest.VisionImage resize(VisionAnalysisRequest.VisionImage image) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(image.content()));
            if (source == null) throw new IllegalArgumentException("이미지 디코딩에 실패했습니다.");
            int max = properties.getMaxImageDimension();
            if (source.getWidth() <= max && source.getHeight() <= max) return image;
            double scale = Math.min((double) max / source.getWidth(), (double) max / source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(target, "jpg", output);
            return new VisionAnalysisRequest.VisionImage(image.pageNumber(), "image/jpeg", output.toByteArray());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("이미지 전처리 실패", e);
        }
    }
}
