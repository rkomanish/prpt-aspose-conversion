package com.migration.prpt2aspose.generator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one place that knows how Aspose Smart Marker strings are spelled.
 * Also understands the two PRPT formula shapes that convert cleanly:
 * {@code =[Field]} (a plain field reference) and {@code =SUM([Field])}
 * (a group/report aggregate).
 */
public final class SmartMarkerSyntax {

    private static final Pattern FIELD_REF = Pattern.compile("^=\\s*\\[(\\w+)]\\s*$");
    private static final Pattern SUM_OF_FIELD = Pattern.compile("^=\\s*SUM\\(\\s*\\[(\\w+)]\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);

    private SmartMarkerSyntax() {
    }

    /** {@code &=Orders.OrderAmount} — repeats once per data row. */
    public static String field(String dataset, String field) {
        return "&=" + dataset + "." + field;
    }

    /** {@code &=Orders.CustomerName(group:normal)} — grouped column: value shown once per group. */
    public static String groupedField(String dataset, String field) {
        return "&=" + dataset + "." + field + "(group:normal)";
    }

    /** {@code &=Orders.OrderAmount(subtotal9:Orders.CustomerName)} — per-group SUM row inserted by Aspose. */
    public static String subtotaledField(String dataset, String field, String groupField) {
        return "&=" + dataset + "." + field + "(subtotal9:" + dataset + "." + groupField + ")";
    }

    /** {@code &=$fromDate} — scalar variable, bound via WorkbookDesigner.setDataSource(name, value). */
    public static String variable(String name) {
        return "&=$" + name;
    }

    /** Field name when the formula is just {@code =[Field]}, else null. */
    public static String asDirectFieldRef(String formula) {
        return firstGroupOrNull(FIELD_REF, formula);
    }

    /** Field name when the formula is {@code =SUM([Field])}, else null. */
    public static String asSumOfField(String formula) {
        return firstGroupOrNull(SUM_OF_FIELD, formula);
    }

    private static String firstGroupOrNull(Pattern pattern, String formula) {
        if (formula == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(formula.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }
}
