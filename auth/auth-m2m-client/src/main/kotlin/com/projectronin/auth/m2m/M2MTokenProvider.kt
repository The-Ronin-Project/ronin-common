package com.projectronin.auth.m2m

import com.projectronin.auth.token.RoninLoginProfile

interface M2MTokenProvider {

    fun getToken(
        audience: String,
        scopes: List<String>? = null,
        requestedProfile: RoninLoginProfile? = null
    ): String

    fun addTokenListener(
        audience: String,
        scopes: List<String>? = null,
        requestedProfile: RoninLoginProfile? = null,
        listener: TokenListener
    )

    fun removeTokenListener(
        audience: String,
        scopes: List<String>? = null,
        requestedProfile: RoninLoginProfile? = null,
        listener: TokenListener
    )
}
