package ru.ainetico.scanprice.model

import java.io.File
import java.security.MessageDigest

object FileHashVerifier {

  fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buf = ByteArray(65536)
      var len: Int
      while (input.read(buf).also { len = it } > 0) {
        digest.update(buf, 0, len)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  fun verify(file: File, expectedHash: String): Boolean =
    sha256(file).equals(expectedHash, ignoreCase = true)
}
