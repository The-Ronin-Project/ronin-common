package com.projectronin.common.auth.token

/**
 * Meant to be extracted from a JWT token, a collection of claims related to the Project Ronin auth system.
 */
class RoninClaims(
    val user: RoninUser?
)
