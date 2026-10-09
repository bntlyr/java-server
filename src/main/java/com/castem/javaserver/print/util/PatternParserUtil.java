package com.castem.javaserver.print.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternParserUtil {

    /**
     * Format: E5.5D3 or E5.5D3A
     * Group 1 = First Letter (e.g. E)
     * Group 2 = Thickness (e.g. 5.5)
     * Group 3 = D Letter (e.g. D)
     * Group 4 = D Number (e.g. 3)
     * Group 5 = Optional Suffix Letter (e.g. A)
     */
    private static final Pattern COATING_PATTERN =
        Pattern.compile("^([A-Za-z]+)(\\d+\\.\\d+)(D)(\\d+)([A-Za-z])?$");

    public record CoatingResult(
        String letter,
        String thickness,
        String dLetter,
        int dNumber,
        String lastLetter
    ) {}

    public static CoatingResult parseCoatingPattern(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = COATING_PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
            return null;
        }

        return new CoatingResult(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3),
            Integer.parseInt(matcher.group(4)),
            matcher.group(5)
        );
    }

    /**
     * Format: 1/2 or 1
     */
    private static final Pattern SLIP_PATTERN = Pattern.compile("^(\\d+)\\/(\\d+)$");

    public record SlipSizeResult(
        String first,
        String second
    ) {
        public static SlipSizeResult empty() {
            return new SlipSizeResult("", "");
        }
    }

    public static SlipSizeResult parseSlipSize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return SlipSizeResult.empty();
        }

        Matcher matcher = SLIP_PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
            return new SlipSizeResult(input.trim(), "");
        }

        return new SlipSizeResult(
            matcher.group(1),
            matcher.group(2)
        );
    }
}
