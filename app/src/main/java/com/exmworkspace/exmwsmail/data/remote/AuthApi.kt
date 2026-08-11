package com.exmworkspace.exmwsmail.data.remote

import com.exmworkspace.exmwsmail.data.remote.dto.CaptchaChallengeDto
import com.exmworkspace.exmwsmail.data.remote.dto.CaptchaTokenDto
import com.exmworkspace.exmwsmail.data.remote.dto.CaptchaVerifyRequest
import com.exmworkspace.exmwsmail.data.remote.dto.LoginRequest
import com.exmworkspace.exmwsmail.data.remote.dto.LogoutRequest
import com.exmworkspace.exmwsmail.data.remote.dto.RefreshRequest
import com.exmworkspace.exmwsmail.data.remote.dto.TokenPairDto
import com.exmworkspace.exmwsmail.data.remote.dto.UserDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenPairDto>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<TokenPairDto>

    @POST("api/auth/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<ResponseBody>

    @GET("api/auth/me")
    suspend fun me(): Response<UserDto>

    @POST("api/auth/captcha/challenge")
    suspend fun captchaChallenge(): Response<CaptchaChallengeDto>

    @POST("api/auth/captcha/verify")
    suspend fun captchaVerify(@Body body: CaptchaVerifyRequest): Response<CaptchaTokenDto>
}
