package org.tresamigos.smvtest.test6

import org.tresamigos.smv._

object M1 extends SmvModule("") with SmvOutput {
  override def requiresDS = Seq( input.table )

  override def run(i: runParams) = {
    i( input.table )
  }
}
