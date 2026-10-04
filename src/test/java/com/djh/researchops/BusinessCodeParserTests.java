package com.djh.researchops;

import com.djh.researchops.util.BusinessCodeParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BusinessCodeParserTests {
    @ParameterizedTest
    @CsvSource({"T-1,T-1", "t-1,T-1", "T1,T-1", "t1,T-1", "T-15,T-15",
            "T-9223372036854775807,T-9223372036854775807"})
    void normalizesTaskCode(String input, String expected) {
        assertEquals(expected, BusinessCodeParser.normalizeTaskCode(input));
    }

    @ParameterizedTest
    @CsvSource({"R-2,R-2", "r-2,R-2", "R2,R-2", "r2,R-2", "R-15,R-15",
            "R-9223372036854775807,R-9223372036854775807"})
    void normalizesRunCode(String input, String expected) {
        assertEquals(expected, BusinessCodeParser.normalizeRunCode(input));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("T-7", BusinessCodeParser.normalizeTaskCode("  t7 \t"));
        assertEquals("R-7", BusinessCodeParser.normalizeRunCode("  r-7 \t"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "T-0", "T--1", "T-abc", "T-01", "T 1", "R-1", "1",
            "Task 1", "T-9223372036854775808", "T-9999999999999999999999"})
    void rejectsInvalidTaskCode(String input) {
        assertNull(BusinessCodeParser.normalizeTaskCode(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "R-0", "R--1", "R-test", "R-01", "R 1", "T-1", "1",
            "Run 1", "R-9223372036854775808", "R-9999999999999999999999"})
    void rejectsInvalidRunCode(String input) {
        assertNull(BusinessCodeParser.normalizeRunCode(input));
    }
}
