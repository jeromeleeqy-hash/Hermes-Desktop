package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.BuildConfig
import java.io.IOException
import java.util.concurrent.CancellationException

enum class GatewayLoginStage(val request: String) {
    INPUT("Local input validation"),
    GATEWAY("GET /api/status"),
    PROVIDERS("GET /api/auth/providers"),
    LOCAL_SESSION("Local encrypted session storage"),
    PASSWORD("POST /auth/password-login"),
    VERIFY_SESSION("GET /api/auth/me"),
    RESTORE_SESSION("GET /api/auth/me (saved session)"),
    PROFILES("GET /api/profiles"),
}

enum class GatewayLoginReason { EMPTY_INPUT, CREDENTIALS, SESSION, RATE_LIMIT, CONFIGURATION, NETWORK, STORAGE, RESPONSE }

/** Contains only known categories, never a password, cookie or raw server response. */
class GatewayLoginException(
    val stage: GatewayLoginStage,
    val statusCode: Int? = null,
    val reason: GatewayLoginReason,
) : Exception(when (reason) {
    GatewayLoginReason.EMPTY_INPUT -> "请填写网关用户名和密码。"
    GatewayLoginReason.CREDENTIALS -> "网关未接受本次账号密码验证。请点击“显示”核对输入，或复制登录诊断。"
    GatewayLoginReason.SESSION -> if (stage == GatewayLoginStage.RESTORE_SESSION)
        "保存的登录状态已失效，请重新输入账号和密码。"
        else "账号密码提交已完成，但登录状态未通过验证。请复制登录诊断继续排查。"
    GatewayLoginReason.RATE_LIMIT -> "网关暂时限制了登录请求，请稍后再试。"
    GatewayLoginReason.CONFIGURATION -> "该网关未提供当前客户端支持的账号密码登录方式。请复制登录诊断。"
    GatewayLoginReason.NETWORK -> "登录时网络连接失败，请检查网关地址和网络后重试。"
    GatewayLoginReason.STORAGE -> "无法读取或保存本机登录状态，请检查钥匙串和应用数据目录。"
    GatewayLoginReason.RESPONSE -> "网关未能完成本次连接，请复制登录诊断查看失败步骤。"
}) {
    fun diagnostic(): String = """
        Hermes login diagnostic
        App: ${BuildConfig.VERSION_NAME}
        Stage: ${stage.name}
        Request: ${stage.request}
        HTTP status: ${statusCode ?: "not available"}
        Reason: ${reason.name}
    """.trimIndent()
}

internal fun validateGatewayCredentials(username: String, password: String) {
    if (username.isBlank() || password.isBlank())
        throw GatewayLoginException(GatewayLoginStage.INPUT, reason = GatewayLoginReason.EMPTY_INPUT)
}

internal inline fun <T> gatewayLoginStep(stage: GatewayLoginStage, action: () -> T): T = try {
    action()
} catch (e: GatewayLoginException) {
    throw e
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    val code = (e as? ApiException)?.statusCode
    val reason = when {
        stage == GatewayLoginStage.LOCAL_SESSION -> GatewayLoginReason.STORAGE
        code == 429 -> GatewayLoginReason.RATE_LIMIT
        stage == GatewayLoginStage.PASSWORD && code == 401 -> GatewayLoginReason.CREDENTIALS
        stage in listOf(GatewayLoginStage.VERIFY_SESSION, GatewayLoginStage.RESTORE_SESSION) && code in listOf(401, 403) -> GatewayLoginReason.SESSION
        stage in listOf(GatewayLoginStage.GATEWAY, GatewayLoginStage.PROVIDERS) && code == 400 -> GatewayLoginReason.CONFIGURATION
        e is IOException -> GatewayLoginReason.NETWORK
        else -> GatewayLoginReason.RESPONSE
    }
    throw GatewayLoginException(stage, code, reason)
}
