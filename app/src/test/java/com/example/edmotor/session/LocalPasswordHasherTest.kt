package com.example.edmotor.session

import com.example.edmotor.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPasswordHasherTest {
    @Test
    fun samePasswordGetsDistinctSaltAndOnlyItsMatchingPasswordVerifies() {
        val password = "prueba-offline-año"
        val first = LocalPasswordHasher.create(password)
        val second = LocalPasswordHasher.create(password)

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
        assertFalse(first.hash.contains(password))
        assertTrue(LocalPasswordHasher.verify(password, first.hash, first.salt))
        assertFalse(LocalPasswordHasher.verify("incorrecta", first.hash, first.salt))
        assertFalse(LocalPasswordHasher.verify(password, first.hash, second.salt))
        assertFalse(LocalPasswordHasher.verify(password, null, null))
    }

    @Test
    fun api24CompatibleRecordUsesTheSupportedVersionAndWorkFactor() {
        val hash = "pbkdf2-sha1-v1:1300000:wmaLTSv2dexdxuniJonREr6vqwn1JZMDoW5smtvskww="
        val salt = "VGVzdFNhbHQtMTZCeXRlcw=="
        assertTrue(LocalPasswordHasher.isRecognized(hash, salt))
        assertTrue(LocalPasswordHasher.verify("old-android-fixture", hash, salt))
        assertFalse(LocalPasswordHasher.isRecognized(hash.replace("1300000", "1"), salt))
        assertFalse(LocalPasswordHasher.isRecognized(hash, "sal-inválida"))
    }

    @Test
    fun localIdentityMatchesOnlyItsExpectedRoleAndNamespace() {
        val client = "local-account:4d65b827-1394-4229-b274-a5071e2cb66b"
        val owner = "local-account:owner"
        assertTrue(LocalAccountIdentity.matchesRole(client, UserRole.CLIENT.name))
        assertTrue(LocalAccountIdentity.matchesRole(owner, UserRole.WORKSHOP.name))
        assertFalse(LocalAccountIdentity.matchesRole(client, UserRole.WORKSHOP.name))
        assertFalse(LocalAccountIdentity.matchesRole(owner, UserRole.CLIENT.name))
        assertFalse(LocalAccountIdentity.isKey("mockapi:demo:1"))
        assertFalse(LocalAccountIdentity.isKey("remote:project:4d65b827-1394-4229-b274-a5071e2cb66b"))
        assertFalse(LocalAccountIdentity.isKey("local-account:1-1-1-1-1"))
    }
}
