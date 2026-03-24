package com.kraat.lostfilmnewtv.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun isNewerThan_returnsTrue_whenVersionHasHigherMajor() {
        assertTrue(VersionComparator.isNewerThan("2.0.0", "1.0.0"))
    }

    @Test
    fun isNewerThan_returnsTrue_whenVersionHasHigherMinor() {
        assertTrue(VersionComparator.isNewerThan("1.1.0", "1.0.0"))
    }

    @Test
    fun isNewerThan_returnsTrue_whenVersionHasHigherPatch() {
        assertTrue(VersionComparator.isNewerThan("1.0.1", "1.0.0"))
    }

    @Test
    fun isNewerThan_returnsFalse_whenVersionsEqual() {
        assertFalse(VersionComparator.isNewerThan("1.0.0", "1.0.0"))
    }

    @Test
    fun isNewerThan_returnsFalse_whenVersionIsOlder() {
        assertFalse(VersionComparator.isNewerThan("1.0.0", "2.0.0"))
    }

    @Test
    fun isNewerThan_handlesComplexVersionFormats() {
        assertTrue(VersionComparator.isNewerThan("v2026.03.24.125", "v2026.03.24.123"))
    }

    @Test
    fun isNewerThan_handlesDifferentLengthVersions() {
        assertTrue(VersionComparator.isNewerThan("1.0.1", "1.0"))
    }

    @Test
    fun isNewerThan_handlesNonNumericParts() {
        assertTrue(VersionComparator.isNewerThan("v1.2.3-beta", "v1.2.2"))
    }
}
