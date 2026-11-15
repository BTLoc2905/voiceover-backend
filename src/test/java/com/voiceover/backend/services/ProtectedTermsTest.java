package com.voiceover.backend.services;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProtectedTermsTest {
    @Test void longestMatchDuplicatesRepeatedNamesAndRegexCharactersSurvive() throws Exception {
        String source = "Saigon University, Saigon University; Saigon; C++ (A).";
        var terms = new ProtectedTerms(source, List.of("Saigon", "Saigon University", "Saigon", "C++ (A)", "", "TERM"));
        assertFalse(terms.masked().contains("Saigon"));
        assertEquals(source, terms.restore(terms.masked()));
    }

    @Test void literalTokenInSourceOrProtectedTermCannotCollideOrBeRestoredAsAnotherName() throws Exception {
        String source = "PTERM0X, Saigon and PTERM0X University.";
        var terms = new ProtectedTerms(source, List.of("Saigon", "PTERM0X University", "TERM"));
        assertEquals(source, terms.restore(terms.masked()));
    }

    @Test void missingChangedOrDuplicatedTokensFailInsteadOfPersistingCorruptedNames() {
        var terms = new ProtectedTerms("Saigon and Saigon", List.of("Saigon"));
        assertThrows(IOException.class, () -> terms.restore("a translated city"));
        assertThrows(IOException.class, () -> terms.restore("PTERM0X"));
        assertThrows(IOException.class, () -> terms.restore("PTERM0X PTERM0X PTERM0X"));
    }

    @Test void noMatchingTermsLeavesTranslationUntouched() throws Exception {
        var terms = new ProtectedTerms("Hello", List.of("Saigon"));
        assertEquals("Hello", terms.masked()); assertEquals("Bonjour", terms.restore("Bonjour"));
    }
}
