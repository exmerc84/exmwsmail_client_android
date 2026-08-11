package com.exmworkspace.exmwsmail.data.remote

import com.exmworkspace.exmwsmail.data.remote.dto.AiDraftDto
import com.exmworkspace.exmwsmail.data.remote.dto.AiDraftRequest
import com.exmworkspace.exmwsmail.data.remote.dto.AttachmentBrowseDto
import com.exmworkspace.exmwsmail.data.remote.dto.CalendarReplyRequest
import com.exmworkspace.exmwsmail.data.remote.dto.FolderShareDto
import com.exmworkspace.exmwsmail.data.remote.dto.FolderShareRequest
import com.exmworkspace.exmwsmail.data.remote.dto.FollowupCreateDto
import com.exmworkspace.exmwsmail.data.remote.dto.FollowupDto
import com.exmworkspace.exmwsmail.data.remote.dto.FollowupListDto
import com.exmworkspace.exmwsmail.data.remote.dto.RemoteAccountDto
import com.exmworkspace.exmwsmail.data.remote.dto.FollowupUpdateDto
import com.exmworkspace.exmwsmail.data.remote.dto.SummarizeRequest
import com.exmworkspace.exmwsmail.data.remote.dto.SummaryDto
import com.exmworkspace.exmwsmail.data.remote.dto.TranslateDto
import com.exmworkspace.exmwsmail.data.remote.dto.TranslateRequest
import com.exmworkspace.exmwsmail.data.remote.dto.TranslateSegmentsDto
import com.exmworkspace.exmwsmail.data.remote.dto.TranslateSegmentsRequest
import com.exmworkspace.exmwsmail.data.remote.dto.DeviceDto
import com.exmworkspace.exmwsmail.data.remote.dto.DeviceRegisterRequest
import com.exmworkspace.exmwsmail.data.remote.dto.DraftLookupDto
import com.exmworkspace.exmwsmail.data.remote.dto.DraftSavedDto
import com.exmworkspace.exmwsmail.data.remote.dto.FolderDto
import com.exmworkspace.exmwsmail.data.remote.dto.MessageDetailDto
import com.exmworkspace.exmwsmail.data.remote.dto.MessageHeadersDto
import com.exmworkspace.exmwsmail.data.remote.dto.MessageSourceDto
import com.exmworkspace.exmwsmail.data.remote.dto.MessageDto
import com.exmworkspace.exmwsmail.data.remote.dto.QuotaDto
import com.exmworkspace.exmwsmail.data.remote.dto.SimpleMessageDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * EXMWS Mail REST API. Folder actions take their arguments as query params, not JSON
 * bodies, and `/send` + `/drafts` are multipart — both are backend quirks, not oversights.
 */
interface MailApi {

    // ---- Folders ----

    @GET("api/emails/folders")
    suspend fun folders(): Response<List<FolderDto>>

    @POST("api/emails/folders")
    suspend fun createFolder(@Query("name") name: String): Response<SimpleMessageDto>

    @PUT("api/emails/folders/rename")
    suspend fun renameFolder(
        @Query("old_name") oldName: String,
        @Query("new_name") newName: String,
    ): Response<SimpleMessageDto>

    @POST("api/emails/folders/empty")
    suspend fun emptyFolder(@Query("name") name: String): Response<SimpleMessageDto>

    @DELETE("api/emails/folders")
    suspend fun deleteFolder(@Query("name") name: String): Response<SimpleMessageDto>

    // ---- Messages ----

    @GET("api/emails/messages")
    suspend fun messages(
        @Query("folder") folder: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("category") category: String? = null,
        @Query("color") color: String? = null,
    ): Response<List<MessageDto>>

    @GET("api/emails/messages/{uid}")
    suspend fun message(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
    ): Response<MessageDetailDto>

    /** Full RFC822 source, "ver original" (§4.17). */
    @GET("api/emails/messages/{uid}/source")
    suspend fun messageSource(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
    ): Response<MessageSourceDto>

    /** Raw headers only (§4.17). */
    @GET("api/emails/messages/{uid}/headers")
    suspend fun messageHeaders(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
    ): Response<MessageHeadersDto>

    /** Every attachment in the mailbox, newest first; excludes Junk and Trash (§4.17). */
    @GET("api/emails/attachments/browse")
    suspend fun browseAttachments(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("search") search: String? = null,
    ): Response<List<AttachmentBrowseDto>>

