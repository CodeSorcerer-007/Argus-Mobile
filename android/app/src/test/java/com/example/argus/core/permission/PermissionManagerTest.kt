package com.example.argus.core.permission

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionManagerTest {

    @Test
    fun testGetRequiredPermissionsIsNotEmpty() {
        val perms = PermissionManager.getRequiredPermissions()
        assertNotNull(perms)
        assertTrue("Required permissions must contain at least Camera and Record Audio", perms.isNotEmpty())
        assertTrue("Must contain CAMERA", perms.contains(android.Manifest.permission.CAMERA))
        assertTrue("Must contain RECORD_AUDIO", perms.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue("Must contain READ_CONTACTS", perms.contains(android.Manifest.permission.READ_CONTACTS))
    }

    @Test
    fun testGetStorageAndMediaPermissions() {
        val mediaPerms = PermissionManager.getStorageAndMediaPermissions()
        assertNotNull(mediaPerms)
        assertTrue("Media permissions list must not be empty", mediaPerms.isNotEmpty())
    }

    @Test
    fun testGetCallPermissions() {
        val callPerms = PermissionManager.getCallPermissions()
        assertEquals(2, callPerms.size)
        assertTrue("Call permissions must include RECORD_AUDIO", callPerms.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue("Call permissions must include CAMERA", callPerms.contains(android.Manifest.permission.CAMERA))
    }

    @Test
    fun testPermissionTypeEnums() {
        val types = ArgusPermissionType.values()
        assertTrue("Should have 5 core permission types defined", types.size >= 5)
        assertNotNull(ArgusPermissionType.CAMERA.title)
        assertNotNull(ArgusPermissionType.AUDIO.title)
        assertNotNull(ArgusPermissionType.STORAGE_AND_MEDIA.title)
        assertNotNull(ArgusPermissionType.NOTIFICATIONS.title)
        assertNotNull(ArgusPermissionType.CONTACTS.title)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
