package com.jefelabs.modules.requestcontext.service;

import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.FieldConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Helper class for masking sensitive field values based on configured patterns.
 * Supports various masking strategies including simple, advanced, email, and legacy patterns.
 */
@Component
@Slf4j
public class RequestContextMaskingHelper {

    /**
     * Mask a value based on the field configuration
     */
    String maskValue(String value, FieldConfiguration fieldConfig) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (fieldConfig.getSecurity() == null ||
                fieldConfig.getSecurity().getMasking() == null) {
            return "***";
        }

        String maskPattern = fieldConfig.getSecurity().getMasking();

        // Handle advanced masking patterns with {n} syntax
        if (maskPattern.contains("{") && maskPattern.contains("}")) {
            return applyAdvancedMaskPattern(value, maskPattern);
        }

        // Handle email masking
        if (maskPattern.contains("@") && value.contains("@")) {
            return applyEmailMasking(value, maskPattern);
        }

        // Handle legacy partial masking (e.g., "*-4" shows last 4 chars)
        if (maskPattern.startsWith("*-")) {
            return applyLegacyPartialMasking(value, maskPattern);
        }

        // Default: return the pattern as-is (simple replacement)
        return maskPattern;
    }

    /**
     * Mask a value using a specific masking pattern
     */
    String maskValue(String value, String maskPattern) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (maskPattern == null) {
            return "***";
        }

        // Handle advanced masking patterns with {n} syntax
        if (maskPattern.contains("{")) {
            return applyAdvancedMaskPattern(value, maskPattern);
        }

        // Handle email masking (only if pattern looks like email format)
        if (maskPattern.contains("@") && value.contains("@")) {
            return applyEmailMasking(value, maskPattern);
        }

        // Handle special email masking keyword
        if ("email".equals(maskPattern) && value.contains("@")) {
            return applyDefaultEmailMasking(value);
        }

        // Handle legacy partial masking (e.g., "*-4" shows last 4 chars)
        if (maskPattern.startsWith("*-")) {
            return applyLegacyPartialMasking(value, maskPattern);
        }

        // Default: return the pattern as-is (simple replacement)
        return maskPattern;
    }

    /**
     * Apply advanced masking patterns with {n} syntax
     * Examples:
     * - "****-****-****-{4}" -> show last 4 characters
     * - "{8}***" -> show first 8 characters
     * - "***{4}***" -> show middle 4 characters
     * - "{3}-***-{4}" -> show first 3 and last 4 characters
     */
    String applyAdvancedMaskPattern(String value, String maskPattern) {
        try {
            // Validate pattern first
            if (!isValidAdvancedPattern(maskPattern)) {
                return "***";
            }

            StringBuilder result = new StringBuilder();
            int valueIndex = 0;
            int patternIndex = 0;

            while (patternIndex < maskPattern.length() && valueIndex < value.length()) {
                char c = maskPattern.charAt(patternIndex);

                if (c == '{') {
                    // Find the closing brace
                    int closeBrace = maskPattern.indexOf('}', patternIndex);
                    if (closeBrace == -1) {
                        return "***"; // Invalid pattern
                    }

                    // Extract the number
                    String numberStr = maskPattern.substring(patternIndex + 1, closeBrace);
                    int showChars;
                    try {
                        showChars = Integer.parseInt(numberStr);
                        if (showChars < 0) {
                            return "***"; // Invalid negative number
                        }
                    } catch (NumberFormatException e) {
                        return "***"; // Invalid number format
                    }

                    // Determine if this is from start, end, or middle
                    boolean isFromStart = patternIndex == 0 ||
                            (patternIndex > 0 && maskPattern.charAt(patternIndex - 1) != '*');
                    boolean isFromEnd = closeBrace == maskPattern.length() - 1 ||
                            (closeBrace < maskPattern.length() - 1 && maskPattern.charAt(closeBrace + 1) != '*');

                    if (isFromStart) {
                        // Show characters from start
                        int charsToShow = Math.min(showChars, value.length() - valueIndex);
                        result.append(value, valueIndex, valueIndex + charsToShow);
                        valueIndex += charsToShow;
                    } else if (isFromEnd) {
                        // Show characters from end - calculate position
                        int startPos = Math.max(0, value.length() - showChars);
                        int charsToShow = Math.min(showChars, value.length() - valueIndex);
                        if (valueIndex <= startPos) {
                            // Skip to the end position
                            valueIndex = startPos;
                            result.append(value, valueIndex, valueIndex + charsToShow);
                            valueIndex += charsToShow;
                        }
                    } else {
                        // Middle characters - show from current position
                        int charsToShow = Math.min(showChars, value.length() - valueIndex);
                        result.append(value, valueIndex, valueIndex + charsToShow);
                        valueIndex += charsToShow;
                    }

                    patternIndex = closeBrace + 1;
                } else if (c == '*') {
                    // Skip characters in value (mask them)
                    result.append('*');
                    if (valueIndex < value.length()) {
                        valueIndex++;
                    }
                    patternIndex++;
                } else {
                    // Literal character in pattern
                    result.append(c);
                    patternIndex++;
                }
            }

            // Handle any remaining pattern characters
            while (patternIndex < maskPattern.length()) {
                char c = maskPattern.charAt(patternIndex);
                if (c != '{' && c != '}' && !Character.isDigit(c)) {
                    result.append(c);
                }
                patternIndex++;
            }

            return result.toString();

        } catch (Exception e) {
            log.debug("Error applying advanced mask pattern '{}' to value: {}", maskPattern, e.getMessage());
            return "***"; // Fallback to simple masking
        }
    }

    /**
     * Validate advanced pattern syntax
     */
    boolean isValidAdvancedPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        int braceCount = 0;
        boolean inBrace = false;
        StringBuilder numberStr = new StringBuilder();

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);

            if (c == '{') {
                if (inBrace) {
                    return false; // Nested braces not allowed
                }
                inBrace = true;
                braceCount++;
                numberStr.setLength(0);
            } else if (c == '}') {
                if (!inBrace) {
                    return false; // Closing brace without opening
                }
                inBrace = false;

                // Validate the number
                if (numberStr.length() == 0) {
                    return false; // Empty braces
                }
                try {
                    int num = Integer.parseInt(numberStr.toString());
                    if (num < 0) {
                        return false; // Negative numbers not allowed
                    }
                } catch (NumberFormatException e) {
                    return false; // Invalid number
                }
            } else if (inBrace) {
                if (!Character.isDigit(c)) {
                    return false; // Only digits allowed inside braces
                }
                numberStr.append(c);
            }
        }

        return !inBrace; // Make sure all braces are closed
    }

    /**
     * Apply default email masking (show first char and domain extension)
     */
    String applyDefaultEmailMasking(String value) {
        int atIndex = value.indexOf("@");
        if (atIndex <= 0) {
            return "***"; // Not a valid email
        }

        String localPart = value.substring(0, atIndex);
        String domainPart = value.substring(atIndex + 1);

        // Show first character of local part and domain extension
        String maskedLocal = localPart.charAt(0) + "***";
        String maskedDomain = "***";

        if (domainPart.contains(".")) {
            String extension = domainPart.substring(domainPart.lastIndexOf("."));
            maskedDomain = "***" + extension;
        }

        return maskedLocal + "@" + maskedDomain;
    }

    /**
     * Apply email-specific masking
     */
    String applyEmailMasking(String value, String maskPattern) {
        int atIndex = value.indexOf("@");
        if (atIndex <= 0) {
            return maskPattern; // Not a valid email, return pattern as-is
        }

        String localPart = value.substring(0, atIndex);
        String domainPart = value.substring(atIndex + 1);

        // Apply masking based on pattern
        if (maskPattern.equals("***@***.***")) {
            return "***@***.***";
        } else if (maskPattern.contains("{") && maskPattern.contains("}")) {
            // Advanced email masking like "{3}***@***.***" or "***@{3}.***"
            return applyAdvancedMaskPattern(value, maskPattern);
        } else {
            // Simple email masking
            return localPart.charAt(0) + "***@***." +
                    (domainPart.contains(".") ? domainPart.substring(domainPart.lastIndexOf(".") + 1) : "***");
        }
    }

    /**
     * Apply legacy partial masking for backward compatibility
     */
    String applyLegacyPartialMasking(String value, String maskPattern) {
        String[] parts = maskPattern.split("-");
        if (parts.length > 1) {
            try {
                int showChars = Integer.parseInt(parts[1]);
                if (showChars <= 0) {
                    return "***";
                }
                if (value.length() > showChars) {
                    return "***" + value.substring(value.length() - showChars);
                } else {
                    // If value is shorter than or equal to requested chars, return as-is
                    return value;
                }
            } catch (NumberFormatException e) {
                log.debug("Invalid legacy mask pattern: {}", maskPattern);
            }
        }
        return "***";
    }

    /**
     * Check if a masking pattern is valid
     */
    boolean isValidMaskingPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // Simple patterns are always valid
        if (!pattern.contains("{") && !pattern.contains("}")) {
            return true;
        }

        // Validate advanced patterns
        try {
            int openBraces = 0;
            int closeBraces = 0;
            boolean inBrace = false;
            StringBuilder numberBuffer = new StringBuilder();

            for (char c : pattern.toCharArray()) {
                if (c == '{') {
                    if (inBrace) return false; // Nested braces not allowed
                    openBraces++;
                    inBrace = true;
                    numberBuffer.setLength(0);
                } else if (c == '}') {
                    if (!inBrace) return false; // Closing without opening
                    closeBraces++;
                    inBrace = false;

                    // Validate the number inside braces
                    if (numberBuffer.length() == 0) return false;
                    try {
                        int num = Integer.parseInt(numberBuffer.toString());
                        if (num < 0) return false;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                } else if (inBrace) {
                    if (!Character.isDigit(c)) return false;
                    numberBuffer.append(c);
                }
            }

            return openBraces == closeBraces && !inBrace;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get a preview of how a value would be masked without actually masking it
     */
    String getMaskingPreview(String sampleValue, String maskPattern) {
        if (sampleValue == null || sampleValue.isEmpty()) {
            return "[empty]";
        }

        if (maskPattern == null) {
            return "***";
        }

        // Use a sample value if the actual value is too short
        String valueToMask = sampleValue.length() < 10 ?
                sampleValue + "1234567890".substring(0, Math.max(0, 10 - sampleValue.length())) :
                sampleValue;

        return maskValue(valueToMask, maskPattern);
    }
}
