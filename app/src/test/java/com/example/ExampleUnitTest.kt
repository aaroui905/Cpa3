package com.example

import com.example.service.IdentityService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testAsocksFetch() = runBlocking {
    val url = "https://asocks-list.org/Lb5lLZADymiWzwGhSVeZyGrcdJcc9m3g.txt?limit=10&type=res&template_id=2&country=US"
    val res = IdentityService.fetchProxiesFromUrl(url)
    println("Fetch result: isSuccess=${res.isSuccess}")
    assertTrue(res.isSuccess)
    val proxies = res.getOrThrow()
    println("Parsed proxies count: ${proxies.size}")
    assertEquals(10, proxies.size)
    
    for (i in 0 until minOf(3, proxies.size)) {
      val p = proxies[i]
      println("Testing proxy #$i: ${p.host}:${p.port} [${p.type}] user=${p.username.take(15)}...")
      val diag = IdentityService.testAndDetectProxy(p.host, p.port, p.type, p.username, p.password, 12000)
      println("Proxy #$i diagnostic: working=${diag.isWorking}, ip=${diag.exitIp}, ping=${diag.pingMs}ms, city=${diag.city}, country=${diag.country}, score=${diag.qualityScore}")
      assertTrue(diag.isWorking)
    }
  }
}