    /** Copies the attachment into the user's EXMWS Cloud (§4.17). */
    @POST("api/emails/messages/{uid}/attachment/{index}/save-to-cloud")
    suspend fun saveAttachmentToCloud(
        @Path("uid") uid: String,
        @Path("index") index: Int,
        @Query("folder") folder: String,
        @Query("subfolder") subfolder: String? = null,
        @Query("overwrite") overwrite: Boolean = false,
    ): Response<SimpleMessageDto>

    /** Body is a flat JSON array of uids — NOT wrapped in an object. */
    @POST("api/emails/messages/batch")
    suspend fun messageBatch(
        @Query("folder") folder: String,
        @Body uids: List<String>,
    ): Response<List<MessageDetailDto>>

    @POST("api/emails/prefetch")
    suspend fun prefetch(
        @Query("folder") folder: String,
        @Body uids: List<String>,
    ): Response<SimpleMessageDto>

    @GET("api/emails/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("folder") folder: String = "INBOX",
    ): Response<List<MessageDto>>

    @GET("api/emails/category-counts")
    suspend fun categoryCounts(
        @Query("folder") folder: String = "INBOX",
    ): Response<Map<String, Int>>

    @POST("api/emails/sync")
    suspend fun sync(@Query("folder") folder: String?): Response<SimpleMessageDto>

    @GET("api/emails/quota")
    suspend fun quota(): Response<QuotaDto>

    // ---- Per-message actions ----

    @POST("api/emails/messages/{uid}/read")
    suspend fun markRead(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
    ): Response<SimpleMessageDto>

    @POST("api/emails/messages/{uid}/unread")
    suspend fun markUnread(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
    ): Response<SimpleMessageDto>

    @POST("api/emails/messages/{uid}/pin")
    suspend fun pin(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
    ): Response<SimpleMessageDto>

    @DELETE("api/emails/messages/{uid}/pin")
    suspend fun unpin(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
    ): Response<SimpleMessageDto>

    /** Omit `color` to clear the flag. */
    @PUT("api/emails/messages/{uid}/color")
    suspend fun setColor(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
        @Query("color") color: String? = null,
    ): Response<SimpleMessageDto>

    @POST("api/emails/messages/{uid}/move")
    suspend fun move(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
        @Query("destination") destination: String,
    ): Response<SimpleMessageDto>

    @POST("api/emails/messages/{uid}/copy")
    suspend fun copy(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
        @Query("destination") destination: String,
    ): Response<SimpleMessageDto>

    @POST("api/emails/messages/{uid}/spam")
    suspend fun markSpam(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
    ): Response<SimpleMessageDto>

    /** Moves to Trash unless the message already lives there, in which case it purges. */
    @DELETE("api/emails/messages/{uid}")
    suspend fun delete(
        @Path("uid") uid: String,
        @Query("folder") folder: String,
    ): Response<SimpleMessageDto>

    // ---- Threads ----
    // A list row represents a whole thread; the visible uid is only its representative,
    // so list-level actions must go through these, not the per-message endpoints.

    @GET("api/emails/thread")
    suspend fun thread(
        @Query("thread_id") threadId: String,
        @Query("folder") folder: String,
    ): Response<List<MessageDto>>

    @POST("api/emails/thread/read")
    suspend fun threadRead(@Query("thread_id") threadId: String): Response<SimpleMessageDto>

    @POST("api/emails/thread/move")
    suspend fun threadMove(
        @Query("thread_id") threadId: String,
        @Query("destination") destination: String,
        @Query("source_folder") sourceFolder: String? = null,
    ): Response<SimpleMessageDto>

    @DELETE("api/emails/thread")
    suspend fun threadDelete(
        @Query("thread_id") threadId: String,
        @Query("folder") folder: String,
    ): Response<SimpleMessageDto>

    @POST("api/emails/thread/restore")
    suspend fun threadRestore(@Query("thread_id") threadId: String): Response<SimpleMessageDto>

    // ---- Attachments ----

    @Streaming
    @GET("api/emails/messages/{uid}/attachment/{index}")
    suspend fun attachment(
        @Path("uid") uid: String,
        @Path("index") index: Int,
        @Query("folder") folder: String,
    ): Response<ResponseBody>

    @Streaming
    @GET("api/emails/messages/{uid}/cid/{contentId}")
    suspend fun inlineImage(
        @Path("uid") uid: String,
        @Path("contentId") contentId: String,
        @Query("folder") folder: String,
    ): Response<ResponseBody>

    // ---- Sending ----

