package com.krdevops.springai.service.figma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaVisualComparisonServiceTest {
    @TempDir Path tempDir;

    private final FigmaVisualComparisonService service = new FigmaVisualComparisonService();

    @Test
    void comparesPngAndJpegAtTheSameViewport() throws Exception {
        Path reference = image("reference.png", 20, 10, Color.WHITE);
        Path candidate = image("candidate.jpg", 20, 10, Color.WHITE);

        var result = service.compare(reference, candidate, tempDir.resolve("diff.png"));

        assertThat(result.status()).isEqualTo(FigmaVisualComparisonService.Status.PASSED);
        assertThat(result.differenceRatio()).isLessThanOrEqualTo(.001d);
        assertThat(Files.isRegularFile(tempDir.resolve("diff.png"))).isTrue();
    }

    @Test
    void emitsDifferenceRatioAndDiffForChangedPixels() throws Exception {
        Path reference = image("reference.png", 10, 10, Color.WHITE);
        Path candidate = image("candidate.png", 10, 10, Color.WHITE);
        BufferedImage changed = ImageIO.read(candidate.toFile());
        changed.setRGB(0, 0, Color.BLACK.getRGB());
        ImageIO.write(changed, "png", candidate.toFile());

        var result = service.compare(reference, candidate, tempDir.resolve("diff.png"), 0);

        assertThat(result.status()).isEqualTo(FigmaVisualComparisonService.Status.FAILED);
        assertThat(result.differenceRatio()).isEqualTo(.01d);
        assertThat(Files.isRegularFile(tempDir.resolve("diff.png"))).isTrue();
    }

    @Test
    void rejectsViewportOrFrameSizeMismatchWithoutResizing() throws Exception {
        Path reference = image("reference.png", 10, 10, Color.WHITE);
        Path candidate = image("candidate.png", 11, 10, Color.WHITE);

        var result = service.compare(reference, candidate, tempDir.resolve("diff.png"));

        assertThat(result.status()).isEqualTo(FigmaVisualComparisonService.Status.FAILED);
        assertThat(result.differenceRatio()).isEqualTo(1.0d);
        assertThat(result.message()).contains("크기가 다릅니다");
    }

    private Path image(String name, int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, color.getRGB());
        }
        Path path = tempDir.resolve(name);
        ImageIO.write(image, name.endsWith(".jpg") ? "jpg" : "png", path.toFile());
        return path;
    }
}
