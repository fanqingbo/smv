/*
 * This file is licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tresamigos.smv

import java.io.{InputStream, StringWriter, PrintWriter}
import java.util.zip.CRC32

import scala.util.control.NonFatal
import scala.tools.asm

/**
 * computes the CRC32 checksum for the code of the given class name.
 * The class must be reachable through the configured java class path.
 */
private[smv] case class ClassCRC(
  className: String,
  classLoader : ClassLoader = getClass.getClassLoader) {

  import ClassCRC._
  lazy val crc: Long = checksum(className, classLoader).getValue
}

object ClassCRC {
  private[smv] def cksum0(reader: asm.ClassReader): CRC32 = {
    val code = AsmUtil.asmTrace(reader).toCharArray().map{c => c.toByte}
    val ret = new CRC32
    ret.update(code)
    ret
  }

  def checksum(bytecode: Array[Byte]): CRC32 = cksum0(new asm.ClassReader(bytecode))

  def checksum(className: String, classLoader: ClassLoader): CRC32 = {
    val classResourcePath = className.replace('.', '/') + ".class"
    val is: InputStream = classLoader.getResourceAsStream(classResourcePath)

    try {
      cksum0(new asm.ClassReader(is))
    } catch {
      case NonFatal(t) => throw new IllegalArgumentException("invalid class name for crc: " + className, t)
    } finally {
      if (is != null) is.close
    }
  }
}