    @Multipart
    @POST("api/emails/send")
    suspend fun send(
        @Part("to") to: RequestBody,
        @Part("cc") cc: RequestBody,
        @Part("bcc") bcc: RequestBody,
        @Part("subject") subject: RequestBody,
        @Part("body") body: RequestBody,
        @Part("is_html") isHtml: RequestBody,
        @Part("in_reply_to") inReplyTo: RequestBody,
        @Part("references") references: RequestBody,
        @Part("forward_of") forwardOf: RequestBody,
        @Part files: List<MultipartBody.Part>,
    ): Response<SimpleMessageDto>

    // ---- Drafts ----

    @Multipart
    @POST("api/emails/drafts")
    suspend fun saveDraft(
        @Part("to") to: RequestBody,
        @Part("cc") cc: RequestBody,
        @Part("bcc") bcc: RequestBody,
        @Part("subject") subject: RequestBody,
        @Part("body") body: RequestBody,
        @Part("in_reply_to") inReplyTo: RequestBody,
        @Part("references") references: RequestBody,
        @Part("client_draft_id") clientDraftId: RequestBody,
        @Part("attachments_mode") attachmentsMode: RequestBody,
        @Part("prev_uid") prevUid: RequestBody,
        @Part files: List<MultipartBody.Part>,
    ): Response<DraftSavedDto>

    /** Returns a JSON `null` body when the window has no tracked draft. */
    @GET("api/emails/drafts")
    suspend fun draft(
        @Query("client_draft_id") clientDraftId: String,
    ): Response<DraftLookupDto?>

    @DELETE("api/emails/drafts")
    suspend fun deleteDraft(
        @Query("client_draft_id") clientDraftId: String,
        @Query("uid") uid: String? = null,
    ): Response<SimpleMessageDto>

    // ---- Calendar RSVP (§4.22) ----

    @POST("api/emails/calendar/reply")
    suspend fun calendarReply(@Body body: CalendarReplyRequest): Response<SimpleMessageDto>

    // ---- Accounts (§4.23) ----
    // Read-only on purpose: mailboxes are provisioned server-side, so the section's
    // POST/DELETE are not declared here.

    @GET("api/accounts/")
    suspend fun accounts(): Response<List<RemoteAccountDto>>

    // ---- Devices (FCM) ----

    @POST("api/devices/register")
    suspend fun registerDevice(@Body body: DeviceRegisterRequest): Response<DeviceDto>

    @GET("api/devices/")
    suspend fun devices(): Response<List<DeviceDto>>

    @DELETE("api/devices/{id}")
    suspend fun deleteDevice(@Path("id") id: Long): Response<ResponseBody>

    // ---- Followups (§4.19) ----

    @GET("api/followups")
    suspend fun followups(): Response<FollowupListDto>

    @POST("api/followups")
    suspend fun createFollowup(@Body body: FollowupCreateDto): Response<FollowupDto>

    @PUT("api/followups/{id}")
    suspend fun updateFollowup(
        @Path("id") id: Long,
        @Body body: FollowupUpdateDto,
    ): Response<FollowupDto>

    @POST("api/followups/{id}/done")
    suspend fun followupDone(@Path("id") id: Long): Response<SimpleMessageDto>

    @DELETE("api/followups/{id}")
    suspend fun deleteFollowup(@Path("id") id: Long): Response<SimpleMessageDto>

    // ---- AI (§4.21) ----

    @POST("api/emails/summarize")
    suspend fun summarize(@Body body: SummarizeRequest): Response<SummaryDto>

    @POST("api/emails/ai/draft")
    suspend fun aiDraft(@Body body: AiDraftRequest): Response<AiDraftDto>

    @POST("api/emails/ai/translate")
    suspend fun translate(@Body body: TranslateRequest): Response<TranslateDto>

    /** For inline translation: the app sends the text nodes, never the HTML around them. */
    @POST("api/emails/ai/translate-segments")
    suspend fun translateSegments(
        @Body body: TranslateSegmentsRequest,
    ): Response<TranslateSegmentsDto>

    // ---- Shared folders (§4.20) ----

    /** Who a folder of mine is shared with. */
    @GET("api/emails/folders/shares")
    suspend fun folderShares(@Query("name") name: String): Response<List<FolderShareDto>>

    /** Grants or changes access. `permission` is "read" or "write". */
    @PUT("api/emails/folders/share")
    suspend fun shareFolder(@Body body: FolderShareRequest): Response<SimpleMessageDto>

    @DELETE("api/emails/folders/share")
    suspend fun unshareFolder(
        @Query("folder") folder: String,
        @Query("grantee") grantee: String,
    ): Response<SimpleMessageDto>
}
