package org.dromara.lqg.embed.guard;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmbedReceiptKindRulesTest {
    @Test void allowsOnlyExplicitKinds() {
        assertEquals("tissue", EmbedFillRules.normalizeReceiptKind("tissue"));
        assertEquals("organoid", EmbedFillRules.normalizeReceiptKind("organoid"));
        assertThrows(ServiceException.class, () -> EmbedFillRules.normalizeReceiptKind("anything"));
        assertThrows(ServiceException.class, () -> EmbedFillRules.normalizeReceiptKind("TISSUE"));
    }
    @Test void oldClientsAndClearStayNullable() {
        assertNull(EmbedFillRules.normalizeReceiptKind(null));
        assertNull(EmbedFillRules.normalizeReceiptKind("  "));
    }
}
