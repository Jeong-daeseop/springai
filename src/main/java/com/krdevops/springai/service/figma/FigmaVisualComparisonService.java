package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * R7-002/R7-015/R7-T04: Figma 렌더 이미지와 원본/생성 이미지의 결정론적 비교.
 *
 * <p>PNG와 JPEG를 모두 ImageIO로 읽으며, 이미지 크기가 다르면 자동 보정하지 않고
 * 실패시킨다. 크기 보정은 화면의 viewport·Frame 차이를 숨길 수 있으므로 별도 Gate로
 * 취급한다. 동일 크기 이미지에서는 채널 차이가 10%를 넘는 픽셀의 비율을 계산한다.
 */
@Service
public class FigmaVisualComparisonService {
    public static final double DEFAULT_MAX_DIFFERENCE_RATIO = 0.001d;
    private static final int CHANNEL_THRESHOLD = 26; // pixelmatch threshold 0.1에 대응하는 근사값

    public Comparison compare(Path referenceImage, Path candidateImage, Path diffImage) {
        return compare(referenceImage, candidateImage, diffImage, DEFAULT_MAX_DIFFERENCE_RATIO);
    }

    public Comparison compare(
            Path referenceImage,
            Path candidateImage,
            Path diffImage,
            double maxDifferenceRatio) {
        if (referenceImage == null || candidateImage == null) {
            throw new IllegalArgumentException("referenceImage와 candidateImage는 필수입니다.");
        }
        if (!Double.isFinite(maxDifferenceRatio) || maxDifferenceRatio < 0 || maxDifferenceRatio > 1) {
            throw new IllegalArgumentException("maxDifferenceRatio는 0과 1 사이여야 합니다.");
        }

        try {
            BufferedImage reference = read(referenceImage);
            BufferedImage candidate = read(candidateImage);
            boolean sameSize = reference.getWidth() == candidate.getWidth()
                    && reference.getHeight() == candidate.getHeight();
            if (!sameSize) {
                writeDiffIfRequested(diffImage, candidate, reference);
                return new Comparison(
                        Status.FAILED,
                        1.0d,
                        reference.getWidth(), reference.getHeight(),
                        candidate.getWidth(), candidate.getHeight(),
                        "이미지 크기가 다릅니다. viewport 또는 Frame 크기를 먼저 일치시켜야 합니다.");
            }

            BufferedImage diff = diffImage == null ? null
                    : new BufferedImage(reference.getWidth(), reference.getHeight(), BufferedImage.TYPE_INT_ARGB);
            long changedPixels = 0;
            int totalPixels = reference.getWidth() * reference.getHeight();
            for (int y = 0; y < reference.getHeight(); y++) {
                for (int x = 0; x < reference.getWidth(); x++) {
                    int referenceRgb = reference.getRGB(x, y);
                    int candidateRgb = candidate.getRGB(x, y);
                    boolean changed = differs(referenceRgb, candidateRgb);
                    if (changed) changedPixels++;
                    if (diff != null) {
                        diff.setRGB(x, y, changed
                                ? new Color(220, 38, 38, 220).getRGB()
                                : new Color(255, 255, 255, 0).getRGB());
                    }
                }
            }
            double ratio = totalPixels == 0 ? 0 : (double) changedPixels / totalPixels;
            if (diff != null) writeDiff(diffImage, diff);
            Status status = ratio <= maxDifferenceRatio ? Status.PASSED : Status.FAILED;
            String message = status == Status.PASSED ? null
                    : "시각 차이 비율 " + ratio + "이 허용치 " + maxDifferenceRatio + "를 초과했습니다.";
            return new Comparison(status, ratio,
                    reference.getWidth(), reference.getHeight(),
                    candidate.getWidth(), candidate.getHeight(), message);
        } catch (IOException exception) {
            return new Comparison(Status.INFRA_ERROR, null, 0, 0, 0, 0,
                    "이미지 비교에 실패했습니다: " + exception.getMessage());
        }
    }

    private BufferedImage read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("이미지 파일이 없습니다: " + path);
        }
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("지원하지 않는 이미지 형식입니다: " + path);
        return image;
    }

    private boolean differs(int left, int right) {
        return Math.abs(((left >>> 24) & 0xff) - ((right >>> 24) & 0xff)) > CHANNEL_THRESHOLD
                || Math.abs(((left >>> 16) & 0xff) - ((right >>> 16) & 0xff)) > CHANNEL_THRESHOLD
                || Math.abs(((left >>> 8) & 0xff) - ((right >>> 8) & 0xff)) > CHANNEL_THRESHOLD
                || Math.abs((left & 0xff) - (right & 0xff)) > CHANNEL_THRESHOLD;
    }

    private void writeDiffIfRequested(Path path, BufferedImage candidate, BufferedImage reference) throws IOException {
        if (path == null) return;
        BufferedImage diff = new BufferedImage(
                Math.max(candidate.getWidth(), reference.getWidth()),
                Math.max(candidate.getHeight(), reference.getHeight()),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < diff.getHeight(); y++) {
            for (int x = 0; x < diff.getWidth(); x++) {
                boolean inCandidate = x < candidate.getWidth() && y < candidate.getHeight();
                boolean inReference = x < reference.getWidth() && y < reference.getHeight();
                diff.setRGB(x, y, inCandidate && inReference && !differs(reference.getRGB(x, y), candidate.getRGB(x, y))
                        ? new Color(255, 255, 255, 0).getRGB()
                        : new Color(220, 38, 38, 220).getRGB());
            }
        }
        writeDiff(path, diff);
    }

    private void writeDiff(Path path, BufferedImage diff) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        if (!ImageIO.write(diff, "png", path.toFile())) {
            throw new IOException("diff PNG를 저장하지 못했습니다: " + path);
        }
    }

    public enum Status { PASSED, FAILED, INFRA_ERROR }

    public record Comparison(
            Status status,
            Double differenceRatio,
            int referenceWidth,
            int referenceHeight,
            int candidateWidth,
            int candidateHeight,
            String message) { }
}
