package com.migration.prpt2aspose.parser;

/** Reserved for genuinely unrecoverable failures (not a zip, no report definition at all) — everything else becomes a ParsingWarning. */
public class PrptParsingException extends RuntimeException {

    public PrptParsingException(String message) {
        super(message);
    }

    public PrptParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
