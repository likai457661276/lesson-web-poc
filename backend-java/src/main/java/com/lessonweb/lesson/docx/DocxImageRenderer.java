package com.lessonweb.lesson.docx;

import com.lessonweb.lesson.exception.AppException;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocxImageRenderer {

    private static final Pattern DATA_IMAGE = Pattern.compile(
            "^data:image/(png|jpeg|jpg|gif);(base64)?,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final long EMU_PER_INCH = 914_400L;
    private static final long MAX_WIDTH = Math.round(6.25 * EMU_PER_INCH);
    private static final long MAX_HEIGHT = Math.round(7.5 * EMU_PER_INCH);
    private final AtomicLong drawingId = new AtomicLong(1);

    public void append(WordprocessingMLPackage document, P paragraph, Element image, int availableWidthDxa) {
        byte[] payload = decode(image.attr("src"));
        if (payload == null) {
            throw invalidImage();
        }
        try {
            BufferedImage dimensions = ImageIO.read(new ByteArrayInputStream(payload));
            if (dimensions == null || dimensions.getWidth() < 1 || dimensions.getHeight() < 1) {
                throw invalidImage();
            }
            long width = Math.round(dimensions.getWidth() / 96d * EMU_PER_INCH);
            long height = Math.round(dimensions.getHeight() / 96d * EMU_PER_INCH);
            long maximumWidth = Math.min(MAX_WIDTH, Math.max(1, availableWidthDxa) * 635L);
            double scale = Math.min(1d, Math.min((double) maximumWidth / width, (double) MAX_HEIGHT / height));
            width = Math.max(1, Math.round(width * scale));
            height = Math.max(1, Math.round(height * scale));

            BinaryPartAbstractImage part = BinaryPartAbstractImage.createImagePart(document, payload);
            long id = drawingId.getAndIncrement();
            Inline inline = part.createImageInline("image-" + id, image.attr("alt"), id, (int) id, width, height, false);
            Drawing drawing = new Drawing();
            drawing.getAnchorOrInline().add(inline);
            R run = new R();
            run.getContent().add(drawing);
            paragraph.getContent().add(run);
        } catch (Exception ignored) {
            throw invalidImage();
        }
    }

    private AppException invalidImage() {
        return new AppException("DOCX_IMAGE_INVALID", "图片无法解码或格式不受支持，请更换图片后重试",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private byte[] decode(String source) {
        Matcher matcher = DATA_IMAGE.matcher(source == null ? "" : source);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return matcher.group(2) == null
                    ? percentDecode(matcher.group(3))
                    : Base64.getDecoder().decode(matcher.group(3));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private byte[] percentDecode(String value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '%' && index + 2 < value.length()) {
                int high = Character.digit(value.charAt(index + 1), 16);
                int low = Character.digit(value.charAt(index + 2), 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid percent encoding");
                output.write((high << 4) | low);
                index += 2;
            } else if (current <= 0x7f) {
                output.write(current);
            } else {
                throw new IllegalArgumentException("Non-ASCII data URL payload");
            }
        }
        return output.toByteArray();
    }
}
