package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeBaseTest {
    @Test fun presetsContainOnlyKnownSafeEntries() {
        for (p in KnowledgeBase.presets) for (pkg in p.pkgs) {
            val info = KnowledgeBase.classify(pkg, true)
            assertTrue("$pkg in ${p.id} must be a known SAFE entry", info.known && info.risk == Risk.SAFE)
        }
    }

    @Test fun presetsHaveValidPackageNames() {
        for (p in KnowledgeBase.presets) for (pkg in p.pkgs) assertTrue(pkg, isValidPackageName(pkg))
    }

    @Test fun coreAndOverlaysAreCore() {
        assertEquals(Risk.CORE, KnowledgeBase.classify("com.android.systemui", true).risk)
        assertEquals(Risk.CORE, KnowledgeBase.classify("com.android.phone.auto_generated_characteristics_rro", true).risk)
        assertEquals(Risk.CORE, KnowledgeBase.classify("com.miui.systemui.carriers.overlay", true).risk)
        assertEquals(Risk.CORE, KnowledgeBase.classify("com.android.providers.blockednumber", true).risk)
    }

    @Test fun unknownPackagesFallBackSafely() {
        assertEquals(Risk.USER, KnowledgeBase.classify("com.example.thing", false).risk)
        assertEquals(Risk.CAUTION, KnowledgeBase.classify("com.mediatek.ygps", true).risk)
        assertEquals(Risk.CAUTION, KnowledgeBase.classify("com.miui.unknownthing", true).risk)
    }

    @Test fun prettifyStripsPrefixes() {
        assertEquals("Thing", KnowledgeBase.prettify("com.example.thing"))
    }
}
