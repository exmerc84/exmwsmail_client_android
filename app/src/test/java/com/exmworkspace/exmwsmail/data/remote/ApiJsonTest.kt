package com.exmworkspace.exmwsmail.data.remote

import com.exmworkspace.exmwsmail.data.remote.dto.ContactUpsertDto
import com.exmworkspace.exmwsmail.data.remote.dto.DeviceRegisterRequest
import com.exmworkspace.exmwsmail.data.remote.dto.LoginRequest
import com.exmworkspace.exmwsmail.data.remote.dto.TokenPairDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the encoding rules the backend depends on. These are invisible failures: a dropped
 * field produces a 200 with the wrong semantics, not an error.
 */
class ApiJsonTest {

    @Test
    fun login_body_carries_the_mobile_client_flag() {
        val body = ApiJson.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(email = "yo@dominio.com", password = "secreto"),
        )
        // Without this the backend replies in legacy web mode: no refresh token, no session.
        assertTrue("client flag missing from $body", body.contains("\"client\":\"mobile\""))
    }

    @Test
    fun login_body_omits_an_absent_captcha_token() {
        val body = ApiJson.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(email = "yo@dominio.com", password = "secreto"),
        )
        assertFalse(body.contains("captcha_token"))
    }

    @Test
    fun login_body_includes_the_captcha_token_when_present() {
        val body = ApiJson.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(email = "yo@dominio.com", password = "secreto", captchaToken = "def456"),
        )
        assertTrue(body.contains("\"captcha_token\":\"def456\""))
    }

    @Test
    fun device_registration_carries_the_android_platform() {
        val body = ApiJson.encodeToString(
            DeviceRegisterRequest.serializer(),
            DeviceRegisterRequest(fcmToken = "t", deviceName = "Pixel", appVersion = "1.0"),
        )
        assertTrue("platform missing from $body", body.contains("\"platform\":\"android\""))
        assertTrue(body.contains("\"fcm_token\":\"t\""))
    }

    @Test
    fun contact_upsert_sends_cleared_fields_so_they_can_be_erased() {
        val body = ApiJson.encodeToString(
            ContactUpsertDto.serializer(),
            ContactUpsertDto(email = "yo@dominio.com", displayName = "Yo"),
        )
        // explicitNulls = false drops nulls, so blanks must travel as "" or the backend
        // would keep whatever it had and nothing could ever be cleared.
        assertTrue("group_name missing from $body", body.contains("\"group_name\":\"\""))
        assertTrue(body.contains("\"phone\":\"\""))
        assertTrue(body.contains("\"display_name\":\"Yo\""))
    }

    @Test
    fun contact_upsert_never_sends_the_favourite_flag() {
        val body = ApiJson.encodeToString(
            ContactUpsertDto.serializer(),
            ContactUpsertDto(email = "yo@dominio.com"),
        )
        // Editing a contact must not silently un-favourite it.
        assertFalse(body.contains("is_favorite"))
    }

    @Test
    fun token_response_is_parsed_with_snake_case_names() {
        val pair = ApiJson.decodeFromString(
            TokenPairDto.serializer(),
            """
            {"access_token":"a","token_type":"bearer","refresh_token":"r","expires_in":900,
             "user":{"id":1,"email":"yo@dominio.com","display_name":"Yo","phone":null}}
            """.trimIndent(),
        )
        assertEquals("a", pair.accessToken)
        assertEquals("r", pair.refreshToken)
        assertEquals(900L, pair.expiresIn)
        assertEquals("Yo", pair.user?.displayName)
        assertNull(pair.user?.phone)
    }

    @Test
    fun unknown_response_fields_are_ignored() {
        val pair = ApiJson.decodeFromString(
            TokenPairDto.serializer(),
            """{"access_token":"a","refresh_token":"r","expires_in":900,"nuevo_campo":42}""",
        )
        assertEquals("a", pair.accessToken)
    }
}
