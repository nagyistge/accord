/*
  Copyright 2013-2014 Wix.com

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package com.wix.accord.tests.transform

import org.scalatest.{WordSpec, Matchers}
import com.wix.accord.transform.ExpressionDescriber

class ExpressionDescriberTests extends WordSpec with Matchers {

  case class Test( field1: String, field2: String )

  import com.wix.accord.TestDomain.dsl.Descriptor

  "ExpressionDescriber.describe over a single-parameter function literal" should {
    "render a correct description for expressions over the function parameter members" in {
      val description = ExpressionDescriber describe { ( t: Test ) => t.field1 }
      description shouldEqual "field1"
    }
    "propagate an explicitly-described expression" in {
      val description = ExpressionDescriber describe { ( t: Test ) => t.field2 as "explicit" }
      description shouldEqual "explicit"
    }
    "fall back to the expression tree itself for unsupported expressions" in {
      val description = ExpressionDescriber describe { ( _: Test ) => "arbitrary" }
      description shouldEqual "\"arbitrary\""
    }
  }

  "ExpressionDescriber.describe" should {
    "generate a compile-time error for function parameters" ignore {
      // Ignored pending fix of https://issues.scala-lang.org/browse/SI-6636
      import scala.tools.reflect.{ToolBox, ToolBoxError}

      val toolbox = scala.reflect.runtime.currentMirror.mkToolBox()

      val thrown = the [ ToolBoxError ] thrownBy {
        toolbox.compile( toolbox.parse(
          """
            |val f: Int => String = { _ => "" }
            |com.wix.accord.transform.ExpressionDescriber describe f
          """.stripMargin ) )
      }

      thrown.message should contain( "Only function literals are supported" )
    }
  }
}
