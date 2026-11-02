package com.servicehub

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform