package com.example

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun createMasterRegistryBlob() {
    try {
      val url = URL("https://jsonblob.com/api/jsonBlob")
      val conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "POST"
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/json")
      conn.setRequestProperty("Accept", "application/json")
      conn.outputStream.use { out ->
         out.write("{}".toByteArray())
      }
      val code = conn.responseCode
      val location = conn.getHeaderField("Location")
      val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
      
      System.err.println("--- REGISTRY CREATION CODE: $code ---")
      System.err.println("--- REGISTRY LOCATION: $location ---")
      System.err.println("--- REGISTRY RESPONSE: $body ---")
      
      val registryBlobId = location?.substringAfterLast("/") ?: ""
      System.err.println("--- MASTER REGISTRY BLOB ID: $registryBlobId ---")
      assertFalse(registryBlobId.isEmpty())
    } catch (e: Exception) {
      e.printStackTrace()
      throw e
    }
  }
}











