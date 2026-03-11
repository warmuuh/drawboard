package com.drawboard.domain.search;

import io.avaje.jsonb.Json;

/**
 * Options for configuring search behavior.
 */
@Json
public record SearchOptions(
    boolean caseSensitive,
    boolean wholeWord,
    boolean useRegex,
    int maxResults,
    int snippetContextLength
) {
    /**
     * Default search options: case-insensitive, no regex, 100 results, 50 chars context.
     */
    public static SearchOptions defaults() {
        return new SearchOptions(false, false, false, 100, 50);
    }

    /**
     * Create options with custom values, using defaults for unspecified.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean caseSensitive = false;
        private boolean wholeWord = false;
        private boolean useRegex = false;
        private int maxResults = 100;
        private int snippetContextLength = 50;

        public Builder caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder wholeWord(boolean wholeWord) {
            this.wholeWord = wholeWord;
            return this;
        }

        public Builder useRegex(boolean useRegex) {
            this.useRegex = useRegex;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder snippetContextLength(int snippetContextLength) {
            this.snippetContextLength = snippetContextLength;
            return this;
        }

        public SearchOptions build() {
            return new SearchOptions(caseSensitive, wholeWord, useRegex,
                                    maxResults, snippetContextLength);
        }
    }
}
