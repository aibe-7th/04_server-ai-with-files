package org.example.ragandimage.domain.imagerag.util;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

// [Step 2-2] 이미지 리사이저 (Thumbnailator 오픈소스 활용)
@Component
public class ImageResizer {

    public byte[] resize(InputStream inputStream, String formatName, int maxDimensionPx) throws Exception {
        /*
         * [이전 직접 구현 시도 코드 흔적]
         * 오픈소스 도입 이전 java.awt.Graphics2D 기반 수동 리사이즈 방식
         *
         * BufferedImage original = ImageIO.read(inputStream);
         * int w = original.getWidth(), h = original.getHeight();
         * double scale = Math.min((double) maxDimensionPx / w, (double) maxDimensionPx / h);
         * int targetW = (int) (w * scale), targetH = (int) (h * scale);
         * Image scaled = original.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
         * BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
         * Graphics2D g2d = resized.createGraphics();
         * g2d.drawImage(scaled, 0, 0, null);
         * g2d.dispose();
         */

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(inputStream)
                .size(maxDimensionPx, maxDimensionPx)
                .outputFormat(formatName.replace("image/", ""))
                .toOutputStream(baos);
        return baos.toByteArray();
    }
}
