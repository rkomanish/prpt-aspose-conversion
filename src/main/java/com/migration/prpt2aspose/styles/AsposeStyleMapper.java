package com.migration.prpt2aspose.styles;

import com.aspose.cells.BackgroundType;
import com.aspose.cells.Color;
import com.aspose.cells.Font;
import com.aspose.cells.Style;
import com.aspose.cells.TextAlignmentType;
import com.aspose.cells.Workbook;
import com.migration.prpt2aspose.model.ElementType;
import com.migration.prpt2aspose.model.StyleDefinition;
import org.springframework.stereotype.Component;

/**
 * Translates the raw {@link StyleDefinition} facts captured at parse time
 * into an Aspose {@link Style}. Number/date display formats ride along in
 * {@code rawAttributes["format"]}; date patterns are lowercased because
 * Excel format codes use {@code yyyy-mm-dd} where Java uses {@code yyyy-MM-dd}.
 */
@Component
public class AsposeStyleMapper {

    public Style toAsposeStyle(Workbook workbook, StyleDefinition def, ElementType elementType) {
        Style style = workbook.createStyle();
        Font font = style.getFont();

        if (def.fontFamily() != null) {
            font.setName(def.fontFamily());
        }
        if (def.fontSize() != null) {
            font.setDoubleSize(def.fontSize());
        }
        font.setBold(def.bold());
        font.setItalic(def.italic());

        Integer alignment = alignmentOf(def.textAlign());
        if (alignment != null) {
            style.setHorizontalAlignment(alignment);
        }

        Color background = parseHexColor(def.backgroundColor());
        if (background != null) {
            style.setForegroundColor(background);
            style.setPattern(BackgroundType.SOLID);
        }

        String format = def.rawAttributes().get("format");
        if (format != null && !format.isBlank()) {
            style.setCustom(elementType == ElementType.DATE_FIELD ? format.toLowerCase() : format);
        }

        return style;
    }

    private Integer alignmentOf(String textAlign) {
        if (textAlign == null) {
            return null;
        }
        return switch (textAlign.toLowerCase()) {
            case "left" -> TextAlignmentType.LEFT;
            case "center", "middle" -> TextAlignmentType.CENTER;
            case "right" -> TextAlignmentType.RIGHT;
            case "justify" -> TextAlignmentType.JUSTIFY;
            default -> null;
        };
    }

    private Color parseHexColor(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return Color.fromArgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}
