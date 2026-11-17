package com.rpb.reservation.cards.voucher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class VoucherImages {
    private static final String PREFIX = "data:image/jpeg;base64,";
    private static final int MAX_BYTES = 1_000_000;

    private VoucherImages() {}

    static String validate(String image) {
        if (image == null || image.isBlank()) return null;
        if (!image.startsWith(PREFIX) || image.length() > PREFIX.length() + (MAX_BYTES + 2) / 3 * 4) {
            throw VoucherService.invalid("请上传 4:3 且不超过 1 MB 的券图");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(image.substring(PREFIX.length()));
            if (bytes.length <= MAX_BYTES) {
                try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (readers.hasNext()) {
                        var reader = readers.next();
                        try {
                            reader.setInput(input, true, true);
                            int width = reader.getWidth(0);
                            int height = reader.getHeight(0);
                            if ("JPEG".equalsIgnoreCase(reader.getFormatName()) && width > 0 && width <= 1600
                                    && height > 0 && height <= 1200 && width * 3 == height * 4
                                    && reader.read(0) != null) return image;
                        } finally {
                            reader.dispose();
                        }
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw VoucherService.invalid("券图无法读取");
        }
        throw VoucherService.invalid("请上传 4:3 且不超过 1 MB 的券图");
    }
}
