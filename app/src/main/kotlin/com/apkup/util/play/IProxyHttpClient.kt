package com.apkup.util.play

import com.aurora.gplayapi.network.IHttpClient


interface IProxyHttpClient : IHttpClient {
    @Throws(UnsupportedOperationException::class)
    fun setProxy(proxyInfo: ProxyInfo): IHttpClient
}
