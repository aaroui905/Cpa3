package com.example

import com.example.service.IdentityService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testAsocksFetch() = runBlocking {
    val url = "https://asocks-list.org/2aO0MbMZ6MsQ6klCK248dXEekfbNKyzf.txt?limit=10&type=res&template_id=2&country=US"
    val res = IdentityService.fetchProxiesFromUrl(url)
    println("Fetch result: isSuccess=${res.isSuccess}")
    if (res.isSuccess) {
      val proxies = res.getOrThrow()
      println("Parsed proxies count: ${proxies.size}")
      val first = proxies.first()
      println("Testing first proxy: ${first.host}:${first.port} user=${first.username}")
      val testRes = IdentityService.testProxyConnection(first.host, first.port, first.type, first.username, first.password, 10000)
      println("testProxyConnection result: isWorking=${testRes.first}, ip=${testRes.second}, ping=${testRes.third}ms")
      val geo = IdentityService.fetchGeoInfo(first.host, first.port, first.type, first.username, first.password)
      println("fetchGeoInfo result: ip=${geo.ip}, country=${geo.country}, city=${geo.city}")
    } else {
      println("Fetch error: ${res.exceptionOrNull()?.message}")
    }
  }
}
