package com.spotkofi.app.data.remote

/** A catalog request or response failure exposed to repository callers. */
class CatalogException(message: String, cause: Throwable? = null) : Exception(message, cause)
