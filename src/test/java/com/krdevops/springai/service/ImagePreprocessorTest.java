package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.model.design.VisionAnalysisRequest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePreprocessorTest {

    @Test
    void manyPdfPagesBecomeContactSheetAndLimitedDetails() throws Exception {
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.setMaxImagesPerRequest(3);
        ImagePreprocessor preprocessor = new ImagePreprocessor(properties);
        List<VisionAnalysisRequest.VisionImage> pages = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            pages.add(new VisionAnalysisRequest.VisionImage(page, "image/png", image()));
        }

        List<VisionAnalysisRequest.VisionImage> result = preprocessor.preprocess(pages);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).pageNumber()).isZero();
        assertThat(result).extracting(VisionAnalysisRequest.VisionImage::pageNumber)
                .containsExactly(0, 1, 2);
    }

    private byte[] image() throws Exception {
        BufferedImage image = new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
