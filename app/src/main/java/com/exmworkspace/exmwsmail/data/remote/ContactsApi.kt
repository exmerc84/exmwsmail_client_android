package com.exmworkspace.exmwsmail.data.remote

import com.exmworkspace.exmwsmail.data.remote.dto.ContactCountsDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupUpsertDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactUpsertDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** The address book (§5). Same JWT session as the mail endpoints. */
interface ContactsApi {

    @GET("api/contacts")
    suspend fun list(): Response<List<ContactDto>>

    /** Recipient autocomplete while composing. */
    @GET("api/contacts/suggest")
    suspend fun suggest(@Query("q") query: String): Response<List<ContactDto>>

    /** Everyone on the same domain — colleagues who are not in the personal book. */
    @GET("api/contacts/corporate/list")
    suspend fun corporate(): Response<List<ContactDto>>

    @GET("api/contacts/count")
    suspend fun counts(): Response<ContactCountsDto>

    @GET("api/contacts/groups")
    suspend fun groups(): Response<List<ContactGroupDto>>

    /** Richer variant: id, name, domain, colour. Falls back to [groups] if absent. */
    @GET("api/contacts/groups/detail")
    suspend fun groupsDetail(): Response<List<ContactGroupDto>>

    // Group writes live under /groups/manage (§5.1); /groups itself is read-only and answers
    // 405 to a POST.

    /** 409 when a group with that name already exists. */
    @POST("api/contacts/groups/manage")
    suspend fun createGroup(@Body group: ContactGroupUpsertDto): Response<ContactGroupDto>

    /**
     * Partial update. Renaming cascades to the group's contacts, and changing the domain
     * re-assigns them: contacts of the old domain are released, contacts of the new one
     * picked up.
     */
    @PUT("api/contacts/groups/manage/{id}")
    suspend fun updateGroup(
        @Path("id") id: Long,
        @Body group: ContactGroupUpsertDto,
    ): Response<ContactGroupDto>

    /** Contacts survive; they are just left without a group (`group_name = ""`). */
    @DELETE("api/contacts/groups/manage/{id}")
    suspend fun deleteGroup(@Path("id") id: Long): Response<ResponseBody>

    @GET("api/contacts/{id}")
    suspend fun detail(@Path("id") id: Long): Response<ContactDto>

    @POST("api/contacts")
    suspend fun create(@Body contact: ContactUpsertDto): Response<ContactDto>

    @PUT("api/contacts/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body contact: ContactUpsertDto,
    ): Response<ContactDto>

    @DELETE("api/contacts/{id}")
    suspend fun delete(@Path("id") id: Long): Response<ResponseBody>

    @POST("api/contacts/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: Long): Response<ResponseBody>

    /** Mines the mailbox for addresses the user has corresponded with. */
    @POST("api/contacts/import-from-emails")
    suspend fun importFromEmails(): Response<ResponseBody>
}
